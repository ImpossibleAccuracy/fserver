//! Network layer: transports, TLS, discovery, pairing, authentication, sessions,
//! streaming transfer.
//! Knows nothing about sync and never depends on `fx-files`.

pub mod dictionary;
mod error;
pub(crate) mod handshake;
pub mod peer;
pub mod security;
pub mod wire;

pub use error::{Cause, NetError};
