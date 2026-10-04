//! File access: the platform `FileSystem` seam, scanning, hashing, atomic placement,
//! at-rest crypto, placeholders, media, export.
//! Never depends on `fx-net`.

mod error;

pub use error::{Cause, FsError};
