//! The header written around every payload.

use std::fmt;

use super::binary::{ByteReader, ByteWriter};
use super::{FrameKind, version};
use crate::NetError;

/// The header `fx-net` writes around every payload. Routing, correlation and queueing read this
/// and nothing else - which is exactly what keeps the dictionary opaque.
#[derive(Clone, PartialEq, Eq)]
pub(crate) struct Envelope {
    /// Wire protocol version, one byte on the wire.
    pub version: u8,
    /// What the payload is.
    pub kind: FrameKind,
    /// Id of this message.
    pub message_id: i32,
    /// Id of the message being answered;
    /// [`NO_CORRELATION`](Self::NO_CORRELATION) when this frame answers nothing.
    pub correlation_id: i32,
    /// Opaque to the envelope.
    pub payload: Vec<u8>,
}

impl Envelope {
    /// `correlation_id` of a frame that answers nothing.
    pub const NO_CORRELATION: i32 = 0;

    /// version + kind + message id + correlation id + payload length prefix.
    pub const HEADER_SIZE: usize = 1 + 1 + 4 + 4 + 4;

    /// Current-version envelope with no correlation and an empty payload.
    pub fn new(kind: FrameKind, message_id: i32) -> Self {
        Self {
            version: version::CURRENT,
            kind,
            message_id,
            correlation_id: Self::NO_CORRELATION,
            payload: Vec::new(),
        }
    }

    /// Marks this envelope as the answer to `correlation_id`.
    pub fn answering(mut self, correlation_id: i32) -> Self {
        self.correlation_id = correlation_id;
        self
    }

    /// Replaces the payload.
    pub fn with_payload(mut self, payload: impl Into<Vec<u8>>) -> Self {
        self.payload = payload.into();
        self
    }

    /// The frame as it goes on the wire.
    pub fn encode(&self) -> Vec<u8> {
        let mut writer = ByteWriter::with_capacity(Self::HEADER_SIZE + self.payload.len());
        writer
            .u8(self.version)
            .u8(self.kind.code())
            .i32(self.message_id)
            .i32(self.correlation_id)
            .bytes(&self.payload);
        writer.into_bytes()
    }

    /// Parses one frame. Bytes after the payload are ignored.
    pub fn decode(frame: &[u8]) -> Result<Self, NetError> {
        let mut reader = ByteReader::new(frame);
        Ok(Self {
            version: reader.u8()?,
            kind: FrameKind::try_from(reader.u8()?)?,
            message_id: reader.i32()?,
            correlation_id: reader.i32()?,
            payload: reader.bytes()?.to_vec(),
        })
    }
}

/// The payload can be a secret or a file chunk, so only its size is shown.
impl fmt::Debug for Envelope {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "Envelope(v={}, {:?}, id={}, corr={}, {}b)",
            self.version,
            self.kind,
            self.message_id,
            self.correlation_id,
            self.payload.len()
        )
    }
}
