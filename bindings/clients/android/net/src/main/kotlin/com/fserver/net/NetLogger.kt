package com.fserver.net

/**
 * Logging seam. `:net` has no platform logger of its own - the host passes one in
 * (Timber on Android), and the default swallows everything.
 */
interface NetLogger {
    fun debug(message: String) = Unit
    fun warn(message: String, error: Throwable? = null) = Unit
    fun error(message: String, error: Throwable? = null) = Unit

    companion object {
        val None: NetLogger = object : NetLogger {}
    }
}
