# Overall project TODO list

### App

Major:

- Create unified error parser;
- Link security settings screen to :core and :net;
- Find and localize raw strings (mostly in compose and VMs);

Minor:

- Organize domain and data layers;
- Files picker deserves better UI;
- Check TODOs in code;
- Needs a lot of UI/UX improvements;

## Core

Major:

- Impl file storage part;

Minor:

- It is necessary to decide on a spot where all incoming connections will be processed;
- This module is a complete piece of crap, too many useless classes, too much trivial
  mappings/proxying. Need to find a way to relieve it, at least partially;

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
