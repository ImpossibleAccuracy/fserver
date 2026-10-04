//! What is known about a peer at each stage of the handshake.

mod descriptor;
mod greeting;

pub use descriptor::PeerDescriptor;
pub use greeting::PublicGreeting;
