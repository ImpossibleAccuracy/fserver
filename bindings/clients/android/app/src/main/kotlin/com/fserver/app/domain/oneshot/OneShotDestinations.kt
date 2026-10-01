package com.fserver.app.domain.oneshot

import com.fserver.core.files.SourceLocation

/** The fixed destinations received files can go to; a picked folder is the third kind. */
object OneShotDestinations {
    val Downloads = SourceLocation.Downloads("FServer")
    val AppStorage = SourceLocation.Internal("oneshot-received")
}
