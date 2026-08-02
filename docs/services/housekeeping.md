# Housekeeping: garbage collection and the activity journal

> Background cleanup of what crashes and abandoned transfers leave behind, and the journal that tells
> the user what the engine did and what is still wrong.

## 1. Garbage collection

Runs at startup, after every round of local passes and after every pass the peer drove; one run at a
time (a run already in progress makes a new request a no-op). Each step is best effort.

| Garbage                              | Rule                                                                                  |
|--------------------------------------|---------------------------------------------------------------------------------------|
| staged source uploads                | row untouched for **1 day**, or its bytes are gone (the OS cleared the cache) → delete row + bytes |
| staged files without a row           | older than **1 hour** → delete (younger ones may be an upload between creating the file and writing its row); one-shot staging excluded |
| one-shot staging                     | transfer no longer incoming and `Active` → delete                                     |
| one-shot outbox copies               | transfer finished, or no transfer row and older than 1 hour → delete                  |
| copies fetched on demand             | fetched more than **1 day** ago, in sources that evict locally → evict again ([eviction §2](../sync/eviction.md#2-who-evicts-when)) |
| stale part files (`.fserver-part`)   | at most **once a day**, walking every active source raw (past the encryption seam - nothing reads content); older than 1 hour → delete |

Removing a source never deletes its files; nothing in GC touches a file that has an index row as
`Present`, except the fetched-copy eviction, which goes through the normal confirmed eviction path.

## 2. Activity journal

The user-facing record of the engine. Two kinds of entries:

- **events** - things that happened (pass completed with a tally, file fetched, device paired or
  forgotten, source added/removed/requested/accepted/refused/changed, conflict resolved, decision
  dropped, encryption migrated, one-shot finished, peer pass failed);
- **issues** - problems that are still true, each with a stable **key**:

| Issue                    | Key                          | Raised when                                        | Solved when |
|--------------------------|------------------------------|----------------------------------------------------|-------------|
| `PassFailed`             | `pass:<source>`              | a local pass fails                                 | a pass over the source succeeds (either side) |
| `SealedFilesUnreadable`  | `sealed:<source>`            | a pass fails on a missing key / unknown cipher / corrupted file | a pass succeeds |
| `ConflictHeld`           | `conflict:<source>:<file>`   | an Ask conflict is held                            | resolved, or a pass no longer plans it |
| `ClockSkewed`            | `clock:<device>`             | the peer's clock offset > 60 s                     | a normal measurement |
| `IncompatibleDictionary` | `dictionary:<device>`        | a dial fails on the peer's dictionary              | a dial succeeds |
| `ConnectionRefused`      | `connect:<device>`           | a dial fails on authentication, identity mismatch or handshake | a dial succeeds |
| `EncryptionIncomplete`   | `encryption:<source>`        | files could not be migrated                        | a migration run finds nothing due |

Rules:

- Raising an issue whose key is open **bumps** it (`lastSeenAt`, `occurrences`) instead of adding a
  row; once solved, the same problem opens a new entry.
- Removing a source solves its issues; forgetting a device solves the device's issues.
- Silence is not an issue: a dial that simply found nobody is recorded in reachability
  ([discovery §5](../network/discovery.md#5-reaching-a-device)), not here.
- The user may dismiss an issue or clear settled entries; open issues are never trimmed.
- At most 1000 entries are kept besides open issues.
- **Writing the journal never fails the action it describes.**
