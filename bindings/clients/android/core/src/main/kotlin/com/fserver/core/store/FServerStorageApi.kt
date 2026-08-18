package com.fserver.core.store

/**
 * Marks the persistence SPI `:core` needs from its host - **not** a repository the UI should talk
 * to.
 *
 * Every interface under this package carries `@SubclassOptInRequired(FServerStorageApi::class)`,
 * so implementing one is a deliberate act. That is the whole point: a store is the narrowest set
 * of operations the engine itself calls, shaped for the engine. Screens want a repository
 * (`:core:storage` publishes those), which is a different, wider, UI-shaped surface.
 *
 * If you are reaching for `@OptIn` outside a storage module, the repository you actually wanted is
 * missing - add it there instead.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Storage SPI, not a repository. Implement it only inside a storage module; " +
            "screens should depend on the repositories published by :core:storage.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class FServerStorageApi
