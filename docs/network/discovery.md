# Discovery and transports

> How a device finds peers, makes itself findable, and which transports carry the bytes; when radios
> run, and how a device gets back to a peer it knew.
>
> What is announced and why: [connection-protocol §3](connection-protocol.md#3-disclosure-levels).

## 1. Three roles, one plug-in bundle

| Role                 | Does                                                                     | Example                          |
|----------------------|--------------------------------------------------------------------------|----------------------------------|
| discovery provider   | turns scan parameters into `Appeared` / `Disappeared` / `Failed` events | mDNS browse, subnet sweep        |
| advertiser           | puts this device on the air with a payload built by the network layer   | mDNS register, radio advertise   |
| transport            | opens outgoing channels, optionally listens for incoming ones           | TCP, radio link                  |

Finding and carrying are separate: mDNS finds, TCP carries. A plug-in contributes any subset of the
three. It receives the node-wide settings (identity, policy, crypto) from the node at build time, so
nobody can hand a plug-in a different identity than the node uses. Each plug-in has a unique id; a
duplicate is a configuration error.

### 1.1. Transport contract

- A **channel** is a duplex **frame** pipe: whole frames, in order. Stream transports length-prefix
  them. The inbound stream completes when the link goes down - that is how link loss is learned.
- An **inbound connection** is not accepted until the network layer says so; the network layer
  answers every inbound connection automatically through the public greeting
  ([connection-protocol §4.6](connection-protocol.md#46-responder-probe-vs-connection-attempt)).
- A **listener** reports where peers can reach it right now (used for access codes), re-evaluated on
  every network change.
- An **endpoint** built from an accepted socket is not dialable as is: its port is the peer's source
  port.

### 1.2. Capabilities (static, declared before any connection)

| Field          | Meaning                                                                                        |
|----------------|------------------------------------------------------------------------------------------------|
| `maxFrameSize` | largest frame carried (default 512 KiB, min 4 KiB); negotiated down per session                |
| `security`     | auth method id the transport itself backs (link already encrypted + digits), or none          |
| `greeting`     | `Wire` (greeting over the channel), `Transport` (from discovery data, a probe opens nothing), `None` |

A transport declaring `security` admits **only** that method; one that does not admits every method
that does not require channel security ([authentication §2.2](authentication.md#22-choosing-the-method)).

## 2. Peer discovery

- **Nothing starts by itself.** Scans and advertisers run only when explicitly started, one plug-in
  at a time - several need OS permissions the user may never have been asked for.
- A scan runs the provider that accepts the parameters until it ends (mDNS: until stopped; subnet
  sweep: completes) and returns what *this* run found.
- Announcements carrying this device's own id are ignored.
- **Merging**: devices are keyed by the announced device id (fallback: endpoint address). One device
  found by two providers is one entry with two routes; a route re-announced under the same address
  replaces the old one. `Disappeared` removes a route; the device goes when its last route does.
- **Advertising** builds the payload from the local identity: essential attributes (id, protocol
  range, advertisable auth methods, name if published) and optional ones (host extras). A transport
  short of room drops optional ones first. Hidden mode announces nothing. Advertising survives config
  reloads and is re-announced only if the payload changed.

Everything learned here is a claim: it selects routes and fills the UI, never a security decision.

## 3. Transports

| Plug-in id           | Discovery                               | Transport                               | Advertiser          | Greeting    | Security |
|----------------------|-----------------------------------------|-----------------------------------------|---------------------|-------------|----------|
| `multicast-dns`      | mDNS browse of `_fserver._tcp`          | TCP, listener on the fixed port list    | mDNS register, TXT = attributes | `Wire` | none |
| `direct-ip`          | -                                       | TCP dial to `host:port` (outbound only) | -                   | `Wire`      | none     |
| `subnet-scan`        | TCP connect sweep of the LAN            | - (hits are dialled via `direct-ip`)    | -                   | -           | -        |
| radio (e.g. platform nearby-device APIs) | radio discovery     | radio link                              | radio advertise     | `Transport` | `transport-confirmation` |

### 3.1. LAN rules

- **TCP framing**: `i32 length` + frame; a length outside `1..maxFrameSize` is an I/O error that ends
  the link. Outgoing frames above the limit are refused locally, never written.
- **Listener ports**: first free of 29470, 29471, 29472, else ephemeral
  ([connection-protocol §4.7](connection-protocol.md#47-listening-ports)). The mDNS record publishes
  the port the listener actually bound, so the listener runs before advertising starts.
- **Dialling** sweeps the port list on *connection refused* only.
- **mDNS**: the device id is in TXT `did`. The instance name is never an identifier (mDNS renames on
  collision). It is the display name when the name is published, otherwise a name that reveals
  nothing (e.g. a random token per advertising run). TXT entries over 255 bytes or with unusable
  keys are dropped.
- **Subnet sweep**: only physical LAN networks (Wi-Fi, Ethernet), each host's own addresses excluded,
  IPv4 subnets narrowed to the /24 around the device, IPv6 swept only over hosts already known on the
  link; 64 probes in parallel, 300 ms connect timeout. Sockets are bound to the physical network:
  through a VPN every address "answers". A hit proves only that something listens on a fixed port;
  the handshake names it.

### 3.2. Compact announcement encoding

For transports that publish only a small endpoint record before a connection exists (≤ 131 bytes):

```
'F' (0x46) | version u8 (=1) | records: tag u8 | len u8 | value
```

| Tag    | Value                                                     |
|--------|-----------------------------------------------------------|
| `0x01` | device id as a 16-byte UUID                               |
| `0x02` | device id as UTF-8 text (when not a UUID)                 |
| `0x05` | display name, ≤ 32 bytes UTF-8 (only if published)        |
| `0x06` | protocol min (u8)                                         |
| `0x07` | protocol max (u8)                                         |
| `0x7F` | `key=value` UTF-8 text record, while room lasts           |

Identity goes first so a full budget drops the extras, never the id. Unknown tags are skipped by
length. Tags `0x03`, `0x04` (key fingerprint) and `0x08` (auth list) are retired and never reused: a
stable key digest on the air lets a passive listener track a device, and a self-securing transport
fixes its method itself. A record that does not start with the magic is read as a plain name.

A radio transport's endpoint ids are per advertising session, so its routes are never re-dialled
from storage; the device is found again by discovery.

## 4. Presence policy

One component owns "is this device on the air / scanning" for the whole process:

- Callers take a **handover** and state what *they* want (advertise yes/no, which methods to scan).
  What runs is the **union** of open handovers; closing one withdraws only its own ask.
- It **reconciles** continuously against: the union, what is actually running, the current network,
  and precondition re-checks (on demand and every 30 s - OSes do not announce a granted permission or
  a radio switched on):
  - a method whose preconditions are unmet is not started (and is stopped if running);
  - a network change takes every advertiser and scan down and restarts them (an announcement made on
    the old network names an unreachable address);
  - a method that died is started again.
- Recommended background policy: scan with mDNS only; radios and sweeps only while a user looks at
  the result.

Transport kinds as the host sees them: radio (automatic, can advertise), mDNS (automatic, can
advertise), subnet sweep, manual address.

## 5. Reaching a device

A locator names what the user has: a discovered device id, an address (+ optional port, default
29470), an access code (QR), a radio endpoint, or a known (trusted) device id.

**Known device** (sync and one-shot always dial this way):

1. an open session for the id → use it;
2. a discovered route → connect over it (routes in policy order: radio, mDNS, direct
   address);
3. the handshake profile from earlier in this run → its route;
4. the **last known route** from the trust store (one per transport).

Fall through to the next candidate only when transport could not carry the attempt. **A peer that
answered and refused stays refused** - asking again over another route would make its user refuse
twice. An identity mismatch is not a refusal (someone else holds the route; the real device may be
elsewhere).

**Identity check**: when the caller named a device, the session's proven device id must match;
otherwise the session is closed and the attempt fails with an identity mismatch. Everything that named
the device before the handshake was unauthenticated.

**Route memory**: a route is recorded only for a session that came up, against the proven id, from
the session's own endpoint (inbound routes are kept as not dialable - the host is still worth a port
sweep). Never from a probe: a recorded probe route would let anyone announcing a trusted device's id
replace its address.

**Reachability**: failed dials to a named device are recorded as a run (`since`, `attempts`, reason
`NoRoute | Unreachable | Refused | NotAllowed | Failed`); any session with it clears the run. An
unreachable device is shown as a fault only after ≥ 2 attempts and ≥ 1 day since it was last seen.

**Access code (QR)**: JSON

```
{ "deviceId": "...", "name": "...", "keyHash": "<hex SHA-256 of the identity key>",
  "directIp": { "host": "...", "port": 29470 }, "radio": { "endpointId": "..." } }
```

built from the listener's dialable endpoints; none while nothing listens. A code is dialled by device
id if that device is visible, else by its address, else by its radio endpoint - always with
`oob-key-1` and the code's key hash ([authentication §2.3](authentication.md#23-catalogue)).

## 6. Incoming connections

Requests that passed the greeting and started `AUTH` reach the host as incoming connections
(advertised name - unverified, transport, whether the transport produced a code). The host accepts
or rejects; the auth method and the trust gate still run after an accept.

**Auto-accept** (opt-in): a request is accepted without asking the host when its claimed device -
the announced id for its route, or the trusted device last reached from its address - is trusted and
has a non-disabled source with this device. Every other request is still put in front of the host.
Auto-accept decides only whether to *ask*: authentication and the trust gate run regardless, so a
wrong guess costs a prompt, never access.
