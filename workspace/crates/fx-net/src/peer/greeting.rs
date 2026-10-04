use std::ops::RangeInclusive;

use crate::security::auth::AuthMethodId;

/// Everything a device will tell a stranger: which protocol versions it speaks and
/// which ways it is willing to be authenticated.
///
/// **Advisory only.** Nothing here is verified and nothing derived from it may decide anything: a
/// greeting may lie. The connection that follows says hello again on its own link and
/// binds that hello to the authentication, so a lie told here fails there.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PublicGreeting {
    /// Wire protocol versions the peer claims to speak.
    pub protocol_versions: RangeInclusive<u16>,
    /// Auth methods the peer claims to accept.
    pub methods: Vec<AuthMethodId>,
}
