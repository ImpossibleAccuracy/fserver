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

Idea:

- After the first confirmed synchronization, the devices can exchange randomly generated passwords,
  which can then be used to connect without user intervention;
- Implement actions logging into database;
- Add incoming sync requests expiration;
- Add "trash" to keep files before final deletion.

Major:

- Add sync mode x source location checks (e.g. deny mirror strategy for media source)

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
- Extract peer identity fetch from auth methods to `AuthPhase.kt`;

Minor:

- Case: both devices keep trusted records for each other, but one of them forget another. Then, when
  connecting, only this device will have the SAS code;
- Add support for auth versioning, e.g. same SAS/PAKE but with different versions;
- Update trust downgrade detection: it should protect against downgrading a single method, rather
  than switching between different methods;
