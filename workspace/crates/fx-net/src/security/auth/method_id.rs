use std::fmt;

/// Names one way of authenticating a peer, and with it the handshake pattern and the primitives it
/// uses - `spake2-x25519-chacha20poly1305` rather than a `Password` constant.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct AuthMethodId(String);

impl AuthMethodId {
    /// Id of a method named by `value`.
    pub fn new(value: impl Into<String>) -> Self {
        Self(value.into())
    }

    /// Confirmation by the transport itself.
    pub fn transport_confirmation() -> Self {
        Self::new("transport-confirmation")
    }

    /// The id as it is written on the wire.
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for AuthMethodId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl From<&str> for AuthMethodId {
    fn from(value: &str) -> Self {
        Self::new(value)
    }
}

impl From<String> for AuthMethodId {
    fn from(value: String) -> Self {
        Self(value)
    }
}
