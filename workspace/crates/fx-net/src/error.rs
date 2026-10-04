//! Errors of the network layer (`common/exception/NetworkException` in the Kotlin MVP).

use std::time::Duration;

/// Underlying cause carried by an error, kept for logs and `source()` chains.
pub type Cause = Box<dyn std::error::Error + Send + Sync + 'static>;

/// Everything the network layer fails with.
#[derive(Debug, thiserror::Error)]
pub enum NetError {
    /// The bytes on the wire were not what the protocol says they should be.
    #[error("{reason}")]
    Protocol {
        /// What was wrong with the bytes.
        reason: String,
        /// What failed underneath, if anything.
        #[source]
        source: Option<Cause>,
    },

    /// No transport in the node can reach the endpoint that was asked for.
    #[error("{0}")]
    NoRoute(String),

    /// The transport refused, timed out, or died while opening.
    #[error("{reason}")]
    Transport {
        /// What the transport was doing.
        reason: String,
        /// What failed underneath, if anything.
        #[source]
        source: Option<Cause>,
    },

    /// The handshake did not complete: versions, identity, or the dictionary.
    /// Use [`NetError::is_handshake`] to also match [`NetError::AuthenticationRejected`].
    #[error("{reason}")]
    Handshake {
        /// Why the handshake stopped.
        reason: String,
        /// What failed underneath, if anything.
        #[source]
        source: Option<Cause>,
    },

    /// The host's authenticator refused this peer. A kind of handshake failure.
    #[error("peer rejected: {reason}")]
    AuthenticationRejected {
        /// Why the peer was refused.
        reason: String,
        /// What failed underneath, if anything.
        #[source]
        source: Option<Cause>,
    },

    /// The session is gone; open a new one instead of retrying on this object.
    #[error("session is closed")]
    SessionClosed,

    /// The link dropped. Pending requests fail with this - `fx-net` never silently re-sends them.
    #[error("session link lost")]
    SessionLinkLost(#[source] Option<Cause>),

    /// No answer in time. Whether to ask again is the dictionary's decision, not the network's.
    #[error("no response within {0:?}")]
    RequestTimeout(Duration),

    /// The message is past what this link will carry at all.
    ///
    /// Not "does not fit a frame" - a message over the frame size is split and sent in pieces.
    /// This is the ceiling on the whole message.
    #[error("message of {size} bytes exceeds the {limit} byte payload limit of this link")]
    FrameTooLarge {
        /// Size of the message.
        size: usize,
        /// Largest payload this link carries.
        limit: usize,
    },
}

impl NetError {
    /// [`NetError::Protocol`] without an underlying cause.
    pub fn protocol(reason: impl Into<String>) -> Self {
        Self::Protocol {
            reason: reason.into(),
            source: None,
        }
    }

    /// [`NetError::Handshake`] without an underlying cause.
    pub fn handshake(reason: impl Into<String>) -> Self {
        Self::Handshake {
            reason: reason.into(),
            source: None,
        }
    }

    /// Whether the handshake failed, refusal by the authenticator included
    /// (Kotlin `is NetworkException.Handshake`).
    pub fn is_handshake(&self) -> bool {
        matches!(
            self,
            Self::Handshake { .. } | Self::AuthenticationRejected { .. }
        )
    }
}
