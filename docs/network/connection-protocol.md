# Connection protocol

### Discovery data, public greeting, handshake phases, wire format

> Version 2 (draft). Cross-platform: the server and every client follow it, so a change here is
> never a single-platform decision.
>
> Builds on ToR §3.2 (access and trust), §3.3 (transport), §4.4 (protocol versioning).
> What happens *inside* the `AUTH` phase is [authentication.md](authentication.md); what happens on
> the sealed link afterwards is [sessions.md](sessions.md).

---

## 1. Goal

Split everything two devices exchange into two disclosure levels:

- **Public level** - answers anyone, with no user involvement. Carries the minimum needed to know
  *how* to open a secure connection: the protocol version range and the list of auth methods.
- **Private level** - everything else (device name, kind, message dictionary, frame limit). Only
  after mutual authentication, inside the encrypted channel.

A greeting therefore never needs a user's confirmation, and nothing identifying leaves in clear
frames.

## 2. Threat model

### 2.1. Defended against

| Adversary                              | Capability                                             | What the split gives                              |
|----------------------------------------|--------------------------------------------------------|---------------------------------------------------|
| Port scanner / anyone reachable on WAN | dials the address, never saw a LAN announcement        | learns no name, kind or id                        |
| Device in hidden mode (QR access)      | -                                                      | does not announce at all; the greeting reveals the minimum |
| Active MITM on the link                | replaces and injects frames                            | breaks authentication (§6), no session comes up  |

### 2.2. Deliberately not defended against

- **A passive observer on the same LAN.** It sees discovery announcements, which carry the device
  name by construction (§3). Hiding the name from the greeting does not help against it and must
  not be presented as protection.
- **Correlating an announcement with a greeting.** They share a route (`host:port`); whoever saw the
  announcement can match them.

### 2.3. The asymmetry everything rests on

An announcement is a **voluntary broadcast**: the device decides what and when to publish, and can
go silent. A greeting is a **forced answer** to anyone who dialled, including someone who found the
device by sweeping ports. So an announcement may carry data the greeting must not.

## 3. Disclosure levels

| Data                         | Announcement | Public greeting | Sealed descriptor | Proven by auth |
|------------------------------|:------------:|:---------------:|:-----------------:|:--------------:|
| protocol version range       | ✓            | ✓               | -                 | -              |
| auth method list             | ✓*           | ✓               | -                 | -              |
| device id                    | ✓            | ✗               | ✗                 | ✓              |
| display name                 | ✓ (optional) | ✗               | ✓                 | -              |
| device kind                  | ✗            | ✗               | ✓                 | -              |
| message dictionary           | ✗            | ✗               | ✓                 | -              |
| max frame size               | ✗            | ✗               | ✓                 | -              |
| long-term public key / fingerprint | ✗      | ✗               | ✗                 | ✓              |

\* Only where the greeting travels over the wire (§8.2): a transport that fixes its own method does
not broadcast the list, the dialling side reads it off its own transport declaration.

Rules:

1. **An announcement carries nothing more private than the name.** Whether the name is published is
   a setting (`AdvertisementPolicy.publishName`), not a constant.
2. **The public greeting carries the minimum and nothing else.** Adding a field there is a change to
   the threat model, not a feature.
3. **A device in hidden mode does not announce at all** (`AdvertisementPolicy.enabled = false`).
   That switch is the whole protection of the name against a passive observer.
4. **When an announcement and the sealed descriptor disagree, the descriptor wins.** An announcement
   is a claim; the descriptor arrived over an authenticated link.
5. **No key fingerprint on the air, ever.** A stable digest broadcast continuously is exactly what
   lets a passive observer follow a device from network to network; a name does not, the user
   changes it.

Announced and confirmed data are **different types** (`AdvertisedPeer` vs `PeerDescriptor`), never
one type with optional fields - otherwise nothing can tell whether a field was proven.

### 3.1. Announcement attributes

Key/value pairs (an mDNS TXT record, or a compact encoding on size-limited transports -
[discovery §3.2](discovery.md#32-compact-announcement-encoding)):

| Key    | Value                                        | Budget class |
|--------|----------------------------------------------|--------------|
| `did`  | device id                                    | essential    |
| `pmin` | lowest supported protocol version            | essential    |
| `pmax` | highest supported protocol version           | essential    |
| `auth` | comma-separated auth method ids (advertisable ones, §8.2) | essential |
| `name` | display name, if `publishName`               | essential    |
| others | host-supplied extras                         | optional (dropped first when a transport has no room) |

A transport whose attributes lack `did` produces a peer keyed by its address - nothing breaks, it
is simply known less well.

## 4. Flow

### 4.1. Discovery

The user sees a list built from announcements: name and route, **all unverified**. Discovery is
specified in [discovery.md](discovery.md).

### 4.2. Probe (greeting only)

The user picks a device. The client opens a connection, exchanges the public greeting and **closes
the connection**. The probe answers two questions: is the device still there, and which methods it
takes. The responder needs no user confirmation, creates no session and remembers nothing.

**The probe result is advisory. It is not a security input and is allowed to lie.** It only picks
the right prompt: password field, PIN pad, or code comparison.

### 4.3. Connect

The user enters what is required and the client opens a **new** connection:

```
initiator                                            responder
  ─ HELLO      {min,max versions, methods} ────────▶
  ◀──────────── HELLO_ACK {chosen version, methods} ─      both hellos → auth prologue
  ─ AUTH #1    {method id, method payload} ────────▶      responder checks the method locally,
                                                          parks the request for its host (§4.6)
  ⇄ AUTH × N   method rounds (opaque here)
  ⇄ AUTH       identity exchange, known-peer hint, method confirmation
  ═══════════════════ channel sealed ════════════════════
  ⇄ DESCRIPTOR (both send, either order)
  ─ READY ─────────────────────────────────────────▶
                         session is up
```

The repeated greeting is not a re-check of stale data: its bytes **are cryptographic material** -
the hellos of this connection are the prologue every auth method binds into its keys
([authentication §3](authentication.md#3-transcript-binding)). Hence `connect` neither takes the
probe result nor needs it.

### 4.4. Why two connections

| Option                                       | Problem                                                                                     |
|----------------------------------------------|---------------------------------------------------------------------------------------------|
| One connection for everything                | stays open while the user types a password - minutes of pre-auth resources per dialler     |
| Two phases passing context across connections | the responder must remember which greeting it sent to whom; no stable key (ports, NAT), an attacker-controlled table, expiry policy |
| **Greeting on its own connection; `connect` repeats it** | no state between connections; transcript binding for free; costs one extra exchange |

The third option removes the class of attacks "substitute phase 1, skip phase 2": nothing travels
between the phases, so nothing can be substituted. A lie in the probe is caught on `connect`.

### 4.5. Greeting and connection disagree

The probe said "password", the connection says "only SAS" - abort and ask again. **Never continue
on what the probe said.** When the caller names a method (`AuthRequest.method`), a peer that no
longer offers it ends the attempt instead of quietly running something weaker
([authentication §2.2](authentication.md#22-choosing-the-method)).

### 4.6. Responder: probe vs connection attempt

The responder answers every inbound connection and runs the public half automatically. It then
waits for the first `AUTH` frame. **Only a peer that sends it becomes an incoming request** shown to
the host; a peer that only wanted the greeting hangs up first and never reaches the user. An
incoming request not answered within the auth timeout (2 min) is rejected.

### 4.7. Listening ports

A LAN listener takes **the first free port of a fixed list**, in order:

| Order    | Port            |
|:--------:|-----------------|
| 1        | 29470           |
| 2        | 29471           |
| 3        | 29472           |
| fallback | ephemeral (0)   |

The list is the same for all clients and the server. Why fixed:

1. **A recorded route stays valid.** With an ephemeral port every restart moves the listener and
   every stored `host:port` goes stale.
2. **An address without a port is dialable.** A typed address and a QR code without a port go to the
   first port of the list.
3. **An inbound route can be guessed.** On an accepted socket the remote port is the peer's source
   port, never dialable; only the host is known, and the list is what gets added to it.

Dialling rules:

- a route with a port: its own port first, then the rest of the list;
- a route without a usable port (inbound, typed without port): the whole list in order;
- the sweep continues **only on connection refused** (host present, no listener on that port). A
  timeout or unreachable host means the device is not on the network - sweeping further only
  multiplies the wait.

The threat model is unchanged: §2.3 already treats port sweeping as an adversary capability.

## 5. Wire format

All integers big-endian. `bytes` = `i32 length` + raw; `string` = UTF-8 as `bytes`.

### 5.1. Frame

A transport delivers whole frames in order (a stream transport length-prefixes them itself: TCP uses
`i32 length` + frame). Every frame is one envelope:

```
Envelope {
    version:       u8     // wire protocol version (1)
    kind:          u8     // FrameKind
    messageId:     i64
    correlationId: i64    // id of the message answered; 0 = answers nothing
    payload:       bytes
}                         // header = 22 bytes
```

After the seal, the whole encoded envelope is the plaintext of one AEAD frame
([authentication §6](authentication.md#6-session-cryptography)).

### 5.2. Frame kinds

| Code | Kind         | Phase                 | Control* |
|-----:|--------------|-----------------------|:--------:|
| 1    | `HELLO`      | public, clear         | ✓        |
| 2    | `HELLO_ACK`  | public, clear         | ✓        |
| 3    | `AUTH`       | auth, opaque payload  | ✓        |
| 4    | `DESCRIPTOR` | sealed                | ✓        |
| 5    | `READY`      | sealed                | ✓        |
| 10   | `MESSAGE`    | sealed, fire-and-forget | -      |
| 11   | `REQUEST`    | sealed                | -        |
| 12   | `RESPONSE`   | sealed                | -        |
| 13   | `ERROR`      | sealed, answers a REQUEST | -    |
| 14   | `CHUNK`      | sealed, fragment (§5.6) | -      |
| 20   | `PING`       | sealed                | ✓        |
| 21   | `PONG`       | sealed                | ✓        |
| 22   | `CLOSE`      | any; payload = UTF-8 reason | ✓  |

\* Control frames jump the send queue ([sessions §3](sessions.md#3-outbound)).

An unknown kind is a protocol error. During the handshake any `CLOSE` aborts it with the peer's
reason; any other unexpected kind is a handshake failure. Each side sends `CLOSE` with a reason
before failing, so the other side does not see a silent drop.

### 5.3. Public greeting (`HELLO` / `HELLO_ACK` payload)

```
PublicHello {
    minVersion:  i32
    maxVersion:  i32      // in HELLO_ACK both equal the chosen version
    methodCount: i32      // 0..32, more is a protocol error
    methods:     string × methodCount
}
```

Version choice (responder): `v = min(peer.max, local.max)`; fail unless `v >= max(peer.min,
local.min)`. The initiator then checks the chosen `v` is in its own range. The only version so far
is `1`.

What is deliberately **not** here: the frame size limit (before the seal each side applies its own
local limit - letting a stranger move an allocation limit is not negotiation), a cipher list (folded
into the method id), a nonce (nothing needs binding across connections), any identity.

### 5.4. First `AUTH` frame

The initiator chooses the method and names it in its first `AUTH` frame only:

```
AUTH#1.payload = string methodId + bytes methodPayload
AUTH#n.payload = methodPayload            (n > 1, and every responder frame)
```

### 5.5. Descriptor (`DESCRIPTOR` payload, sealed)

```
PeerDescriptor {
    displayName:     string
    kind:            string     // "" = unknown; desktop | laptop | phone | tablet | nas
    dictionaryId:    string
    dictionaryVer:   i32        // what the sender speaks now
    supportedFrom:   i32        // oldest dictionary version it still serves
    supportedTo:     i32
    maxFrameSize:    i32        // largest frame it accepts; < 4096 is a protocol error
}
```

The effective frame limit is `min(local transport limit, peer maxFrameSize)`. The lower bound
exists because this is the one descriptor field that limits *our* sends - a peer announcing a tiny
frame would make the link useless.

Neither the device id nor the public key is in the descriptor: both are proven by the auth phase,
and restating a proven fact as a claim is how the two drift apart.

The dictionary verdict is the application's (`MessageDictionary.negotiate`); a rejection closes the
handshake with `dictionary rejected: <reason>`. The trust pin is committed only after `READY`, i.e.
after the dictionary was accepted.

### 5.6. Fragmentation

An application message need not fit a frame (file chunks, full indexes). **The session** splits and
reassembles; application code hands over whole messages and never sees frames. A frame never exceeds
the negotiated `maxFrameSize` - the receiver rejects an oversized frame and that costs the whole
session, not one message.

A slice travels as a `CHUNK` envelope with the message id and correlation id **of the split
message**, so slices of two messages may interleave and a reassembled `REQUEST` still matches its
`RESPONSE`:

```
Fragment {
    kind:  u8     // MESSAGE | REQUEST | RESPONSE - what the reassembled message becomes
    index: i32    // 0-based
    total: i32
    part:  rest of the payload (no own length)
}
```

Payload budget of one frame: `maxFrameSize - sealOverhead - 22 (envelope)`; slice size additionally
minus 9 (fragment header).

Receiver rules:

- `kind` must be `MESSAGE`, `REQUEST` or `RESPONSE`; control and handshake frames are never split.
- `0 <= index < total`, `total >= 1`; a repeated index is ignored.
- All slices under one id must agree on `kind` and `total`; disagreement drops the assembly.
- Reassembled size is capped (default 16 MiB) and so is the number of concurrent assemblies
  (default 4). On overflow the **oldest** assembly is dropped - one whose rest never comes would
  otherwise hold its slot for the life of the session.
- Unfinished assemblies are dropped when the link is lost or replaced.

`CHUNK` is part of the protocol, not a negotiated extension: an implementation must reassemble.

## 6. Invariants

A violation of any of these is a security defect, not a style remark.

1. **The public level carries no private data.** Adding a field to the greeting requires revisiting
   §2.
2. **The network layer trusts its plug-ins and never the peer.** A plug-in is code built into this process; the peer is
   bytes from the network.
3. **Nothing received before authentication affects a security decision.** The probe result is for
   display only.
4. **Negotiated parameters are part of the auth transcript** (the hellos are the prologue).
5. **A downgrade of the auth method below the pinned one is never silent**
   ([authentication §5](authentication.md#5-trust-gate-and-pinning)).
6. **Before the seal, frame limits are local, not negotiated.**
7. **Failures are rate-limited on the receiving side** ([authentication §7](authentication.md#7-throttling)).
8. **Client-side checks are UX only** (ToR §1.2). The receiving side decides.
9. **Reassembly is bounded by the receiver** (§5.6) - the peer chooses slice counts and sizes.

## 7. Versioning

Changing the greeting breaks the version negotiation point itself, because negotiation lives in it.
Compatibility with an old greeting layout is not emulated: the minimum supported version is raised
and the old one is cut off. Acceptable while the project is early; not after the first public
release.

The wire protocol version (envelope + handshake) and the dictionary version (application messages)
are independent.

## 8. Special cases

### 8.1. Transports without an anonymous phase

Some radio transports cannot exchange bytes before the connection is accepted, but can publish a
small endpoint record **before** acceptance. A transport declares where its greeting comes from:
`Wire`, `Transport` or `None`. With `Transport` a probe opens nothing and is answered from discovery
data: the protocol versions come from the announcement, the method from the transport's own
declaration - never from the broadcast. Such a record is a broadcast (same content for everyone, tight
size limit), so it can never be more private than an announcement.

### 8.2. Advertisable methods

A transport-backed method (e.g. `transport-confirmation`) is announced only if some installed
transport backs it - promising it over a transport that cannot honor it is a false claim.

### 8.3. Pairing over a self-securing transport

The identity exchange runs on every transport, so a device paired over a radio transport has its
proven identity key pinned and is recognized on any other transport. Only the transport's code
comparison is never cached.

### 8.4. UI cost

Before authentication only announced data is known. A device list shows unverified names and must
say so - visually, or through a "confirmed" state after the first pairing.

### 8.5. Dictionary negotiation

The application decides dictionary compatibility from the sealed descriptors: a **different
dictionary id is rejected**; otherwise the effective version is the highest version both sides
support (`min(local.version, remote.version)`, which must lie in both supported ranges), else
rejected.

### 8.6. Addresses

IPv4 and IPv6 are both first-class: listeners bind both families, routes and access codes carry
either, IPv6 hosts are written as `[host]:port`.
