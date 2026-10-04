//! Version of the wire protocol - the envelope layout and the handshake.
//! Independent of the dictionary version, which the application negotiates separately.

use std::ops::RangeInclusive;

/// The version this build speaks.
pub(crate) const CURRENT: u8 = 1;

/// Versions this build can still speak.
pub(crate) const SUPPORTED: RangeInclusive<u16> = 1..=1;
