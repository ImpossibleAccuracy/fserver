//! Errors of the engine (`common/exception/{Sync,Transfer,DetectionFailed}Exception` in the
//! Kotlin MVP). Network and file errors stay in their crates and are wrapped here.

use fx_files::FsError;
use fx_net::{Cause, NetError};

/// Engine error.
#[derive(Debug, thiserror::Error)]
pub enum CoreError {
    /// The async runtime could not be started.
    #[error("runtime: {0}")]
    Runtime(#[from] std::io::Error),
}

/// Anything a sync pass fails with on purpose. Match a variant to act on why.
#[derive(Debug, thiserror::Error)]
pub enum SyncError {
    /// A source with this id is already registered.
    #[error(
        "Source already registered with id: {source_id} for location: {location} and mode: {mode}"
    )]
    DuplicateSource {
        /// Id of the source.
        source_id: String,
        /// Where the source lives.
        location: String,
        /// The source's mode.
        mode: String,
    },

    /// At least one action in the pass failed; `causes` holds every individual failure.
    #[error("{reason}")]
    ActionFailed {
        /// What the pass was doing.
        reason: String,
        /// What ended the pass early, e.g. the lost link it could not bring back.
        #[source]
        source: Option<Box<SyncError>>,
        /// Each action's failure (Kotlin's suppressed exceptions).
        causes: Vec<SyncError>,
    },

    /// The peer answered, and the answer was "no" - carries the reason it gave, so a refusal is
    /// not reported as a protocol error.
    #[error("{0}")]
    RemoteRejected(String),

    /// The source's mode does not allow this here, e.g. a follower writing into a one-way
    /// source's initiator.
    #[error("{0}")]
    ModeForbidden(String),

    /// The receiver's own file limits had no room for the file. A skip, not a failure.
    #[error("{0}")]
    OverLimit(String),

    /// The source's lease is held elsewhere - usually the peer's pass - so nothing ran.
    /// Worth retrying.
    #[error("{0}")]
    SourceBusy(String),

    /// The pass kept re-planning without converging and was cut off.
    #[error("{reason}")]
    MaxRetriesExceeded {
        /// Where the pass stopped converging.
        reason: String,
        /// What failed underneath, if anything.
        #[source]
        source: Option<Cause>,
    },

    /// A network failure under the pass.
    #[error(transparent)]
    Net(#[from] NetError),

    /// A file access failure under the pass.
    #[error(transparent)]
    Fs(#[from] FsError),

    /// A transfer failure under the pass.
    #[error(transparent)]
    Transfer(#[from] TransferError),
}

/// Failures of an upload or download.
#[derive(Debug, thiserror::Error)]
pub enum TransferError {
    /// No upload is open under this path.
    #[error("Upload not found: {path}")]
    UploadNotFound {
        /// Path the upload was opened for.
        path: String,
    },

    /// The file to send is not there.
    #[error("{0}")]
    FileNotFound(String),

    /// The received bytes do not hash to what the sender declared.
    #[error("Upload hash mismatch: expected {expected}, but got {actual}")]
    UploadHashMismatch {
        /// Hash the sender declared.
        expected: String,
        /// Hash of what arrived.
        actual: String,
    },

    /// The receiver already runs as many uploads as it allows.
    #[error("Too many uploads in progress: at most {max_uploads} at a time")]
    TooManyUploads {
        /// The limit.
        max_uploads: usize,
    },

    /// Out-of-order chunks waiting for their turn take more memory than allowed.
    #[error("Pending chunks overflow: occupied {occupied} bytes, but max is {max} bytes")]
    PendingChunksOverflow {
        /// Bytes held by pending chunks.
        occupied: usize,
        /// The limit.
        max: usize,
    },

    /// Whatever the upload belongs to takes nothing more - a one-shot transfer cancelled or
    /// settled.
    #[error("{0}")]
    UploadStopped(String),

    /// A chunk reaches past the size declared for the file.
    #[error("Chunk [{offset}, +{length}) is outside the declared size {size}")]
    ChunkOutOfBounds {
        /// Offset of the chunk.
        offset: u64,
        /// Length of the chunk.
        length: usize,
        /// Declared size of the file.
        size: u64,
    },
}

/// A device-detection request could not be started or could not finish.
#[derive(Debug, thiserror::Error)]
pub enum DetectionError {
    /// Detection failed for the given reason.
    #[error("{0}")]
    Failed(String),

    /// The scanned payload was not a connection code this build understands.
    #[error("not a connection code this build understands")]
    MalformedQr,
}
