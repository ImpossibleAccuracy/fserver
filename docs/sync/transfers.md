# File transfers

> How file bytes move between devices: the upload protocol, resume, integrity checking, staging and
> placement on the receiver, and downloads (which are uploads in reverse). Shared by sync and one-shot
> transfers.

## 1. Principles

- **One direction of bytes: push.** A download is the peer pushing the file back on request; there
  is one receive path to get right.
- **The destination is touched once**, when the file is whole and its hash matches. Until then bytes
  live in staging.
- **Resume, never restart**: the receiver checkpoints what is durably on disk, and that survives the
  session and the process.
- **The receiver decides** whether it takes a file (authorization, mode, its own limits). The sender's
  checks are UX only.
- **Integrity by content hash** (SHA-256 of the plaintext) over the whole file (ToR §3.3).

## 2. Messages

An upload is keyed by `UploadKey`:

| Key                          | Meaning                                              |
|------------------------------|------------------------------------------------------|
| `Source(sourceId, fileId)`   | a file of a source                                   |
| `OneShot(transferId, index)` | file #index of a one-shot transfer ([one-shot](../services/one-shot-transfers.md)) |

| Message (sender → receiver)          | Answer                                                         |
|--------------------------------------|----------------------------------------------------------------|
| `Upload.Init(key, file?)`            | `Received(offset)` - send from here; `Completed` - already there (one-shot only); `OverLimit`; `Stopped`; `Failed` |
| `UploadChunk(key, offset, bytes)`    | none (fire-and-forget)                                         |
| `Upload.Status(key)`                 | `Received(offset)` - checkpoint; `Failed` - upload lost, `Init` again; `Stopped` |
| `Upload.Complete(key, hash, algorithm)` | `Completed`; `Received(offset)` - bytes missing, resend; `Failed` |
| `Upload.Abandon(key, reason)`        | `Completed` - the sender can no longer read the file           |

`Init.file` (for sources) is the full file record: id, path, state, hash?, size, mtime, **version** -
what the receiver will record. A one-shot file was described by its offer and carries none.

All messages are JSON in the application dictionary except `UploadChunk`, which has a binary encoding
(JSON would write bytes as an array of numbers - ~3.6 bytes per byte):

```
"uploadChunk" (11 raw bytes, magic - no JSON document starts like this)
u8 tag (0 = Source, 1 = OneShot)
Source:  string sourceId, string fileId          OneShot: string transferId, i32 index
i64 offset
raw bytes (rest of the message, no own length)
```

## 3. Sender

```
chunkSize = session.maxPayloadSize - chunkHeaderSize(key)   (min 4 KiB) → one chunk = one frame, no split
offset = Init()                                    ; Completed → done, nothing sent
repeat up to 3 attempts:
    stream the file from `offset`: UploadChunk(offset, bytes) ...
        every 64 MiB: Status() → false (receiver lost it) → break
    Complete(hash) → Completed → done
    offset = Init()                                ; resume from the receiver's checkpoint
fail "peer did not take the file in 3 attempts"
```

- The hash is computed **while streaming** when not already known; bytes the receiver already has are
  re-read only as far as the hash has not seen them.
- `Stopped` (the one-shot was cancelled there) ends the upload as stopped; `OverLimit` as skipped;
  `Failed` on `Init` as refused.
- For a source upload the sender then records the hash in its index if it was unknown and upserts the
  peer's row in the remote index cache.
- The file is opened through the source's file system, i.e. as plaintext ([at-rest
  encryption](../files/at-rest-encryption.md)); a missing file fails the action (the next pass
  re-plans).

## 4. Receiver

### 4.1. Admission (`Init`)

For a **source** file:

1. authorize the source for this peer ([sources §6](sources.md#6-authorization-answering-side));
2. **mode**: the source must accept peer writes - *unless this device asked for the file*
   (a pending download for `(peer, file)`; that is how a follower-side `Download` answer is let in);
3. **file limits** (this device's own): a file already held is charged only its growth; a new one by
   count and size; other uploads open for the same source in this session count as booked. Over →
   `OverLimit` (the sender skips, does not retry);
4. `checkPath(path)` - refused before anything is staged, not after all of it arrived;
5. open or resume staging (§5).

For a **one-shot** file: the transfer must be this peer's, incoming, and `Active`; otherwise
`Stopped`. A file already completed answers `Completed`.

Per session at most **8** uploads open at once; a second `Init` for the same key parks the first
attempt. An upload with no chunk or `Status` for 30 min is parked on the next `Init` (nothing else
parks one whose sender went silent mid-stream); one still streaming is never parked.

### 4.2. Writing

The session collector **never writes to disk**: a chunk is offered to the upload's own writer queue
and the collector moves on (the network layer drops frames while its consumer blocks). Chunk bytes buffered in
memory per session are capped at **50 MiB**; a chunk over the cap is refused - the sender resends
after the next `Status`/`Complete` reports a smaller offset.

The writer:

- writes at the chunk's offset (positional - chunks may arrive in any order);
- refuses a chunk outside `[0, declared size)` (a peer must not choose how big a sparse file we make);
- tracks received ranges; a range already covered is a resend and is skipped;
- hashes the contiguous prefix as it grows (from the chunk, or from disk for a run that arrived early).

A writer failure is kept on the upload, not thrown - one failed upload must not tear down the session
serving every other request from that peer.

### 4.3. `Status` and `Complete`

- `Status`: fail if the writer died; else **fsync** and checkpoint the contiguous prefix → answer it.
- `Complete`: wait for the writer to drain. Writer failed, or the prefix does not cover the declared
  size → park and answer `Received(prefix)` (the sender `Init`s and resends). Otherwise:
  1. fsync, checkpoint the full size, stop the writer;
  2. compare the computed hash (and algorithm) with the sender's. Mismatch → delete the staged bytes
     and the row - nothing corrupted is worth resuming - and fail;
  3. **check the target**, under the source's index lock: the file at the target path must still be
     what the index row says (absent if the row is not `Present`; same size and mtime otherwise). A
     local edit made while the upload was running is never overwritten: the placement is refused
     (`Failed("changed locally")`), staging is kept, and the next scan versions the edit, so the next
     plan sees both versions (a conflict, or a newer local version);
  4. **place** ([file systems §2](../files/file-systems.md#2-the-file-system-api)): staging → the
     file's path in the source, replacing it atomically (sealed staging moves as is);
  5. drop the staging row;
  6. `settleLastModified(sender's mtime)` and record **what the disk reports**;
  7. index the file under the **sender's version** with the verified hash, keeping this device's
     pin/fetch state ([indexing §6](indexing.md#6-index-writes-outside-a-scan)).

  A failed placement keeps staging and its row: the next attempt only places again, without a byte
  resent.

## 5. Staging and resume

Source uploads are staged at `<staging>/<sourceId>/<fileId>/data` with a persisted row:

```
StagedUpload { sourceId, fileId, deviceId (sender), locator, size, modifiedAt,
               versionHlc?, versionOrigin?, committedOffset, startedAt, touchedAt }
```

`[0, committedOffset)` is on disk and flushed. On `Init` the parked bytes are reused only if the
**same peer** sends the **same size and version** (`hlc`, `origin`); anything else starts over. Both
ids are peer-chosen and become directory names, so they must be single path segments.

For a source with encryption `Required`, staging is sealed from the first byte
([at-rest encryption §6.4](../files/at-rest-encryption.md#64-plaintext-leftovers)); a resumed staging
file is opened by its own header, since the policy may have changed meanwhile.

When a session ends, all its open uploads are **parked**: writer stopped, flushed, checkpointed.

Garbage collection ([housekeeping](../services/housekeeping.md)) drops rows untouched for 1 day,
rows whose bytes the OS cleared from the cache, and staged files with no row older than 1 hour.

Staged bytes are capped per peer and in total (configurable). An `Init` that would exceed a cap
first discards the oldest parked uploads; if it still does not fit it is answered `Failed`, and the
sender retries on its next pass.

## 6. Downloads

`Download(file, version)` on the driving side:

1. register a pending download for `(peer, sourceId, fileId)`;
2. send `OperationWithConfirmation(File.Download(key, version))`;
3. the peer opens the file from its own index and **uploads it to us** over the same session, under
   `version` (or its own when none was given);
4. our receiver admits it even where the source takes no peer writes (step 2 of §4.1);
5. the peer answers `Completed` only after our `Complete` was answered - i.e. once the file is placed
   and indexed here.

The request timeout is budgeted from size, since only the whole transfer completes it:
`2 min + size / 64 KiB/s`, capped at 2 h.

User-initiated fetches (opening a file this device does not hold) use the same path and are journaled
as `FileFetched` ([eviction §4](eviction.md#4-fetch-on-demand)).
