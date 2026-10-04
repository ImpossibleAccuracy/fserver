//! One slice of a message too large for a single frame.

use std::fmt;

use super::FrameKind;
use super::binary::{ByteReader, ByteWriter};
use crate::NetError;

/// One slice of a message too large for a single frame.
///
/// The slice travels as the payload of a [`FrameKind::Chunk`] envelope that keeps the message id
/// and correlation id of the message being split, so slices of two messages can interleave and
/// still be told apart. `kind` is what the message is rebuilt as.
#[derive(Clone, PartialEq, Eq)]
pub(crate) struct Fragment {
    /// Kind the reassembled message becomes.
    pub kind: FrameKind,
    /// 0-based position of this slice.
    pub index: i32,
    /// How many slices the message has.
    pub total: i32,
    /// This slice's bytes.
    pub part: Vec<u8>,
}

impl Fragment {
    /// What a slice costs on top of its bytes: kind, index, total.
    pub const HEADER_SIZE: usize = 1 + 4 + 4;

    /// The `CHUNK` payload.
    pub fn encode(&self) -> Vec<u8> {
        let mut writer = ByteWriter::with_capacity(Self::HEADER_SIZE + self.part.len());
        writer
            .u8(self.kind.code())
            .i32(self.index)
            .i32(self.total)
            // Last field, so it needs no length of its own - the frame already carries one.
            .raw(&self.part);
        writer.into_bytes()
    }

    /// Parses a `CHUNK` payload.
    pub fn decode(payload: &[u8]) -> Result<Self, NetError> {
        let mut reader = ByteReader::new(payload);
        Ok(Self {
            kind: FrameKind::try_from(reader.u8()?)?,
            index: reader.i32()?,
            total: reader.i32()?,
            part: reader.rest().to_vec(),
        })
    }
}

/// A slice can be file content, so only its size is shown.
impl fmt::Debug for Fragment {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "Fragment({:?}, {}/{}, {}b)",
            self.kind,
            self.index,
            self.total,
            self.part.len()
        )
    }
}
