# Authentication and trust

> The `AUTH` phase of the handshake: which method runs, what every method must deliver, how the peer
> is proven and pinned, and the cryptography of the sealed session.
>
> Frame sequence and wire layout around this phase: [connection-protocol.md](connection-protocol.md).

## 1. Split of responsibility

| Who          | Owns                                                                                          |
|--------------|-----------------------------------------------------------------------------------------------|
| handshake    | frames, round limit, timeouts, **identity exchange**, known-peer hint, trust gate, pinning   |
| auth method  | its own cryptography: reach a shared key + transcript, optionally a code to compare          |
| host         | answering trust prompts (only it has a screen), storing pins                                 |

A method never states or verifies identities itself. That step is invisible from the other end, so a
method that skipped it would hand the trust gate a key the peer merely claimed. The handshake runs
it for every method.

## 2. Methods

### 2.1. The method interface

```
AuthMethod {
    id:                      string            // names pattern, primitives and version, e.g. "sas-1"
    strength:                AuthStrength
    requiresChannelSecurity: bool              // only valid on a transport that secures the link itself
    run(io, context) -> AuthOutcome
}

context = { role, request (initiator's credentials), prologue, confirmationCode (from the transport), local identity }

AuthOutcome {
    sharedSecret()  -> session key material   // never the same key as `aead`
    aead                                      // seals the identity exchange and hints
    transcript                                // everything the run depended on
    confirmationCode?                         // string the users compare, if the method derived one
    needVerifyKey = true                      // false only when the user vouched off-link (§5)
    verifyPeer(peer)                          // method-specific check of the proven identity
    confirm()                                 // last exchange, run after the trust gate passed
}
```

The method's I/O offers `send`, `receive`, `exchange`; sends are buffered so both ends may
`exchange` at once without deadlock. The handshake caps a method at 8 rounds (16 frames), each frame
within the auth timeout (2 min - longer than the 10 s network handshake timeout, because a person
comparing digits is not a stalled network).

The method id is an open string, not an enum: the set is extensible (ToR §3.2) and a peer may offer
one this build does not know. There is no separate cipher negotiation - the method id decides
everything, and its suffix is the method's version (`sas-1`, `sas-2`).

### 2.2. Choosing the method

- **Offered** (what this node answers to) = installed methods usable on this transport, filtered by
  the user's settings.
- **Runnable** (what this node may dial with) = installed methods usable on this transport,
  regardless of what it offers.
- Usable on a transport: if the transport declares its own security method, **only that method**;
  otherwise every method with `requiresChannelSecurity = false`.

Initiator:
- with a requested method (the user was already prompted for it): it must be runnable here and
  present in the peer's hello, else the attempt fails - never a quiet fallback;
- otherwise: the first method in the peer's list that this side also offers.

Responder: the method named in the first `AUTH` frame must be in its **own** offered list for its
**own** transport. Checked locally on both sides - a hostile peer over plain TCP naming a
transport-backed method is refused, otherwise it would skip authentication entirely.

### 2.3. Catalogue

| Id                       | Strength       | Access mode (ToR §3.2) | How it works |
|--------------------------|----------------|------------------------|--------------|
| `sas-1`                  | `UserCompared` | pairing by code comparison | X25519 ephemeral exchange with commit-reveal; both users compare a numeric short authentication string |
| `password-1`             | `SharedSecret` | password               | balanced PAKE keyed by this device's password |
| `pin-1`                  | `SharedSecret` | password (numeric)     | the same PAKE keyed by this device's PIN (separate id so the UI shows a number pad) |
| `otp-1`                  | `SharedSecret` | one-time pairing code  | the same PAKE keyed by a 6-digit code the responder shows (TTL 2 min, spent by the first attempt, right or wrong) |
| `oob-key-1`              | `UserCompared` | QR / key               | ephemeral exchange; the initiator holds the peer's key hash from an access code and `verifyPeer` checks it |
| `transport-confirmation` | `ChannelBound` | radio transports       | the transport keyed the link and produced digits; binds them + prologue; requires channel security |

**SAS (`sas-1`)**:
1. each side: ephemeral X25519 key pair + 32-byte random nonce; `reveal = bytes(pubkey) + bytes(nonce)`;
2. exchange `commit = SHA-256("sas-1:commitment" ‖ reveal ‖ prologue)`;
3. exchange reveals; verify the peer's reveal against its commitment (strict parse, exact sizes, no
   trailing bytes - one commitment admits exactly one parse);
4. `dh = X25519`; `bound = SHA-256("sas-1:bind" ‖ dh ‖ prologue)`;
5. handshake AEAD key = `HKDF-SHA256(bound, info="sas-1:handshake")`, session secret =
   `HKDF(bound, info="sas-1:session")`;
6. transcript = `prologue ‖ initKey ‖ initNonce ‖ respKey ‖ respNonce` (each as `bytes`);
7. code = `HKDF-SHA256(dh, info=transcript, len=codeLength+8) mod 10^codeLength`, zero-padded;
   code length 8, shown as two groups of four;
8. confirm: exchange sealed `"CFD"` - proves the peer's user also accepted.

**Secret-based methods (`password-1`, `pin-1`, `otp-1`)** run a **balanced PAKE**: an active attacker
gets one online guess per attempt, and a recorded transcript gives nothing to brute-force offline.
That is what lets a short PIN be safe at all. Requirements:

- one PAKE for all three, keyed by the secret, with the method id and both hello payloads bound in
  as context (the prologue);
- explicit key confirmation in both directions - a wrong secret fails the exchange, never produces
  a session with mismatched keys;
- handshake and session keys derived from the PAKE output under different HKDF infos;
- `needVerifyKey = false`: the user gave the secret to both ends, nothing is left to compare;
- the exact variant (CPace, or SPAKE2 per RFC 9382 - not an earlier incompatible draft), its group,
  hash and identity encoding are fixed in writing **in this section** before implementation and
  checked with cross-platform test vectors. Two libraries both called "SPAKE2" may not interoperate.

**Out-of-band key (`oob-key-1`)**: plain ephemeral X25519 exchange; transcript = `prologue ‖ initKey
‖ respKey`. The identity exchange (§4) makes the peer sign this run's transcript with its long-term
key, and `verifyPeer` rejects a proven key whose **full SHA-256** differs from the one in the access
code. A man in the middle can relay both legs but cannot produce that signature. Asymmetric: the
scanning side skips the prompt (`needVerifyKey = false`); the scanned side knows nothing about its
caller and goes through the trust gate as usual, where the known-peer hint keeps both prompts from
firing. Connecting through an access code always uses this method.

**Transport confirmation**: `secret = SHA-256(label ‖ code ‖ prologue)`; transcript = `prologue ‖
code`; code = the transport's digits. The transport proves the *channel*, never the *device*; the
identity exchange is what stops a peer from replaying a known public key onto an existing pin.

### 2.4. Strength and downgrade

Strength is a total order by what an attacker has to beat: `ChannelBound < SharedSecret <
UserCompared`. Persisted by name.

A connection is a **downgrade** of a pin when its method is weaker than the pinned strength, **or**
belongs to the same method family with a lower version (`sas-1` after `sas-2` was pinned).

## 3. Transcript binding

The prologue = `HELLO payload ‖ HELLO_ACK payload` **of this connection** (initiator's first). Every
method mixes it into its keys. Tampering with anything negotiated in the clear changes the keys and
the session never comes up - there is no separate comparison step to forget.

## 4. Steps after the method

Run by the handshake, in this order, on top of the method's `aead`:

1. **Identity exchange.** Each side sends, sealed:
   `SignedIdentity { bytes identity, bytes signature }` where
   `identity = string deviceId + bytes publicKey` and
   `signature = Sign_longTermKey("Proof-of-possession" ‖ u8 signerRole ‖ bytes transcript ‖ bytes identity)`
   (`signerRole`: 0 initiator, 1 responder). The receiver verifies the signature against the key in
   the identity. **The responder verifies first** and only then reveals itself - answering a stranger
   with an identity it never earned would make the handshake a device enumerator. A frame that will
   not open means the keys never agreed (wrong password, substituted peer): an authentication
   rejection, not a protocol fault.
2. **`verifyPeer`** - method-specific check of the proven identity.
3. **Known-peer hint.** One sealed byte each way: "the key you just proved is pinned here" (1) or not
   (0). A claim, never a proof: it may only ever cause **more** asking, never less.
4. **Trust gate** (§5).
5. **`confirm()`** - the method's final exchange; it waits on the auth deadline, because the other
   side's user may still be deciding.
6. Session key = `sharedSecret()`; channel sealed ([§6](#6-session-cryptography)).

Identity primitive: ECDSA P-256 + SHA-256; public key = uncompressed SEC1 point (65 bytes,
`04‖X‖Y`) - one canonical encoding, so one key cannot show two fingerprints; signature DER. Any
malformed input is an authentication rejection. The identity key is independent of the session
suite and never leaves the device's key store.

- **Fingerprint** (for people) = first 8 bytes of `SHA-256(publicKey)`, lowercase hex in groups of
  four (`9f2c 4a01 b7d3 e820`). A visual aid only.
- **Key hash** (for machines: access codes, `oob-key-1`) = full `SHA-256(publicKey)`.

## 5. Trust gate and pinning

The gate decides whether the user must be asked, and remembers the answer as a **pin**. What is
cacheable is the proven key, never a verdict.

Decision, for the peer the identity exchange proved:

| Situation                                                       | Action                                    |
|-----------------------------------------------------------------|-------------------------------------------|
| key unknown, **device id pinned under other keys**              | ask - `KeyChanged` (reinstall… or impersonation) |
| key unknown, user vouched off-link (`needVerifyKey = false`)    | do not ask                                |
| key unknown                                                     | ask - `FirstContact`                      |
| key pinned, connection is a downgrade (§2.4)                    | ask - `Downgrade`                         |
| key pinned, peer says it does not know us                       | ask - `PeerForgotUs` (reinstall, restored backup, or a copied key) |
| key pinned, same or stronger method, mutual                     | do not ask                                |

The prompt carries: proven identity, method, strength, the confirmation code (if any), the peer's
hint and the reason. A rejection fails the handshake. A node without a host authenticator is a test
rig and trusts every peer; a shipping client always has one.

**Pin commit** happens after the descriptor exchange and dictionary acceptance: `TrustRecord {
publicKey, deviceId, displayName (from the sealed descriptor), method, strength, descriptor }`,
keeping the **strongest** method ever seen for that key. A failed pin write costs a prompt next time
and never takes down a session that came up.

Rules:

- A name change under the same key is normal (the user renamed the device).
- **The same device id under another key is never silent.**
- A transport's "yes" (radio digits) is never cached - only the key it led to is.

## 6. Session cryptography

Suite `x25519-chacha20poly1305`:

- two directional keys from the session secret:
  `K_i→r = HKDF-SHA256(secret, info="fserver:initiator")`,
  `K_r→i = HKDF-SHA256(secret, info="fserver:responder")` (32 bytes, no salt);
- ChaCha20-Poly1305, 16-byte tag, **no nonce on the wire**: the 12-byte nonce is a per-direction
  frame counter (big-endian in the last 8 bytes). Safe because each direction has its own key and
  frames are never reordered or dropped before the seal; any failure to open is a protocol error
  that ends the link;
- the method's handshake AEAD and the session AEAD always come from different HKDF infos - the
  session restarts its counters at zero.

Seal overhead per frame = 16 bytes, subtracted from the frame budget. A suite that does not encrypt
exists only for tests.

## 7. Throttling

Pre-auth defenses, keyed by `(transport, host address)` - the only thing known before a peer proves
an id. They replace any per-connection user confirmation:

| Limit                                          | Default                       |
|------------------------------------------------|-------------------------------|
| attempts per source                            | 5 per 10 s (sliding window)   |
| concurrent unauthenticated handshakes (global) | 32                            |
| backoff after a rejected authentication        | 30 s, doubling, max 5 min     |

Only a rejected authentication earns backoff (not a probe, not a dropped link); a successful one
clears it. State holds across connections for the life of the process.
