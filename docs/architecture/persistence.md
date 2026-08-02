# Persistence

> What the engine persists and the contract a persistence backend must keep.

## 1. The boundary

The engine never touches disk for its own state. It receives one bundle of narrow **stores** shaped
for the engine, and the host decides where the bytes live. A store is an SPI the engine calls, not a
repository a UI reads; UI-facing queries are a separate surface of the backend.

Every store survives process death: the engine resumes from them after a crash without data loss
(ToR §4.3). Schema changes are applied by versioned migrations; old state is never silently dropped.

## 2. Stores

| Store               | Holds                                                                        | Keyed by                 |
|---------------------|------------------------------------------------------------------------------|--------------------------|
| identity            | this device: id, display name, kind; access to the long-term identity key  | -                        |
| auth                | auth methods this device offers to peers (incl. its password / PIN)         | -                        |
| trust               | pinned peer keys, device metadata, last known route per transport, failed-contact runs | public key / device id |
| sources             | registered sources, per-device source metadata, **source tombstones**        | source id                |
| source requests     | sources a peer asked this device to host, until the user answers            | source id                |
| index               | the local index: one row per file a source has worked through                | (source id, file id)     |
| remote index        | the peer's index as last reported - a cache                                  | (source id, file id)     |
| conflict decisions  | the user's choices on held conflicts, until a pass carries them out          | (source id, file id)     |
| uploads             | staged inbound uploads (resume state)                                        | (source id, file id)     |
| clock               | the last issued HLC reading                                                  | -                        |
| one-shot transfers  | one-shot transfers and their per-file state                                  | transfer id              |
| storage keys        | at-rest data keys, wrapped                                                   | key id / source id       |
| journal             | activity log and open issues                                                 | entry id / issue key     |

Row shapes are documented with the behavior that owns them: index rows in
[indexing](../sync/indexing.md#2-the-index-row), staged uploads in
[transfers](../sync/transfers.md#5-staging-and-resume), decisions in
[conflicts](../sync/conflicts.md#3-ask), keys in
[at-rest encryption](../files/at-rest-encryption.md#8-keys).

## 3. Contracts

1. **An index row is marked processed only after the hand-off.** Recording a file before its bytes
   were placed/sent means a failure leaves it skipped forever. Writes replace by row.
2. **Listing a source's processed files** is read once per pass and compared with the whole scan; it
   returns tombstones and evicted rows too, and tolerates the full source.
3. **Clearing a source's index touches no bytes on disk** - forgetting bookkeeping is never an
   eviction or a deletion.
4. **Deleting a source leaves a tombstone** `(sourceId, deviceId, removedAt, lastLocation)` and
   drops the source's index, remote index and metadata in one transaction. The tombstone is what
   answers the peer later ([sources §5](../sync/sources.md#5-removal)). Refusing a request leaves a
   location-less tombstone.
5. **Replacing a source's remote index replaces the whole set.** An empty list is valid news.
6. **Trust records are per key, metadata per device.** A device may present several keys over time;
   last seen, kind and last network are shared by all keys of a device id. A pin is stored as given -
   the caller already computed the strongest method. Auth strength is persisted **by name**, never by
   ordinal.
7. **Forgetting a device** drops every key, the known routes, metadata and the failed-contact run.
8. **Storage keys are never stored unwrapped**; the wrapping key lives outside the app's data, so a
   copy of the database alone opens nothing.
9. **The HLC reading is persisted on every tick**, so readings keep growing across restarts.
10. **The journal** keeps at most 1000 entries besides open issues, which are never trimmed.

## 4. Deliberately not persisted

| State                               | Why in memory only                                                       |
|-------------------------------------|--------------------------------------------------------------------------|
| sync leases                         | a lease dies with the process holding it - that is what a restart means  |
| peer clock offsets                  | measured again before every pass                                         |
| handshake profiles, discovered peers | re-learned per run                                                      |
| handshake throttle / backoff        | per run ([authentication §7](../network/authentication.md#7-throttling)) |
| pass / transfer progress            | UI state                                                                 |
