# mDNS connection flow

How two devices find each other and get a session, from advertising to an accepted inbound
connection. Everything below `:net`'s SPI lives in this package; everything above it is
platform-neutral and never sees an Android type.

Roles in this document: **device 1** advertises and accepts, **device 2** discovers and dials.
Device 2 is the handshake *initiator*, device 1 the *responder*.

```mermaid
sequenceDiagram
    participant A as Device 1 (advertiser)
    participant N as LAN (mDNS)
    participant B as Device 2 (initiator)

    A->>A: listen() binds a preferred port -> MulticastDnsPortBinder
    A->>N: registerService(name, TXT, port)
    N-->>B: serviceFound + resolve
    B->>B: PeerRegistry.record() -> DiscoveredPeer
    B->>A: TCP connect + HELLO
    A->>A: incoming -> user accepts
    A-->>B: HELLO_ACK / READY
    Note over A,B: both sides hold a PeerSession
```

## 1. Device 1 starts advertising

```
PeerDiscoveryImpl.startAdvertising()          :net  discovery/PeerDiscoveryImpl.kt
  advertisement()                             builds PeerAttributes: did, name, fp,
                                              pmin/pmax, dict, dictv
  Advertiser.advertise(payload)      --SPI--> MulticastDnsAdvertiser
    MulticastDnsPortBinder.value               waits for a listening port
    MulticastDnsAdvertisingService.start()     NsdManager.registerService
```

The published port comes from the transport:
`MulticastDnsTransport.TransportListener.listen()` takes the first free port of
`LanPorts.PREFERRED` - an ephemeral one only when all of them are busy - and writes `localPort`
into `MulticastDnsPortBinder`. The list is protocol, not an Android choice: see
`Connection Protocol.md` §4.6.

**Ordering matters.** Until `ConnectionManager.incoming` is collected, no listener runs, no port
is bound, and the advertiser logs a warning and publishes nothing. Collect `incoming` first, then
call `startAdvertising()`.

The mDNS instance name is the display name — NSD renames it on collision, so it is not an
identifier. The device id travels in TXT under `did`, where it stays stable.

## 2. Device 2 discovers it

```
PeerDiscoveryImpl.scan(MulticastDnsScanParams)   :net  discovery/PeerDiscoveryImpl.kt
  DiscoveryProvider.scan()              --SPI--> MulticastDnsDiscoveryProvider
    MulticastDnsDiscoveryService.start()          NsdManager.discoverServices
      onServiceFound -> resolve -> Event.Found(MulticastDnsPeer)
  Event.Appeared(DiscoveredEndpoint)   -------->  PeerRegistry.record()
```

`PeerRegistry` reads the TXT record: `did` becomes the device key, the rest becomes
`DiscoveredPeer.Advertised` (fingerprint, protocol range, dictionary). Without TXT the key would
fall back to `IP:port`, and one device on two interfaces would look like two devices.

Resolution differs by API level — `ServiceInfoCallback` on 33+, the deprecated `resolveService`
below — but both funnel into the same events. A peer that goes away is reported as
`Event.Disappeared`, keyed on `TransportEndpoint.address`.

None of this is trusted: attributes are UI hints, and the handshake is what establishes identity.

## 3. Device 2 opens the connection

```
ConnectionManagerImpl.connect(DiscoveredPeer)   :net  connection/ConnectionManagerImpl.kt
  TransportSelector.order(routes, policy)        route preference
  connect(PeerRef) -> openLink()
    TransportSelector.forEndpoint()              -> MulticastDnsTransport
    transport.open(endpoint)                     its port first, then the rest of LanPorts
    FramePump(scope, channel)                    :net  handshake/FramePump.kt
    HandshakeNegotiator.negotiate(Initiator)     HELLO -> HELLO_ACK -> READY
```

A dial sweeps the port list only while the host keeps refusing - a refusal means the device is
there and has moved ports, while a timeout means it is not on the network and the rest of the list
would only multiply the wait. The channel carries the port that answered, so the route written down
is the live one.

`FramePump` subscribes to `Transport.Channel.inbound` exactly once and buffers into an unlimited
channel. Without it the handshake and the session would each collect the same hot flow and drop
whatever arrived between the two collections.

The negotiation checks protocol version and dictionary, runs the key exchange, and calls
`PeerAuthenticator`. It yields a `SessionLink` — a `SecureChannel` plus `NegotiatedParameters`.
The pump *is* that link, so it is closed only when the handshake fails.

## 4. Device 1 accepts

```
MulticastDnsTransport  serverSocket.accept() -> InboundConnection -> listen()
ConnectionManagerImpl.incoming                  merge of every transport's listener
  UI shows peer.advertisedName / confirmationCode
  IncomingRequest.accept()
    connection.accept()                          channel over the same socket
    HandshakeNegotiator.negotiate(Responder)
    register(...)
```

Nothing is accepted on its own: an auto-accept would hand any device on the network a channel
into the app. Until `accept()` or `reject()` is called, the socket just sits there.

An inbound connection is still no route home:

- Its endpoint is `isDialable = false`. The port on an accepted socket is the peer's ephemeral
  source port, so that exact route is never dialed back. `open()` takes it anyway and throws the
  port away: only the host survives, and the ports tried are `LanPorts.PREFERRED`. A route the scan
  resolved is still the better one, since it names the port the peer actually took.
- Its session gets `relink = null`. This side never dialed, so it has nothing to redial after a
  drop; it closes and waits for the next inbound connection.

`register()` keys sessions on the `deviceId` the handshake confirmed, not on anything advertised,
and keeps one session per device however many routes led to it.

## Who knows what

| Layer | Knows about |
| --- | --- |
| `datasource/multicastdns/*` | `NsdManager`, TXT records, sockets |
| `spi/multicastdns/*` | translating that into `Transport` / `DiscoveryProvider` / `Advertiser` |
| `:net` connection + discovery | routes, handshake, session registry — no Android types |
| `PeerSessionImpl` | envelopes and dictionary messages — not even that the link was TCP |
