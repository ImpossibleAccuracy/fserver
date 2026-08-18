# Overall project TODO list

### App

- Create unified error parser;
- Organize domain and data layers;
- Link security settings screen to :core and :net;
- Files picker deserves better UI;
- Find and localize raw strings (mostly in compose and VMs);
- Check TODOs in code;
- Needs a lot of UI/UX improvements;

## Core

- This module is a complete piece of crap, too many useless classes, too much trivial
  mappings/proxying. Need to find a way to relieve it, at least partially;
- It is necessary to decide on a spot where all incoming connections will be processed;
- Add actual network info collector;

## Net

- Module became extremely large in short time. Worth full-review;
- Case: both devices keep trusted records for each other, but one of them forget another. Then, when
  connecting, only this device will have the SAS code;
- Add support for auth versioning, e.g. same SAS/PAKE but with different versions;
- Update trust downgrade detection: it should protect against downgrading a single method, rather
  than switching between different methods;
- Need actual security checks (MITM, downgrade, etc.);
