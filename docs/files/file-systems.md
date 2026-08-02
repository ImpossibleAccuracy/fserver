# File systems and paths

> One API over every kind of place files can live, and the canonical path that makes a file the same
> file on two devices.

## 1. Locations

A **location** says where a source's files are on *this* device. It is local: the peer never sees
it, only canonical paths.

Kinds, by what they can do:

| Location             | What it is                                                         | Can be a source | Can host peer files | Encryptable |
|----------------------|--------------------------------------------------------------------|:---------------:|:-------------------:|:-----------:|
| `Root(volumes)`      | whole storage volumes                                              | scan only (to pick a directory) | -     | -           |
| `Directory(path)`    | one directory addressed by path                                    | ✓               | ✓                   | ✓ (user's call) |
| `Tree(handle)`       | one directory reached through an OS-granted handle (no plain path) | ✓               | ✓                   | ✓ (user's call) |
| `Media`              | the OS media library: images, video, audio - a collection, not a directory | ✓      | -                   | never       |
| `Downloads(dir)`     | one directory under the user's public downloads folder             | -               | ✓                   | never       |
| `Internal(bucket)`   | app-private storage, one bucket per source                         | (testing only)  | ✓ (default)         | ✓           |
| `Shared(items)`      | items another app handed over (share sheet); read-only, valid while the grant lasts | - | -              | -           |

"Hostable" locations are where a follower stores a peer's files; Mirror and Host write the peer's
files into the initiator's location too, so they need a hostable one
([sources §3](../sync/sources.md#3-modes)). Locations other apps read (media, downloads) never hold
ciphertext ([at-rest encryption §5](at-rest-encryption.md#5-policy-per-location)).

## 2. The file-system API

```
ReadableFileSystem
    scan()      -> progress task: files found (path, locator, size, lastModified)
    scanTree()  -> scan() + every directory walked (empty ones included)
    openFile(locator) -> FsFile?

FileSystem : ReadableFileSystem
    fileExists(path), checkPath(path)
    createFile(path)          -> FsFile        // refuses an existing file
    place(file, path)         -> FsFile        // file from ANY file system; replaces the target; source gone after

FsFile
    locator                    // backend's own address of the file
    read() / openReader() (positional) / openWriter() (positional, truncate, sync)
    rename(newName, deleteOldOnConflict)
    delete() -> bool           // true also when already gone; false = backend refused
    settleLastModified(time) -> time   // sets mtime where possible, returns what a scan will report
```

Semantics:

- **Path vs locator.** A *path* is canonical and cross-device (§3). A *locator* is the backend's own
  handle (absolute path, URI, row id) - never compared across devices, never sent.
- **Writes are positional** (`write(offset, bytes)`): upload chunks may land out of order. A backend
  location that cannot seek cannot receive transfers.
- **`place` is the only way bytes enter a final location**, and it is atomic: there is never a moment
  with neither the old nor the new file.
  - Same backend: rename over the target.
  - Otherwise: copy into a part file beside the target (`name.fserver-part.ext` - extension kept
    last so type-checking backends accept it), rename the part over the target, delete the source. A
    failure leaves the source intact and drops the part.
  - Where a backend cannot rename over an existing file: rename the existing one aside
    (`name.fserver-replaced`), rename the new one in, delete the aside one; on failure roll both
    renames back. A backend that silently picks a different name is a refusal.
- **`settleLastModified`**: after placing a received file the engine sets the sender's mtime and
  **records whatever the backend reports back** (backends round to seconds or refuse). Recording the
  requested value instead would make the next scan see an edit.
- **A media library refuses non-media names** (by extension type): a file its own scan never reports
  would be sent to it forever.
- **Path safety**: every path handed to `createFile`/`place` may come from a peer. Segments are split
  on `/` and `\`, `.` and empty segments dropped, and any `..` is `InvalidPath`. Locators are confined
  to the location's root. A shared location opens only the items it was given.
- **App-private buckets prune empty directories** left by deletes and moves.
- **Staging** is cache space for bytes on their way in. The OS may clear it: nothing there is ever the
  only copy of anything.

## 3. Canonical paths and file identity

Two devices keep the same file in different places, so everything cross-device derives from one
canonical form:

- separators `/`; no empty segments, no `.`;
- every segment **Unicode NFC** (some file systems hand out decomposed names; without NFC an accented
  name differs between platforms);
- case kept as written;
- locations spanning several volumes (`Root`, `Media`) lead with a volume id: `primary` for built-in
  storage, the volume UUID for removable media; single-directory locations have no volume prefix.

**File id** = lowercase hex `SHA-256(UTF-8 canonical path)`. Derived from the path alone - never from
a source or device id, which are local. Changing this derivation is a protocol change.

Consequences: a rename is a new file id (delete + new file; the planner pairs them back into a move,
[planning §5](../sync/planning.md#5-move-pairing)). Two paths differing only in case are two files; a
receiver on a case-insensitive file system refuses the second one rather than overwrite the first
(`checkPath` fails), and the user sees it as a skipped file.

## 4. Scanning

A scan walks the whole location and reports every regular file with size and mtime as the backend
sees them, with progress (files, bytes), cancellable. Ignoring temp files, lock files and system junk
is the indexer's job ([indexing §3](../sync/indexing.md#3-what-a-scan-skips)).

Paths are built from names on the way down, never parsed out of backend-specific ids. Times are
normalized to instants regardless of the backend's unit.

## 5. Encryption seam

The engine never opens a source's file system directly: it goes through one factory that wraps every
encryptable location in the encrypting decorator - see [at-rest encryption](at-rest-encryption.md).
Everything above it sees plaintext and plaintext sizes.
