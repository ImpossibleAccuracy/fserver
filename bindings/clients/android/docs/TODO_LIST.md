# Overall project TODO list

### App

Major:

- Update home screens design.
- Add UI for incoming sync requests.
- Create unified error parser;
- Link security settings screen to :core and :net;
- Find and localize raw strings (mostly in compose and VMs);

Minor:

- Add sync worker.
- Organize domain and data layers;
- Files picker deserves better UI;
- Check TODOs in code;
- Needs a lot of UI/UX improvements;

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

App side of the same audit:

- `ManualAddressViewModel` / `QrScanViewModel`: no `forTransport(ManualAddress)` and no resolver, so
  a missing permission reads as a failed connection.
- `PairingViewModel.probe`: same, before pairing starts.
- `SourceTargetHandler.startDetection`: `RequirementsNotMetException` goes to `Timber.w`, so the
  user sees "nothing found" instead of being asked for the permission.

Idea:

- After the first confirmed synchronization, the devices can exchange randomly generated passwords,
  which can then be used to connect without user intervention;
- Implement actions logging into database;
- Add incoming sync requests expiration;
- Add "trash" to keep files before final deletion.

Major:

- Add sync mode x source location checks (e.g. deny mirror strategy for media source)
- Bad files transferring speed.

Minor:

- It is necessary to decide on a spot where all incoming connections will be processed;
- This module is a complete piece of crap, too many useless classes, too much trivial
  mappings/proxying. Need to find a way to relieve it, at least partially;

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
