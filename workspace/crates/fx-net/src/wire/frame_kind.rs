use crate::NetError;

/// Frame types. [`is_control`](Self::is_control) frames jump the send queue: a multi-hour
/// transfer must not sit in front of a keep-alive or a close.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[repr(u8)]
pub(crate) enum FrameKind {
    /// Public greeting of the initiator.
    Hello = 1,
    /// Public greeting of the responder, carrying the chosen version.
    HelloAck = 2,
    /// One round of the chosen auth method; the payload is opaque here.
    Auth = 3,
    /// Everything about a device that is not public; sent inside the sealed channel.
    Descriptor = 4,
    /// The handshake is complete on the sender's side.
    Ready = 5,

    /// Fire-and-forget application message.
    Message = 10,
    /// Application message that expects a [`Response`](Self::Response) or an [`Error`](Self::Error).
    Request = 11,
    /// Answer to a [`Request`](Self::Request).
    Response = 12,
    /// Failed answer to a [`Request`](Self::Request).
    Error = 13,
    /// One slice of a message that did not fit a frame; see [`Fragment`](super::Fragment).
    Chunk = 14,

    /// Keep-alive probe.
    Ping = 20,
    /// Answer to a [`Ping`](Self::Ping).
    Pong = 21,
    /// Ends the session or aborts the handshake; the payload is a UTF-8 reason.
    Close = 22,
}

impl FrameKind {
    /// The byte this kind has on the wire.
    pub(crate) fn code(self) -> u8 {
        self as u8
    }

    /// Control frames are the handshake ones (`< 10`) and the keep-alive/close ones (`>= 20`).
    pub(crate) fn is_control(self) -> bool {
        let code = self.code();
        !(10..20).contains(&code)
    }
}

impl TryFrom<u8> for FrameKind {
    type Error = NetError;

    fn try_from(code: u8) -> Result<Self, NetError> {
        Ok(match code {
            1 => Self::Hello,
            2 => Self::HelloAck,
            3 => Self::Auth,
            4 => Self::Descriptor,
            5 => Self::Ready,
            10 => Self::Message,
            11 => Self::Request,
            12 => Self::Response,
            13 => Self::Error,
            14 => Self::Chunk,
            20 => Self::Ping,
            21 => Self::Pong,
            22 => Self::Close,
            _ => return Err(NetError::protocol(format!("unknown frame kind {code}"))),
        })
    }
}
