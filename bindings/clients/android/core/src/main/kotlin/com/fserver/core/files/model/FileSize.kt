package com.fserver.core.files.model

@JvmInline
value class FileSize(val bytes: Long) {
    init {
        require(bytes >= 0) { "FileSize cannot be negative" }
    }

    operator fun plus(other: FileSize): FileSize {
        return FileSize(this.bytes + other.bytes)
    }

    override fun toString(): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
