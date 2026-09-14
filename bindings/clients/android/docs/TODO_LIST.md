# Overall project TODO list

### App

Major:

- Create unified error parser;
- Link security settings screen to :core and :net;
- Find and localize raw strings (mostly in compose and VMs);

Minor:

- Add sync worker.
- Organize domain and data layers;
- Check TODOs in code;
- Needs a lot of UI/UX improvements;
- Fix sync errors displaying;

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

Reachability (auto-sync without a scan running):

- Reachability reporting is in (`DeviceReachability`, the `failedContact` table, and the red device
  card on Files), but it stops at telling the user. Left to do: the unreachable block names the ways
  round a dead end in a sentence - manual address, QR - where it should offer them as buttons that
  open those screens for that device.
- Short targeted discovery session as the last resort, once every cheaper route has failed:
  discovery as a whole rather than mDNS - a device paired over Nearby Connections cannot be synced
  without it at all - time-boxed, and looking only for known device ids.
- Fixed port list for the mDNS transport. `MulticastDnsTransport.listen` binds port `0` today, so a
  restart moves the listener and every stored route for it goes stale. Wanted: a short list of
  preferred ports tried in order with an ephemeral fallback, a dial that retries the rest of the
  list, and - separately - a port guess over the host of an inbound route, which is non-dialable
  because `socket.port` is the peer's *source* port, something a port list does not fix.
  Cross-platform: the numbers belong in `Connection Protocol.md`, not in an Android decision.

Architecture:

- Policy over the engine is piling up in `:core` - `PresenceController` decides when the radios
  run, `AutoSyncCoordinator` decides when a pass starts. Both are default policy, not engine, and
  belong in a `:core:lifecycle` module shaped like `:core:storage`: separate artifact, droppable by
  a host that drives `FServerCore` itself. Presence needs only the public surface already; the
  coordinator would need `SourcesController.runSync(deviceId)` and a narrow
  `devicesWithActiveSources` published first - not a source listing, which stays a repository.

Idea:

- After the first confirmed synchronization, the devices can exchange randomly generated passwords,
  which can then be used to connect without user intervention;
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
