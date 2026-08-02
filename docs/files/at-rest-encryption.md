# At-rest encryption

### Encrypting files on disk, per-source policy, pluggable ciphers

> Version 3 (draft).
>
> Builds on ToR §3.4 (sync, offload), §3.6 (security and encryption), §3.9 (export).

---

## 1. Goal

ToR §3.6: "files are encrypted in storage (at rest), the transport is always encrypted". This
document settles:

- where a file is decrypted - on transfer or only on access;
- what to do with locations that cannot be encrypted;
- how encrypted and not-yet-encrypted files live side by side;
- how a host plugs in its own cipher.

## 2. Threat model

### 2.1. Defended against

- **Theft or seizure of the storage medium** - a disk, an SD card, an extracted copy of app data.

### 2.2. Deliberately not defended against

- **An untrusted server.** Keys are available to the server (ToR §3.6: AI decrypts files). The UI
  must say so honestly.
- **A compromised running device** (root, malware in the process): the key is in memory, files are
  read the way the app reads them.
- **End-to-end encryption** where the server cannot read files - out of scope, postponed far ahead.
  It would be a separate layer above the engine with its own user key.

## 3. Key decision: encryption is local to the device

**A file is decrypted when read from storage. Plaintext travels over the network inside the
encrypted session ([authentication §6](../network/authentication.md#6-session-cryptography)).** Each
device has its own key and possibly its own cipher; other devices know nothing about them.

Why ciphertext is not transferred:

| Reason                | What would break                                                                                                  |
|-----------------------|-------------------------------------------------------------------------------------------------------------------|
| Content hash          | version vectors, "same content is not a conflict", evict confirmation by hash compare hashes; random nonces make the same content a different ciphertext on every device |
| Different keys/ciphers | the receiver would need the sender's key and cipher; one host's custom cipher would leak into the protocol       |
| Unencryptable locations | a receiver writing into a shared media library decrypts anyway                                                 |
| AI on the server      | decrypts anyway                                                                                                   |

Consequences:

- **The content hash is always of the plaintext.** Index, protocol and engine only ever see it.
- **Encryption does not change the version.** Encrypting, re-encrypting, changing the cipher,
  rotating a key never touch the version vector: the content did not change (ToR §3.4).
- **Export writes plaintext** ([data export](../services/data-export.md)).

## 4. Place in the architecture

Encryption is a decorator over a location's file system: `EncryptedFileSystem(inner, source, ciphers,
index)`. Reads return plaintext, writes take plaintext and store ciphertext, sizes are plaintext
sizes. The sync engine does not know about encryption.

Every location that **can** hold ciphertext is always opened through the decorator - even under
policy `Off` - so files sealed under an earlier policy stay readable. How each file sits on disk is
recorded by the index (`atRest`), not by the engine (§6).

## 5. Policy per location

Not every location can be encrypted: files in shared folders are read by other apps. Each location
declares whether it supports encryption, and the policy is set per source.

| Location                | Encryption                               | Why                                                                                   |
|-------------------------|------------------------------------------|---------------------------------------------------------------------------------------|
| App-private (`Internal`) | allowed                                 | private; the OS may already encrypt it, so the gain is protection from root and backup extraction |
| `Directory`, `Tree`     | the user's call: a folder used only by FServer | ciphertext in a user folder breaks every other app                              |
| `Media`, `Downloads`    | never                                    | gallery and file managers must see the files                                          |
| `Shared` (share sheet)  | impossible                               | read-only                                                                             |

Policy: `Off` / `Required(cipherId)` (default cipher `fserver.aes256gcm-seg.v1`). Part of this
device's source preferences; **never sent to the peer**. Setting `Required` on an unsupported
location or with an unregistered cipher is refused.

UI status per source: `Unsupported`, `Off`, `Encrypted(cipher)`, `Migrating(remaining, toward)`.

## 6. Mixed state

One folder may hold sealed and plain files at once: the user dropped a file in, the policy was just
enabled, the cipher changed.

### 6.1. Source of truth

- **The index** records how a file sits on disk: `Plain` or `Sealed(cipherId, keyId)`. "Pending"
  is not stored but derived: the state disagrees with the policy.
- **The file header** describes the file on its own (§7.1) and serves as a cross-check.

The header alone cannot be trusted:

- a plaintext file that happens to start with the magic would be taken for sealed - a header that
  fails to parse is treated as plaintext **unless the index says sealed**;
- **downgrade to plaintext**: an attacker puts a plain file where a sealed one was. Under
  `Required`, if the index remembers the file as sealed, plaintext is refused: reading fails, and a
  scan keeps the index row as it was (otherwise the substitution would go to the peer as an edit).

### 6.2. Background migration

One procedure for `plain → sealed`, `sealed → plain`, cipher change and key rotation:

1. write the target form into a part file beside the original
   (`<name>.<random id>.fserver-part`, ignored by scans);
2. fsync;
3. under the source's index lock, **only if** neither the index row nor the raw file length changed
   since the rewrite began: rename the part over the original, re-settle the original mtime, update
   `atRest` in the index;
4. otherwise delete the part.

A crash at any step leaves either the old or the new file whole. Neither the version nor
`processedAt` changes - nothing to send to the peer.

Target: `Off` → plain; `Required(c)` → cipher `c` and the source's **current** key. Migration runs at
startup, after sync passes (both local and the peer's), and when a source's policy changes; one
source and one file at a time. Before migrating, the source is rescanned: a rewrite keeps the
index's mtime, so an unscanned edit would hide behind it. A file that will not open (lost key,
damage) is counted as failed and raises a journal issue, without holding up the others.

### 6.3. New files

- Files arriving over the network and files created through the core are written through the
  decorator and sealed immediately.
- Files appearing in the folder behind the core's back stay plain until migration picks them up.

### 6.4. Plaintext leftovers

Encrypting storage is pointless if a plain copy sits next to it. Temporary copies fall under the same
policy:

- an inbound transfer for a `Required` source is sealed in staging **from the first chunk** and moved
  into the source as is;
- the app reads source files only through the core - thumbnails, viewer, player, handing to another
  app. A sealed file never yields a raw file handle; another app gets plaintext through a pipe or a
  content provider of the app.

Deliberate exception - **previews**: thumbnails, frames and covers live in the app's disk cache in
plain form, including previews of evicted files. A downscaled picture is not the file.

Not covered: copies of files received through "Share" (outgoing one-shot transfers) - they have no
source, hence no policy.

## 7. File format

### 7.1. Header

```
magic "FSEC" | version u8 (=1) | segmentSize i32 | nonceSize u8 | tagSize u8
           | cipherId (u8 len + UTF-8) | keyId (u8 len + UTF-8) | salt (16 random bytes)
```

The whole header is part of every segment's AAD: a changed header fails the first segment opened,
and the per-file salt stops a segment from being moved from one file to another. Segment size
1 KiB..4 MiB, default 64 KiB.

### 7.2. Segments

The plaintext is cut into segments, each sealed separately by the cipher's AEAD:

- on disk: `nonce | ciphertext+tag`; the nonce is **fresh random on every seal** and stored. Deriving
  it from the segment number is forbidden: an in-place write reseals a segment under the same key,
  and a repeated GCM nonce reveals plaintext and enables forgery;
- AAD = header ‖ segment index (i64) ‖ "last" flag (u8): reordering segments or truncating at a
  segment boundary fails authentication;
- every segment holds `segmentSize` plaintext bytes except the last; an empty file is one empty last
  segment;
- a write at an offset reseals the touched segments (and the previous last one when the file grows);
  `truncate` reseals the new last one.

Replacing a segment with an older version of itself (rollback) is not detected - outside the threat
model (§2).

A single stream does not work: no random access, and common AEAD implementations buffer the whole
ciphertext before releasing plaintext - a large file runs out of memory.

### 7.3. Size

File systems and scans report the **plaintext** size, computed from the file length: header and
per-segment overhead (nonce + tag) are constant and recorded in the header. A scan reads the header
only where the index cannot vouch for the file: when the mtime matches the row and the length equals
the expected ciphertext length for the recorded plaintext size, the size is taken from the index.

### 7.4. Open decision: marking sealed files by name

Proposal: a sealed file sits on disk under a suffix (`photo.jpg` → `photo.jpg.fsenc`) that the
decorator strips, so path, file id and what the peer sees stay the same. Together with a fixed-size
header (key id as 16 raw bytes, cipher id as a 16-byte hash looked up in the registry), a scan derives
the plaintext size from the length alone and never opens a file - instead of reading the header of
every file the index cannot vouch for (on a first scan of a large folder, a read per file). It also
survives what no per-source record can: a source removed and re-added on a folder that still holds
sealed files, or a storage card moved between devices.

Costs: the decorator maps paths in every operation (`createFile`, `place`, `fileExists`, `checkPath`,
`rename`); migration renames files; a user who strips the suffix by hand turns a file into
"plaintext" (caught only by the downgrade check, where the index remembers it sealed).

## 8. Keys

Envelope scheme:

- **one data key per source** (`StorageKey { id, secret }`), created on first use;
- stored **only wrapped** by a wrapping key from the platform keystore (AES-256-GCM, no
  user-auth requirement so background sync works); the key id is bound into the wrap as AAD so
  two rows cannot swap keys;
- the header names the key id; reading looks the key up by id, so files sealed under an older key of
  the source stay readable after rotation;
- rotating the wrapping key = re-wrapping data keys, not re-encrypting files;
- deleting a source's keys makes its files unreadable (crypto-erase). On source removal the keys are
  erased **only** for an app-private location, where the files go with the source; for a user folder
  the files outlive the source, and a source re-added on the same folder must still open them;
- a key the keystore no longer unwraps (app data restored on another device) is not returned: old
  files fail with "missing key", new ones are written under a new key;
- **export / import**: the user can export all data keys (`keyId -> key`) into one versioned file,
  re-wrapped under a key derived from a passphrase (a memory-hard KDF), and import it on another
  install, where the keys are re-wrapped under the local keystore and found by `resolve` for any
  source. This is what keeps sealed files readable after a reinstall or a folder/SD card moved to
  another device. The UI warns that the file plus the passphrase opens everything.

```
StorageKeysStore
    current(sourceId) -> StorageKey(id, key)    // creates on first use
    resolve(keyId)    -> key | none
    forget(sourceId)
    export(passphrase) -> blob
    import(blob, passphrase)
```

## 9. Extensibility

```
StorageCipher
    id                    // "fserver.aes256gcm-seg.v1"; written into headers, never reused for another algorithm
    nonceSize, tagSize
    seal(key, nonce, aad, plain) -> sealed       // output = plain.size + tagSize
    open(key, nonce, aad, sealed) -> plain       // throws on any mismatch
```

- **A cipher is only an AEAD over one segment.** Nonce generation, AAD, format, header, sizes,
  migration and random access are the core's job.
- **Built-in + host ciphers.** The host passes its ciphers at core configuration
  (engine config); ids must be unique. The cipher is chosen by the header's id, so
  old files stay readable after a cipher change.
- **An unknown cipher id is an explicit error,** never silent raw bytes.
- Built-in: AES-256-GCM per segment, 12-byte nonce, 16-byte tag, id `fserver.aes256gcm-seg.v1`.
- Fallback when a segment-level cipher does not fit (hardware encryption, external key store): a whole
  file system decorator of one's own.

## 10. Invariants

1. The content hash is always of the plaintext.
2. Encryption, re-encryption and key rotation never create a file version.
3. Neither storage ciphertext, nor cipher ids, nor storage keys ever go over the network.
4. Shared locations (media, Downloads, share) are never encrypted.
5. The index decides a file's encryption state; under `Required`, plaintext where the index remembers
   a sealed file is refused.
6. A migration replacement is atomic.
7. An unknown cipher is an error, not plaintext.
