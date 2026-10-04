use crate::NetError;
use crate::dictionary;
use crate::wire::MIN_FRAME_SIZE;
use crate::wire::binary::{ByteReader, ByteWriter};

/// What a device turned out to be. Exchanged inside the sealed channel,
/// so none of it is visible to anyone who merely reached the address.
///
/// Everything here is confirmed by the handshake that carried it; the unconfirmed counterpart a
/// scan produces is a different type so "claimed" is never mistaken for "proven". Neither the
/// device id nor the public key is here: the auth method proves both, and restating a proven fact
/// as a claim is how the two drift apart.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PeerDescriptor {
    /// Name to show the user.
    pub display_name: String,
    /// `desktop`, `laptop`, `phone`, `tablet`, `nas`; `None` when the peer did not say.
    pub kind: Option<String>,
    /// The peer's dictionary.
    pub dictionary: dictionary::Descriptor,
    /// Largest frame the peer will take. Negotiated here rather than in the public hello: before
    /// the seal each side applies its own limit, so a stranger cannot move it.
    pub max_frame_size: u32,
}

#[allow(dead_code, reason = "consumed by the handshake negotiator slice")]
impl PeerDescriptor {
    /// The `DESCRIPTOR` payload.
    pub(crate) fn encode(&self) -> Vec<u8> {
        let mut writer = ByteWriter::with_capacity(128);
        writer
            .string(&self.display_name)
            .string(self.kind.as_deref().unwrap_or(""))
            .string(&self.dictionary.id)
            .u16(self.dictionary.version)
            .u16(*self.dictionary.supported.start())
            .u16(*self.dictionary.supported.end())
            // The wire has no room above i32; a limit past 2 GiB is unbounded in practice.
            .u32(self.max_frame_size);
        writer.into_bytes()
    }

    /// Parses a `DESCRIPTOR` payload.
    pub(crate) fn decode(payload: &[u8]) -> Result<Self, NetError> {
        let mut reader = ByteReader::new(payload);
        // Read into locals: field order is the wire format, not an evaluation-order detail.
        let display_name = reader.string()?.to_owned();
        let kind = Some(reader.string()?.to_owned()).filter(|kind| !kind.trim().is_empty());
        let dictionary_id = reader.string()?.to_owned();
        let dictionary_version = reader.u16()?;
        let supported_from = reader.u16()?;
        let supported_to = reader.u16()?;
        let max_frame_size = reader.u32()?;

        // The one field here that becomes a limit on our own sends, so it is the one a peer could
        // use to make the link useless - every message would be too large for a frame.
        if max_frame_size < MIN_FRAME_SIZE {
            return Err(NetError::protocol(format!(
                "peer declared a {max_frame_size} byte frame limit, below the {MIN_FRAME_SIZE} byte minimum"
            )));
        }

        Ok(Self {
            display_name,
            kind,
            dictionary: dictionary::Descriptor {
                supported: supported_from..=supported_to,
                ..dictionary::Descriptor::new(dictionary_id, dictionary_version)
            },
            max_frame_size,
        })
    }
}
