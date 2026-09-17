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
- A raw-path source (`Root`, `Directory`) cannot work on API 29 and `rawPathPermissions` now asks for
  nothing there, so the source reports itself ready and then finds nothing. 24-28 reach the path with
  the runtime pair and 30+ with `MANAGE_EXTERNAL_STORAGE`, so the hole is one api level wide.
  Two ways out: `android:requestLegacyExternalStorage="true"` in `:app` plus `WRITE_EXTERNAL_STORAGE`
  capped at 29 instead of 28 and the rule's boundary back at `ALL_FILES_ACCESS_SDK` - Android 10
  honours the flag whatever the app targets, only API 30+ ignores it, worth confirming on an
  emulator - or a new `Requirement` saying the os cannot serve this source, pointing the user at a
  document tree.

Reachability (auto-sync without a scan running):

- Reachability reporting is in (`DeviceReachability`, the `failedContact` table, and the red device
  card on Files), but it stops at telling the user. Left to do: the unreachable block names the ways
  round a dead end in a sentence - manual address, QR - where it should offer them as buttons that
  open those screens for that device.
- Short targeted discovery session as the last resort, once every cheaper route has failed:
  discovery as a whole rather than mDNS - a device paired over Nearby Connections cannot be synced
  without it at all - time-boxed, and looking only for known device ids.

Idea:

- Implement actions logging into database;
- Add incoming sync requests expiration;
- Add "trash" to keep files before final deletion.

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
