package com.fserver.common.model

/** Carries the file hash. */
data class ContentHash(
    val value: String,
    /** Hash algorithm, so records hashed by different versions never compare equal by accident */
    val algorithm: String,
)