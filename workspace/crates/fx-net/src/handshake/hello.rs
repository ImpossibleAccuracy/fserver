use crate::NetError;
use crate::security::auth::AuthMethodId;
use crate::wire::binary::{ByteReader, ByteWriter};

/// The public half of the handshake, and the only part that goes out in the clear.
/// Sent as `HELLO` by the initiator and `HELLO_ACK` by the
/// responder, which sets both versions to the one it chose.
///
/// Three fields, and adding a fourth is a change to the threat model rather than a new feature:
/// anything that says *which* device this is travels later, inside the auth method or the sealed
/// channel. `methods` is a claim, not an instruction - each side checks a choice against what its
/// own transport allows before running it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct PublicHello {
    /// Oldest wire protocol version the sender speaks.
    pub min_version: u16,
    /// Newest wire protocol version the sender speaks.
    pub max_version: u16,
    /// Auth methods the sender is willing to run.
    pub methods: Vec<AuthMethodId>,
}

impl PublicHello {
    /// A peer that offers more than this is not negotiating, it is probing the parser.
    pub const MAX_METHODS: usize = 255;

    /// The `HELLO` / `HELLO_ACK` payload.
    pub fn encode(&self) -> Vec<u8> {
        let mut writer = ByteWriter::with_capacity(64);
        writer
            .u16(self.min_version)
            .u16(self.max_version)
            // The receiver refuses more than MAX_METHODS, so a count this large is refused anyway.
            .u8(u8::try_from(self.methods.len()).unwrap_or(u8::MAX));

        for method in &self.methods {
            writer.string(method.as_str());
        }
        writer.into_bytes()
    }

    /// Parses a `HELLO` / `HELLO_ACK` payload.
    pub fn decode(payload: &[u8]) -> Result<Self, NetError> {
        let mut reader = ByteReader::new(payload);
        let min_version = reader.u16()?;
        let max_version = reader.u16()?;

        let declared = reader.u8()?;
        let count = usize::try_from(declared)
            .ok()
            .filter(|&count| count <= Self::MAX_METHODS)
            .ok_or_else(|| NetError::protocol(format!("hello declares {declared} auth methods")))?;

        let methods = (0..count)
            .map(|_| reader.string().map(AuthMethodId::from))
            .collect::<Result<_, _>>()?;

        Ok(Self {
            min_version,
            max_version,
            methods,
        })
    }
}
