package com.fserver.core.network.impl

import com.fserver.net.NetLogger
import timber.log.Timber

internal object TimberNetLogger : NetLogger {
    override fun debug(message: String) {
        Timber.tag("NetCore").d(message)
    }

    override fun warn(message: String, error: Throwable?) {
        Timber.tag("NetCore").w(error, message)
    }

    override fun error(message: String, error: Throwable?) {
        Timber.tag("NetCore").e(error, message)
    }
}
