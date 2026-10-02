package com.fserver.core.crypto.spi

import javax.crypto.SecretKey

/** A data key files are sealed with. [id] goes into every header sealed under it. */
class StorageKey(val id: String, val secret: SecretKey) {
    override fun toString(): String = "StorageKey($id)"
}
