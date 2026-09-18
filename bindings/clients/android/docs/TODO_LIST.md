# Overall project TODO list

### App

Major:

- Link security settings screen to :core and :net;

Minor:

- Add sync worker;
- Check TODOs in code;
- Needs a lot of UI/UX improvements;
- A failed sync pass carries no cause, so `AppViewModel` reports the same line for every one of
  them. Type it the way a dial is typed and `toAppError` can say what broke;

## Core

RequirementsChecker:

- `DevicesRepositoryImpl.probe` / `connect`: gate on `forTransport`. An `Ip`/`QrPayload` locator
  runs under `ManualAddress` rules (connectivity, `ACCESS_LOCAL_NETWORK` from API 37); a
  `DiscoveredDevice` may dial a Nearby route that needs Bluetooth permissions and radios on.
  Behaviour change - a manual connect starts refusing before it tries - so decide the UX first.
- `PeerIndexFetcher.tryToConnectByDeviceId`: a sync pass dials a peer with no check at all, so a
  radio switched off surfaces as an opaque pass failure with nothing to show the user.
- `FServerCore.startServing` / `PeerRequestServer`: the listener comes up without checking
  connectivity or the local-network permission.
- `forNetworkInfo` has exactly one caller (`DeviceDiscoveryViewModel`). Any other screen naming the
  network gets the redacted placeholder with no explanation - see `Screen Data Wiring.md`.
- A raw-path source (`Root`, `Directory`) cannot work on API 29 and `rawPathPermissions` now asks
  for nothing there, so the source reports itself ready and then finds nothing. 24-28 reach the path
  with the runtime pair and 30+ with `MANAGE_EXTERNAL_STORAGE`, so the hole is one api level wide.
  Two ways out: `android:requestLegacyExternalStorage="true"` in `:app` plus
  `WRITE_EXTERNAL_STORAGE` capped at 29 instead of 28 and the rule's boundary back at
  `ALL_FILES_ACCESS_SDK` - Android 10 honours the flag whatever the app targets, only API 30+
  ignores it, worth confirming on an emulator - or a new `Requirement` saying the os cannot serve
  this source, pointing the user at a document tree.

Idea:

- Implement actions logging into database;
- Add incoming sync requests expiration;
- Add "trash" to keep files before final deletion.
- Short targeted discovery session as the last resort, once every cheaper route has failed:
  discovery as a whole rather than mDNS - a device paired over Nearby Connections cannot be synced
  without it at all - time-boxed, and looking only for known device ids.

Major:

- Add sync mode x source location checks (e.g. deny mirror strategy for media source);
- Bad files transferring speed;
- Single connected device never goes back to offline;

Minor:

- This module is a complete piece of crap, too many useless classes, too much trivial
  mappings/proxying. Need to find a way to relieve it, at least partially (seems to be fixed by
  migration to rust);

# Files

Major:

- Implement more strategies;

## Net

Major:

- Module became extremely large in short time. Worth full-review;
- Need actual security checks (MITM, downgrade, etc.);

Minor:

- Case: both devices keep trusted records for each other, but one of them forget another. Then, when
  connecting, only this device will have the SAS code;
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
