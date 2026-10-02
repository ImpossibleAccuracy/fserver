package com.fserver.core.crypto.internal

import com.fserver.core.crypto.impl.AesGcmCipher
import com.fserver.core.crypto.spi.StorageCipher

/** The built-in algorithm under another id: enough to tell which one sealed a file. */
internal object AltCipher : StorageCipher by AesGcmCipher {
    override val id = "test.alt.v1"
}
