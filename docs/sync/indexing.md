# Indexing: detecting local changes

> How a source's local index is brought in line with the disk, when a change becomes a new version,
> how hashes are computed lazily, and every other way index rows change.
>
> Version format and issuing rules: [versioning](versioning.md).

## 1. Principles

- **The core detects changes by comparing the disk with the index**, not from app events - files are
  edited by other programs.
- The whole source is scanned and compared with the index **by path** on every pass (fine at the
  ~1000 file scale, ToR §4.1).
- **mtime is only a hint** for "maybe changed"; a new version requires certain evidence: a new file, a
  different size, a disappeared file, or a hash that differs from the last one.
- **A deleted row stays** as a tombstone - otherwise the file would come back from an older peer.
- All writes to a source's rows are serialized by **one lock per source** (scan, hash record, and
  every write in §6). Two writers interleaving double-bump vectors.

## 2. The index row

```
LocalIndexedFile {
    id            // row key, local
    sourceId, fileId (= SHA-256 of canonical path), path, locator
    state         // Present(pinned, fetchedAt?) | Evicted(at) | Deleted(at)
    size, modifiedAt                 // as the disk last reported them
    hash?                            // content hash of the plaintext, or null = not hashed yet
    hashStale                        // bytes touched since `hash` was taken (kept to tell edit from touch)
    version?                         // see versioning
    lastAccessed?                    // last read of the bytes through the core (LRU); null = unknown
    processedAt
    atRest                           // Plain | Sealed(cipher, key) - local only
}
```

What leaves the device (index listing / publish) is the row without locator and `atRest`, and with
`hash = null` when `hashStale` - a stale hash may hide an unversioned edit, so the peer must ask for a
rehash instead of trusting it.

**Content hash**: SHA-256 over the plaintext, lowercase hex, always carried with its algorithm name,
so records hashed by different algorithms never compare equal.

The **remote index** is the same shape (no locator) per source: the peer's last reported listing,
written through on every fetch and on every `PublishIndex` push, plus an upsert after each successful
upload. It is a **cache**: a pass never plans from it (it fetches fresh), but held conflicts, the file
browser and eviction checks read it.

## 3. What a scan skips

Skipped files are neither indexed nor turned into tombstones - an ignored file indexed before a rule
existed would otherwise become a deletion on the peer. A skipped row keeps what it had.

**Ignored paths** (case-insensitive):

| Kind | Rule |
|------|------|
| directories anywhere in the path | `.thumbnails .trash .trashes .spotlight-v100 .fseventsd .temporaryitems .appledouble $recycle.bin "system volume information" lost+found .stfolder .stversions`, `.trash-*` |
| names | `.nomedia .ds_store thumbs.db ehthumbs.db desktop.ini .directory` |
| prefixes | `.trashed- .pending- .trash- ~$ .~lock. .# ._` |
| suffixes | `.tmp .temp .part .partial .crdownload .download .swp .swo .lck ~ .fserver-replaced` |
| contains | `.fserver-part` (our own in-flight copies) |

**Files open in an editor** - judged by a lock file next to them younger than 2 hours (older = a
crashed editor or an edit long enough to sync midway). Every save would otherwise be a version, and a
save's rename dance would look like a deletion:

| Editor     | Lock file          | Locks                                          |
|------------|--------------------|------------------------------------------------|
| MS Office  | `~$name.ext`       | `name.ext` (long names lose up to 2 leading chars) |
| LibreOffice | `.~lock.name.ext#` | `name.ext`                                     |
| emacs      | `.#name`           | `name`                                         |
| vim        | `.name.swp/.swo`   | `name`                                         |

The list is part of the indexing rules on every platform; a platform adds the conventions of its
own editors to it.

## 4. Scan → index

For every scanned (not skipped) file, matched to the row with the same path:

| Disk vs index                                       | Result                                                      |
|-----------------------------------------------------|-------------------------------------------------------------|
| no row                                              | new row, **new version**, hash unknown                      |
| row `Deleted`/`Evicted`, file is back               | `Present` again, **new version**, hash unknown              |
| different size                                      | **new version**, hash unknown                               |
| same size, different mtime, row has a hash          | version unchanged, `hashStale = true` ("touched")           |
| same size, different mtime, row has no hash         | **new version**, hash unknown                               |
| same size, same mtime                               | nothing                                                     |
| row `Present`, file gone from disk                  | `Deleted`, **new version** (tombstone; `hashStale` kept so renames still match it) |

Rows that are `Evicted` and absent from disk stay `Evicted` - the absence is expected.

All new versions of one scan get consecutive HLC readings from one clock call. The whole batch is
written in one `markProcessed`. New-file `atRest` comes from what the encrypting file system saw during
the scan.

## 5. Lazy hashing

Hashing reads the whole file, so it happens only when a decision needs it:

- the plan asks for it (`ComputeHash`, see [planning](planning.md#4-verbs)) - local files hashed
  here, remote ones by asking the peer (`RemoteOperation.File.Hash`);
- an upload computes it on the way (the sender hashes while streaming);
- rename candidates (below).

Hashing runs **outside** the index lock (a long read must not block scans). Recording it runs under
the lock and applies these rules:

1. the row must still be `Present` with the **same size and mtime** as when hashing started -
   otherwise the hash may describe bytes that are gone, and it is dropped;
2. if the row had a previous hash and the new one **differs**, the bytes hold an edit nobody versioned
   yet → **new version** now;
3. the hash is stored, `hashStale = false`.

This is how "same size, new mtime" resolves: a touch keeps the version, an edit gets one.

**Rename candidates**: after each scan, unhashed `Present` rows whose `(size, mtime)` equal those of a
tombstone with a trusted hash are hashed immediately, so the planner can pair "new file + deleted
file with the same content" into a move ([planning §5](planning.md#5-move-pairing)). A file never
sent needs no rename; a sent one was hashed on the way. Serialized per source with its own lock.

## 6. Index writes outside a scan

Every write below runs under the source's index lock and records bytes that are **already** where the
write says.

| Write             | Caused by                                   | Version                                                    |
|-------------------|---------------------------------------------|------------------------------------------------------------|
| `recordHash`      | lazy hashing                                | new own version only if the hash changed (§5)              |
| `recordReceived`  | a peer's upload placed here                 | **the sender's version**; pin/fetch state kept; mtime = what the disk reports after settling |
| `recordDeleted`   | peer-requested or planned local deletion    | the peer's version, or a new own version when none given   |
| `recordEvicted`   | eviction                                    | none - state only                                          |
| `recordPinned`    | user pin/unpin                              | none; unpinning a fetched copy restarts its TTL            |
| `recordAccessed`  | the user opened/read the file through the core (viewer, player, handing to another app; not export, not sync) | none; sets `lastAccessed = now` |
| `recordMoved`     | planned move (ours or the peer's)           | target row gets the planned version, source row becomes a tombstone under the planned deleted-version (or a new own one) |
| `recordRenamed`   | user rename through the core                | two new own versions: target (after any tombstone at that path) and the old row's tombstone |
| `recordCreated`   | user creates an empty file through the core | new own version, after any tombstone at that path          |
| `recordWritten`   | user writes through the core                | new own version; hash cleared (taken later)                |
| `recordRewritten` | encryption migration                        | none; only locator, mtime (re-settled to the old value), `atRest` |
| `adoptVersion`    | merge / conflict resolution                 | the given version, **only if** content still matches: expected hash (non-stale) or still deleted - a fresh local edit is never relabelled as something the peer has |

A version received from a peer is recorded as is: no local bump (ToR §3.4).

## 7. Entry points

- every local pass (scan once per pass; later plan rounds re-read the index only);
- every `FetchFiles` request from the peer (the answer is a fresh index);
- before encryption migration;
- the host's explicit "index now";
- the initial scan of a newly added source.

Concurrent refreshes of the same source serialize on the lock rather than run twice in parallel.
