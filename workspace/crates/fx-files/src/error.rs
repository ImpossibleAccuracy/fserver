//! Errors of the file access layer (`common/exception/FileSystemException` in the Kotlin MVP).

/// Underlying cause carried by an error, kept for logs and `source()` chains.
pub type Cause = Box<dyn std::error::Error + Send + Sync + 'static>;

/// Everything the file access layer fails with. `fx-core` passes it through to the host as is.
#[derive(Debug, thiserror::Error)]
pub enum FsError {
    /// The path is not one this file system accepts.
    #[error("Invalid path: {path}")]
    InvalidPath {
        /// The rejected path.
        path: String,
    },

    /// A directory was expected.
    #[error("Path is not directory: {path}")]
    NotDirectory {
        /// The path that is not a directory.
        path: String,
    },

    /// Something already exists where a file was to be created.
    #[error("File already exists at path: {path}")]
    AlreadyExists {
        /// The occupied path.
        path: String,
    },

    /// The file system did not create the file.
    #[error("Failed to create file at path: {path}")]
    CreationFailed {
        /// Where the file was to be created.
        path: String,
    },

    /// The file system refused the rename.
    #[error("Rename rejected: {locator} -> {new_name}")]
    RenameRejected {
        /// The file being renamed.
        locator: String,
        /// The name it was to get.
        new_name: String,
    },

    /// The file system refused the delete.
    #[error("Delete rejected: {locator}")]
    DeleteRejected {
        /// The file being deleted.
        locator: String,
    },

    /// A file sealed at rest by a method this device does not have.
    #[error("Unknown storage cipher: {cipher_id}")]
    UnknownCipher {
        /// Id of the cipher named in the file's header.
        cipher_id: String,
    },

    /// A file sealed at rest under a key this device no longer has.
    #[error("Missing storage key: {key_id}")]
    MissingKey {
        /// Id of the key named in the file's header.
        key_id: String,
    },

    /// A file sealed at rest that is damaged or was tampered with. Its bytes are never handed out.
    #[error("Corrupted sealed file: {reason}")]
    Corrupted {
        /// What was found wrong.
        reason: String,
        /// What failed underneath, if anything.
        #[source]
        source: Option<Cause>,
    },
}
