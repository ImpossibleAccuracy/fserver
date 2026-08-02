# Terms of Reference

### Cross-platform file exchange with an open architecture

> Working document. This version consolidates the original project description and the design
> decisions taken so far. Legal points are guidelines, not a legal opinion.

---

## 1. General

### 1.1. Purpose

A cross-platform application for transferring and storing files between a user's devices (later -
between different users), with an open architecture. Works over the local network and through a
public server. The key focus is user convenience and flexible configuration on both the client and
the server side.

### 1.2. Core principles

- **Open source.** Anyone can build their own client or server.
- **Core ↔ UI separation.** Logic (transport, sync, storage, encryption) lives in an independent
  core; the UI talks to it through a documented local API.
- **Documented, versioned protocol.** Third-party clients and servers are compatible thanks to an
  open protocol specification and version negotiation at handshake.
- **The server does not trust the client.** Since anyone can write a client, all authorization and
  permission checks run on the server side. Restrictions in the client are for convenience only.
- **Convenient by default.** Onboarding, clear settings, one-click server presets, encryption out of
  the box.

---

## 2. Terms and modes

| Mode        | Essence                                                                                                         | Direction                 | Purpose                       |
|-------------|-----------------------------------------------------------------------------------------------------------------|---------------------------|-------------------------------|
| **Sync**    | The same set of files on every device                                                                           | Bidirectional             | Shared access to current data |
| **Host**    | Files physically live on one device/server, the others access them over the network                             | Client → host             | "Network drive / NAS" model   |
| **Offload** | Files move to a backup device and are removed from the source to free space; network access to them stays local | One-way (source → backup) | Freeing device storage        |

> Terminology note: the mode previously called "backup" is in fact **storage offloading**
> (tiering): fresh files are kept locally, old ones move to the backup device. It is not a backup,
> so the UI says "offload/archive", not "backup". A real backup with redundancy may be added later
> as a separate mode.

---

## 3. Functional requirements

### 3.1. Discovery and connection

- Automatic discovery of devices on the local network (mDNS/Bonjour or similar).
- Manual connection by address: IP or domain + port.
- Device pairing with key exchange and confirmation of the new device.
- An abstract transport layer, ready for future connection types (USB, Bluetooth, Wi-Fi Direct).

### 3.2. Access and trust model

The access type is set when the server is configured:

- **Fully open access** - anyone on the network can connect. A high-risk mode; choosing it requires
  an explicit warning.
- **QR access** - access is open, but the server does not publish itself on the local network (is
  not discovered automatically); connecting is only possible by code.
- **Password access.**
- **Access by other keys** (an extensible set of authentication methods).

Requirements for the access code (QR/password/key):

- The code carries not only the server address but also the **fingerprint of the server's public
  key** - so the client can check it is connecting to the right server (trust on first connect).

### 3.3. Transport and transfer

- Integrity check of transferred files by content hash.
- Resuming after a dropped connection (resumable).
- Streaming of large files.
- Optional on-the-fly compression.
- The transport is always encrypted (TLS/mTLS) regardless of mode.

### 3.4. Modes (in detail)

#### Sync

- Bidirectional maintenance of an identical set of files.
- **The order of versions is set by version vectors, not by time.** Every file has a vector of "how
  many edits each device made". Comparing vectors yields: the versions are equal, one is newer than
  the other, or the edits are concurrent - only the last one is a conflict. File time (mtime) and
  device clocks are not used for ordering.
- **A new version only on a content change** (a different hash, a different size, a file appearing
  or disappearing). A change of the modification date alone is not a version. The hash may be
  computed lazily, but until it is, the file is neither overwritten nor deleted by an incoming
  version.
- **A deletion is a new version** (a tombstone with a bumped vector), so "deleted here, changed
  there" is detected as an ordinary concurrent conflict instead of the deletion silently winning.
- A version accepted from another device (a file, a deletion) is recorded **with the sender's
  vector**, without an edit of our own. The same content under different vectors is not a
  conflict: the vectors are merged on both sides.
- **Automatic conflict resolution: last-write-wins** - only for concurrent edits, and the winner is
  picked deterministically by the hybrid logical clock (HLC) timestamp and the device id, so that
  every device independently reaches the same result.
- A device whose clock has run ahead by more than the allowed threshold gets a warning.
- The resolution mode is a user setting (details - [`sync/conflicts.md`](sync/conflicts.md)):
    - **Ask** (default) - the user's file does not change without their decision: each side keeps
      its
      own version until the user picks theirs, the other one, or both;
    - **Last Write Wins** - automatic, the losing content is not kept.
- **In the future:** a local recovery bin for LWW; choosing the strategy through the server config,
  manual resolution, etc.

#### Host

- All files live on one device/server; the others access them over the network.
    - Files are accessible only while the host is online.

#### Offload

How it works:

1. Files are created on the source device and synced to the backup device.
2. After a set period (or by an eviction criterion) the app on the source checks that the file is
   present on the backup device.
3. Once the copy is confirmed, the file is removed from the source; network access to it remains
   locally.

Requirements:

- **The direction is strictly one-way** (source → backup).
- Removing from the source is **eviction (`evict`), not deletion (`delete`)**: the sync engine must
  not treat freeing space as the user deleting a file and propagate it to the backup.
- The local copy is removed **only after the copy on the backup is confirmed by content hash** (the
  file was received and reliably written).
- **Placeholders (stub files):** after eviction the user still sees the file in the UI as "cloud" -
  with locally kept metadata and a preview. Tapping it downloads the file on demand.
- Eviction criterion: by age (N days) and/or by last access (LRU); the ability to **pin** a file so
  it is never evicted.
- The UX says explicitly: evicted files are available only while the backup device is online.

### 3.5. Users and accounts

- **Stage 1:** one user - several devices.
- **Stage 2:** the server serves several users.
    - Every user has their own account.
    - By default a user sees only their own files.
    - A user can open their files for **public read-only access**.
    - Data isolation between accounts; storage quotas.

### 3.6. Security and encryption

- **Encryption by default:** files are encrypted in storage (at rest), the transport is always
  encrypted.
- **With AI enabled:** the server decrypts files for indexing. The connection stays encrypted.
- A model the UI must reflect honestly: since the server can decrypt files for AI, the keys are
  available to the server - this protects against theft of the storage medium, not against an
  untrusted server.
- The AI toggle comes with an explicit warning that the server gets access to the content of those
  files.
- **AI scope is per folder:** only folders the user explicitly hands to AI are decrypted and
  indexed.
- Authentication and authorization of devices/users - on the server side.

### 3.7. Viewing and editing

- **Built-in viewing:** images, video, audio, PDF, text (i.e. common office formats).
- **Light editing (MVP):** renaming, rotating/cropping images.
- Full video/audio editing is outside the MVP.
- For video/audio, relying on the platform's system codecs is preferred (licensing and performance
  reasons).

### 3.8. UX, onboarding, presets

- Onboarding for new users.
- Clear settings and connection diagnostics.
- **Server-side presets** - a configuration profile applied in one click (for example, through a QR
  code or a link with a limited lifetime). The profile includes the address, the access method and
  the server key fingerprint.

### 3.9. Data export

- Exporting all files together with metadata and settings into a single archive (data portability,
  migration, compliance with the right to portability).

### 3.10. AI assistant (future)

- Searching and structuring files based on content indexing.
- Works only on folders the user explicitly enabled (see 3.6).

---

## 4. Non-functional requirements

### 4.1. Scale (MVP)

- Target - up to **~1000 files** per storage.
- The limit is set **both by file count and by total size** (1000 notes and 1000 videos of 4 GB are
  incomparable loads).
- Limits are set **per storage**; there is no overall limit across all storages of a device.
- At this scale a full comparison of file sets during sync and an index in SQLite are acceptable;
  delta sync and complex chunking are not required.
- Engine interfaces are designed to be replaceable, so delta sync / a large-scale index can be added
  later without a rewrite.

### 4.2. Cross-platform

- The list of target platforms is fixed separately (critical for effort estimation).
- Platform limits are taken into account (background activity, file system access, local network
  access - especially on mobile OSes).

### 4.3. Reliability and performance

- Recovery after a crash without data loss or corruption.
- Local state storage (index, versions, metadata) - for example, SQLite.
- Smooth UI when viewing large media files.

### 4.4. Protocol and compatibility

- A versioned protocol with version negotiation at handshake; the server serves older clients
  correctly.
- File version metadata (version vector, HLC timestamp, author device) is part of the protocol: all
  clients and the server keep and transmit it the same way.
- A stable, documented core ↔ UI boundary (local API), also versioned.

### 4.5. Licensing

- The choice of open-source license is made explicitly: copyleft (GPL/AGPL) restricts
  closed/commercial forks, permissive (MIT/Apache) allows them. The decision affects the ecosystem
  of third-party clients.

---

## 5. Architecture (overview)

```
┌───────────────────────────────────────────────┐
│                  Client(s)                    │
│  ┌─────────────┐         ┌───────────────────┐│
│  │     UI      │ <────>  │   Core            ││
│  │(replaceable)│local API│ transport, sync,  ││
│  └─────────────┘         │ storage, crypto   ││
│                          └─────────┬─────────┘│
└────────────────────────────────────┼──────────┘
                                     │ versioned protocol
                     ┌───────────────┴───────────────┐
                     │            Server             │
                     │  authorization · accounts ·   │
                     │  storage · (opt.) AI index    │
                     └───────────────────────────────┘
```

- **Core** - an independent component: discovery, transport (abstracted from the connection type),
  the sync/offload engine, encryption, the local index.
- **UI** - a replaceable layer on top of the core.
- **Server** - the source of truth for authorization, accounts, permissions and (optionally) the AI
  index.

---

## 6. Staging (roadmap)

1. **MVP.** LAN discovery + direct file transfer between two devices + viewing images/video. Scale -
   up to ~1000 files.
2. **Sync** (version vectors + LWW by HLC for conflicts) + **host mode**. Encryption by default.
3. **Public server:** access models (open / QR / password / keys), one-click presets, server key
   fingerprint.
4. **Offload** (eviction with placeholders and on-demand download) + archive export + extended
   format viewing.
5. **Multi-user server** (accounts, isolation, public read-only) + **AI assistant** (per-folder
   indexing).
6. **New transports** (USB, Bluetooth, Wi-Fi Direct), configurable conflict resolution strategies.
