# Data export

> Everything this device holds - files, their metadata, settings - as one ZIP archive (ToR §3.9).

## 1. What goes in

- **Files**: every `Present` file of the selected sources, as **plaintext** - also from encrypted
  sources ([at-rest encryption §3](../files/at-rest-encryption.md#3-key-decision-encryption-is-local-to-the-device)).
- **Metadata only** for evicted files and files only the peer holds: nothing is downloaded for an
  export.
- **Settings**: this device, offered auth methods, trusted devices (a partial export keeps only the
  selected sources' peers), sources.
- **Host entries**: opaque files the host adds (its own settings), plain names only.
- **Never**: secrets - identity key, storage keys, the password/PIN values of auth methods.

## 2. Archive layout

```
settings/device.json
settings/auth.json
settings/trusted-devices.json
settings/sources.json
host/<name>                          host-supplied entries
files/<sourceId>/<canonical path>    file bytes
metadata/<sourceId>.json             one record per local file (+ "archived" flag) and per peer-only file
manifest.json                        exportedAt, scope (All | Sources), device, sources, files, bytes,
                                     metadataOnly, skipped
```

`manifest.json` is written last, so an archive without it is incomplete.

## 3. Behavior

- Scope: all sources, or a selected set (an unknown id fails up front).
- Progress: files and bytes, against totals computed up front.
- **Read failures are forgiven, write failures are not**: a file that cannot be read (source not
  reachable, file gone, missing key) is listed in the report as skipped; a failure writing the output
  fails the whole export. The caller owns the output stream and deletes a partial archive.
- Files are deflated at the fastest level (most are already compressed media).
