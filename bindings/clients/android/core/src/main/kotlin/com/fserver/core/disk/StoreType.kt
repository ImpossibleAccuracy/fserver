package com.fserver.core.disk

/** Where a host keeps bytes it reports, so [DiskUsageRepository] does not count them twice. */
enum class StoreType {
    /** The app's cache directories: already inside [AppFootprint.cacheBytes]. */
    Cache,

    /** The app's private data directory, outside cache: already inside [AppFootprint.serviceBytes]. */
    AppData,

    /** Anywhere else: counted nowhere but where it is reported. */
    Other,
}
