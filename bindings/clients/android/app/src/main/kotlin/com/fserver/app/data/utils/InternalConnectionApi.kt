package com.fserver.app.data.utils

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Connection API is internal and should not be used outside of the library."
)
@Retention(AnnotationRetention.BINARY)
annotation class InternalConnectionApi
