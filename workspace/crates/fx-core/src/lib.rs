//! The engine: sources, sync, conflicts, host and offload modes, the SQLite index
//! (ToR §2, §3.4, §4.3, `docs/architecture/overview.md`).
//! The only crate that knows what a source, a version or a conflict is.

mod error;

pub use error::{CoreError, DetectionError, SyncError, TransferError};
use tokio::runtime::Runtime;

/// Engine configuration supplied by the host (`docs/architecture/overview.md` §2.1).
#[derive(Debug, Clone, Default)]
pub struct EngineConfig {
    /// Directory for the engine's private state (index, keys, staging).
    pub data_dir: String,
}

/// Entry point of the core. Owns its async runtime; the runtime is never exposed.
pub struct Engine {
    config: EngineConfig,
    _runtime: Runtime,
}

impl Engine {
    /// Wires the engine (`docs/architecture/overview.md` §2.2). Nothing network-facing
    /// starts by itself.
    pub fn new(config: EngineConfig) -> Result<Self, CoreError> {
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .thread_name("fx-core")
            .enable_all()
            .build()?;
        Ok(Self {
            config,
            _runtime: runtime,
        })
    }

    /// Version of this core build.
    pub fn core_version() -> String {
        env!("CARGO_PKG_VERSION").to_owned()
    }

    /// Configuration the engine was created with.
    pub fn config(&self) -> &EngineConfig {
        &self.config
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn core_version_is_package_version() {
        assert_eq!(Engine::core_version(), env!("CARGO_PKG_VERSION"));
    }

    #[test]
    fn engine_starts() {
        let engine = Engine::new(EngineConfig::default()).expect("engine");
        assert_eq!(engine.config().data_dir, "");
    }
}
