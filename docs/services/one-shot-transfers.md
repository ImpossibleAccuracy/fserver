# One-shot transfers

> Sending files to a device once, outside any source: no index, no versions, no tombstones.
>
> The bytes move with the shared upload protocol: [transfers](../sync/transfers.md).

## 1. Model

```
OneShotTransfer {
    id                 // chosen by the sender; both sides hold the transfer under it
    peer               // device id + display name snapshot (an unpaired peer has no trust record to read later)
    direction          // Outgoing(origin location) | Incoming(destination location? - null until accepted)
    status             // Pending → Active → Completed | Declined | Cancelled | Failed(reason)
    files[]            // index, name (display only), size, locator?, committedBytes, status Pending|Completed|Failed
    createdAt, finishedAt?
}
```

All files of one outgoing transfer come from one origin location. Shared `content://` files (share
sheet) are first **copied into an app-private outbox** (`Internal("oneshot-outbox")`,
`<transferId>_<index>`): the grant ends with the sharing task, the copy lasts as long as the transfer.

## 2. Exchange

```
sender                                                   receiver
  create (validate: 1..1000 files, no duplicates, all readable)
  status = Pending
  ── OneShot.Offer {transferId, senderName, files[index, name, size]} ──▶
                                                         validate (indices 0..n-1, names ≤ 1000 chars)
                                                         park as Incoming/Pending (user decides; host may auto-accept)
                                                         accept(destination) → Active
  ◀── OneShot.Decision {transferId, accepted} ──
  Active → push every Pending file: Upload.Init(OneShot(id, i)) …   (receiver: §3)
  file outcomes → transfer settles
  ── OneShot.Cancel {transferId, reason} ──▶ / ◀──      either side, any time; final
```

Rules:

- **Best-effort messaging**: state changes are recorded first, then sent; a lost message is repaired
  by re-sending. Unanswered offers are re-offered and accepted transfers re-sent at startup and
  whenever the peer connects again; the host may also `retry`.
- **Re-offers are answered, not re-parked**: an offer for a known transfer re-sends the stored
  verdict (`Active`/`Completed` → accepted, `Declined` → declined, `Cancelled`/`Failed` → cancel).
- **Every message is checked against the peer the transfer was recorded with** and its direction - a
  device can only touch its own transfers. An offer reusing an id that is not that peer's incoming
  transfer is ignored.
- Offered names are display hints, never paths.

## 3. Receiving files

Uploads with key `OneShot(transferId, index)` are admitted only while the transfer is this peer's,
incoming and `Active` ([transfers §4.1](../sync/transfers.md#41-admission-init)); otherwise `Stopped`,
which ends the sender's side. A completed file answers `Init` with `Completed`.

- Staging: `<staging>/oneshot/<transferId>/<index>`; the resume offset lives on the transfer's file
  record (`committedBytes`). If the OS cleared the cache, the sender starts that file over.
- Placement: the name is sanitized (last path segment only, no control or `/\:*?"<>|` characters, no
  leading dots, ≤ 200 chars, fallback `file`) and made free in the destination
  (`name (1).ext`, `name (2).ext`, …) - **a received file never replaces one the user has**. Choosing
  the name and placing are one locked step.
- A hash mismatch discards the staged bytes and resets `committedBytes`.

## 4. Settling

- A file ends `Completed` (whole and hash-checked) or `Failed`: the origin file is gone/unreadable
  (`Abandon` sent), or the receiver keeps refusing.
- The transfer settles when no file is `Pending`: `Completed` if anything arrived, else
  `Failed("No file was transferred")`.
- A link failure leaves the transfer `Active`; it resumes when the peer is back.
- `Cancel` (either side) / `Decline`: final. What was already received stays. The sender's outbox
  copies and the receiver's staging are released; garbage collection catches what is missed.
- Every final state is journaled once (`OneShotFinished`).
