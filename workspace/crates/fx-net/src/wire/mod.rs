//! Wire format of `fx-net`.

pub mod binary;

pub(crate) mod envelope;
pub(crate) mod fragment;
pub(crate) mod frame_kind;
pub(crate) mod version;
pub(crate) use {envelope::Envelope, fragment::Fragment, frame_kind::FrameKind};

/// Smallest frame limit a link may have; a peer declaring less is refused.
/// Below this almost no message fits a frame.
pub const MIN_FRAME_SIZE: u32 = 4 * 1024;
