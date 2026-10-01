# Overall project TODO list

### App

Minor:

- Check TODOs in code;

## Core

RequirementsChecker:

- A raw-path source (`Root`, `Directory`) cannot work on API 29 and `rawPathPermissions` now asks
  for nothing there, so the source reports itself ready and then finds nothing. 24-28 reach the path
  with the runtime pair and 30+ with `MANAGE_EXTERNAL_STORAGE`, so the hole is one api level wide.
  Two ways out: `android:requestLegacyExternalStorage="true"` in `:app` plus
  `WRITE_EXTERNAL_STORAGE` capped at 29 instead of 28 and the rule's boundary back at
  `ALL_FILES_ACCESS_SDK` - Android 10 honours the flag whatever the app targets, only API 30+
  ignores it, worth confirming on an emulator - or a new `Requirement` saying the os cannot serve
  this source, pointing the user at a document tree.

Sync (see `../../../docs/HLC.md`):

- A user edit made while an incoming file is being received is overwritten. Before the rename,
  compare the on-disk hash with the index;
- A conflict where the winning side is evicted is never resolved, and repeats every pass;
- Clock skew is only logged: no offset estimate at handshake (NTP-style) and no diagnostics.
  In host/public mode the physical time does not come from the server;

Idea:

- Implement actions logging into database;
- Short targeted discovery session as the last resort, once every cheaper route has failed:
  discovery as a whole rather than mDNS - a device paired over Nearby Connections cannot be synced
  without it at all - time-boxed, and looking only for known device ids.
- Folders in the index: today it holds files only, so an empty folder neither syncs nor shows in
  the `DocumentsProvider`. Needs a record kind in `FileRecordDto` (protocol change - server and
  other clients too), schema, scanner, and sync semantics: mkdir/rmdir, folder-vs-file name
  conflict, non-empty folder deleted on the peer, folder rename. Until then, a provider write stage
  can create real folders on disk and list empty ones from disk without indexing them.

Documents provider:

- Listing a folder reads the whole source (`FilesController.content`). Add a subtree query
  (`path LIKE 'dir/%'`) to the index stores once sources grow large;
- Thumbnails and the app's own viewers read `locator` directly through Coil, bypassing `:core`:
  an encrypted source will break them. Needs a Coil fetcher over `SourceFileReader`;
- Write support (`w`/`rw`, create, delete, rename) - read-only for now.

Major:
- add QR connection via OutOfBandKeyAuthMethod
- no compatibility with different hashers (e.g. SHA-256 vs BLAKE3) - need to add a selection based on already used hash info
- impl storage migrations

## Net

Major:

- Module became extremely large in short time. Worth full-review;
- Need actual security checks (MITM, downgrade, etc.);

Minor:

- Add support for auth versioning, e.g. same SAS/PAKE but with different versions;
- Update trust downgrade detection: it should protect against downgrading a single method, rather
  than switching between different methods;

Idea:

- Admission gate: refuse to answer at all unless the caller shows it already knows this device.
  Today knowing a peer is an *authentication* input (`oob-key-1`), never an admission one, and the
  public greeting is a forced reply to anyone who dialled (`Connection Protocol.md` §2.3).
  Worth having as a separate, orthogonal mechanism - but not keyed on the identity key:
  - the key and its fingerprint are **not secrets**. They are on the QR, and every completed
    handshake states the key. So a knock on them stops a port scanner and stops nobody else;
  - nothing there is revocable. A peer that ever paired, or anyone who photographed the code, can
    knock forever, and taking that away means rotating the identity key and dropping every pin;
  - a static value replayed in the clear is replayable by a passive LAN observer.

  Two pieces to pull apart, then:
  - *cannot knock without the key* - the honest construction is Noise `IK` (first message encrypted
    to the responder's static key), which is what `Connection Protocol.md` §6.1 already names for
    the QR row. Not replayable, and the current `oob-key-1` (transcript signature) is the staged
    predecessor of it. Still gives no revocation;
  - *may not connect* - a generated access code with real entropy, carried by the QR next to the
    fingerprint, rotatable and never stated by a handshake. ToR §8.1 already expects one for the
    password mode, so it is the same token in text form.

  Note ToR §3.2 currently says the opposite for the QR mode - access is open, hidden mode protects
  a device by not announcing it, and the code exists so the *client* can verify the *server*. Any
  of this is a `Connection Protocol.md` change first: the server and the other clients follow it
  too, so it is not an Android-local decision;
