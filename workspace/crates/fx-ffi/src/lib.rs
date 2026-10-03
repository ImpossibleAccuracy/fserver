//! UniFFI adapter: the only crate that depends on `uniffi`. Translates `fx-core`
//! types and errors for Kotlin/Swift and exposes platform seams as foreign traits.
//! Thin layer: no business logic (ToR §1.2, §4.4).

use std::sync::Arc;

uniffi::setup_scaffolding!();

/// Engine configuration as seen by Kotlin/Swift.
#[derive(uniffi::Record)]
pub struct EngineConfig {
    /// Directory for the engine's private state.
    pub data_dir: String,
}

impl From<EngineConfig> for fx_core::EngineConfig {
    fn from(value: EngineConfig) -> Self {
        Self {
            data_dir: value.data_dir,
        }
    }
}

/// Error crossing the FFI boundary.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiError {
    /// The engine failed to start.
    #[error("engine: {reason}")]
    Engine {
        /// Human-readable cause. Not `message`: it would clash with `Throwable.message` in Kotlin.
        reason: String,
    },
}

impl From<fx_core::CoreError> for FfiError {
    fn from(value: fx_core::CoreError) -> Self {
        Self::Engine {
            reason: value.to_string(),
        }
    }
}

/// The engine handle exported to Kotlin/Swift.
#[derive(uniffi::Object)]
pub struct FxEngine {
    _inner: fx_core::Engine,
}

#[uniffi::export]
impl FxEngine {
    /// Creates the engine.
    #[uniffi::constructor]
    pub fn new(config: EngineConfig) -> Result<Arc<Self>, FfiError> {
        let inner = fx_core::Engine::new(config.into())?;
        Ok(Arc::new(Self { _inner: inner }))
    }

    /// Version of the core build.
    pub fn core_version(&self) -> String {
        fx_core::Engine::core_version()
    }
}

/// Version of the core build, callable without an engine.
#[uniffi::export]
pub fn core_version() -> String {
    fx_core::Engine::core_version()
}
