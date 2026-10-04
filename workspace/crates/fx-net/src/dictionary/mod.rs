//! The one place where a user of `fx-net` says what devices talk *about*

use std::collections::BTreeMap;
use std::ops::RangeInclusive;

use crate::NetError;

/// Turns messages into bytes and back. The only code in the process that knows the wire shape.
pub trait MessageCodec<T>: Send + Sync {
    /// The message as it travels as an envelope payload.
    fn encode(&self, message: &T) -> Vec<u8>;

    /// Parses a payload; malformed input is a [`NetError::Protocol`].
    fn decode(&self, bytes: &[u8]) -> Result<T, NetError>;
}

/// What a users of `fx-net` talks about.
pub trait MessageDictionary<M>: Send + Sync {
    /// What this side speaks.
    fn descriptor(&self) -> &Descriptor;

    /// Codec of this dictionary's messages.
    fn codec(&self) -> &dyn MessageCodec<M>;

    /// Called once per handshake, with what the other side announced.
    fn negotiate(&self, remote: &Descriptor) -> Decision;
}

/// Identity and version range of a dictionary.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Descriptor {
    /// Dictionary name.
    pub id: String,
    /// What this side speaks now.
    pub version: u16,
    /// What it can still serve, for older peers.
    pub supported: RangeInclusive<u16>,
    /// Local annotations; not sent over the wire.
    pub extras: BTreeMap<String, String>,
}

impl Descriptor {
    /// Descriptor that speaks only `version`.
    pub fn new(id: impl Into<String>, version: u16) -> Self {
        Self {
            id: id.into(),
            version,
            supported: version..=version,
            extras: BTreeMap::new(),
        }
    }

    /// Also serves the older versions in `supported`.
    pub fn serving(mut self, supported: RangeInclusive<u16>) -> Self {
        self.supported = supported;
        self
    }
}

/// The dictionary's verdict on a remote [`Descriptor`].
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Decision {
    /// Talk, at this dictionary version.
    Accept {
        /// Version both sides will use.
        effective_version: u16,
    },
    /// Close the handshake with `dictionary rejected: <reason>`.
    Reject {
        /// Why the dictionaries are incompatible.
        reason: String,
    },
}
