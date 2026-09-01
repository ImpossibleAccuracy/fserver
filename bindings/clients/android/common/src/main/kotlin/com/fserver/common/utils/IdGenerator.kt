package com.fserver.common.utils

import kotlin.uuid.Uuid

/** ID generator utility. */
object IdGenerator {
    val nextId: String
        get() = Uuid.random().toString()
}
