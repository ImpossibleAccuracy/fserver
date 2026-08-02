# Sessions

> What a sealed link turns into after the handshake: one session per device, its send/receive
> machinery, request correlation, keep-alive, reconnect, and how sessions are registered.
>
> Frame layout and fragmentation rules: [connection-protocol.md §5](connection-protocol.md#5-wire-format).

## 1. Model

- A **link** is one handshaken, sealed channel (`SessionLink` = secure channel + negotiated
  parameters).
- A **session** is the conversation with one device and **outlives its links**: when a dialled link
  drops, the same session object reconnects, so a held reference stays valid.
- **One session per device id**, whatever route or transport it came in on. The id is the one the
  handshake proved.

States: `Connecting → Ready(negotiated) → (Connecting → Ready)* → Closing → Closed | Failed`.

The network layer never looks inside a message: it carries the dictionary codec's bytes and correlates by
envelope ids only. The dictionary is fixed per network node.

## 2. API

| Call                         | Semantics                                                                                  |
|------------------------------|--------------------------------------------------------------------------------------------|
| `send(message)`              | fire-and-forget; succeeds once the frame(s) reached the transport, not the peer's handler   |
| `request(message, timeout?)` | sends `REQUEST`, waits for the `RESPONSE`/`ERROR` with the same correlation id (default 30 s) |
| `incoming`                   | single-consumer stream of `(message, reply?)`; `reply` present for a `REQUEST`             |
| `maxPayloadSize`             | bytes of encoded message that fit one frame (frame limit − seal overhead − 22)             |
| `close(reason)`              | sends `CLOSE` (flush bounded by 2 s), then terminates                                      |

A message larger than `maxPayloadSize` is split transparently, up to `maxAssembledMessageSize`
(16 MiB); larger is a `FrameTooLarge` failure. Senders of bulk data (file chunks) size their messages
to `maxPayloadSize` to avoid the split ([transfers](../sync/transfers.md#3-sender)).

## 3. Outbound

Two queues, one writer:

- **control queue** (`PING`, `PONG`, `CLOSE`, `ERROR` replies) - unbounded, always drained first: a
  keep-alive or a close must never wait behind a multi-hour transfer;
- **application queue** (`MESSAGE`, `REQUEST`, `RESPONSE`, `CHUNK`) - bounded (64), so a fast
  producer suspends instead of buffering a file in memory.

The writer takes a frame and waits for a link; a frame taken while the link is down waits for the
next one rather than being lost. Every application frame carries an ack that completes when the
transport accepted it. On termination, queued frames fail with `SessionClosed`.

Message ids are per-session counters; control frames take ids from the same counter.

## 4. Inbound delivery

One reader per link decodes envelopes and dispatches **without suspending** - it is the only thing
that completes pending requests and answers pings, so it must never park behind the consumer:

| Kind                     | Action                                                                  |
|--------------------------|-------------------------------------------------------------------------|
| `PING`                   | queue `PONG` (correlation = ping id)                                    |
| `PONG`                   | nothing (any inbound frame refreshes liveness)                          |
| `CLOSE`                  | terminate as `Closed(Remote(reason))`                                   |
| `CHUNK`                  | feed reassembly; dispatch the rebuilt message when complete             |
| `MESSAGE`, `REQUEST`     | decode with the dictionary, offer to `incoming`                         |
| `RESPONSE`               | decode, complete the pending request                                    |
| `ERROR`                  | fail the pending request with the payload text                          |
| handshake kinds          | ignored with a warning                                                  |

**Backpressure is refusal, not blocking.** The inbound queue is bounded (64). If it is full, the
frame is dropped; a dropped `REQUEST` is answered with `ERROR "receiver busy"`. A payload the
dictionary cannot decode is dropped; a `REQUEST` gets `ERROR "malformed payload"`. Consequence for
applications: **drain `incoming` continuously and move slow work elsewhere** - a disk write on the
consumer loses the fire-and-forget frames arriving behind it.

## 5. Liveness and reconnect

- Keep-alive (default every 30 s): send `PING`. If nothing at all arrived for **3 periods**, the
  link is closed locally, which ends its reader → link lost.
- **Link lost**: pending requests fail with `SessionLinkLost`; unfinished reassemblies are dropped.
  - an **inbound** session (this side never dialled) has nothing to dial back → `Closed(LinkLost)`;
  - a **dialled** session re-runs the full dial + handshake (`relink`) under the reconnect policy:
    exponential backoff 1 s → 30 s, 5 attempts (or static delay). Success installs the new link in
    the same session (`Ready` again); exhaustion → `Failed`.
- Messages whose fate is unknown are **never re-sent** by the session. Retrying is the
  application's decision (sync uses its own idempotent operations, see
  [sync pass](../sync/sync-pass.md#6-link-loss-during-a-pass)).

## 6. Registration

The connection registry holds at most one live session per device id (default cap 16 sessions):

- `connect(peer)` returns the existing session for that device id if there is one; otherwise dials.
- **Plain race** (two links in the same direction): the first registered stays, the spare link is
  closed.
- **Crossed dial** (both devices dialled each other at once): first-come-first-served would let each
  side keep a different link and close the other's, leaving no session. The tie is broken from ids
  both sides read the same way: **the link dialled by the lower device id wins**; the other session
  is closed with `"crossed with the link both ends keep"`.
- A session is registered only if the current config still permits its transport + auth method
  (checked again at registration, because a config reload may have happened mid-handshake).
- For dialled sessions the **handshake profile** (proven identity, negotiated parameters, the route
  re-stamped with the proven device id) is remembered and outlives the session - used for redial
  and for showing a device that was deliberately hung up on.

## 7. Live config reload

Reloading the network config swaps auth methods, offered ids, transports, policy at runtime
(the engine uses it when the user changes offered auth methods). Dictionary, identity store, crypto
provider and trust store cannot change - that needs a new node. On reload:

1. the new config is published first, so anything starting from now uses it;
2. sessions whose transport disappeared or whose auth method is no longer allowed (offered, for
   inbound; runnable, for dialled) are closed - including sessions between links, which would
   otherwise burn their reconnect budget failing;
3. listeners are stopped/started only for transports that actually changed;
4. transports dropped from the config are shut down;
5. advertisers on the air are restarted only if what would be announced changed.

## 8. Defaults

| Setting                     | Default   |
|-----------------------------|-----------|
| connect timeout             | 15 s      |
| handshake frame timeout     | 10 s      |
| request timeout             | 30 s      |
| keep-alive period           | 30 s (dead after 3) |
| max sessions                | 16        |
| send / inbound queue        | 64 / 64   |
| max reassembled message     | 16 MiB    |
| concurrent reassemblies     | 4         |
| default max frame (TCP)     | 512 KiB   |
| min frame                   | 4 KiB     |
