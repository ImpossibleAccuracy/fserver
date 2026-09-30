package com.fserver.core.sync.server.handler.upload.oneshot

import com.fserver.files.fs.FileSystem

/**
 * A peer-reported name made safe to write: one segment, no separators or control characters, not
 * hidden, bounded. The peer never picks a path, only a hint for a name.
 */
internal fun safeFileName(name: String): String {
    val cleaned = name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .filterNot { it.isISOControl() || it in ForbiddenChars }
        .trim()
        .trimStart('.')
        .take(MaxNameLength)

    return cleaned.ifEmpty { FallbackName }
}

/**
 * [name] if [fs] has nothing there, else "name (1).ext", "name (2).ext"... A received file never
 * replaces one the user already has.
 */
internal suspend fun freeName(fs: FileSystem, name: String): String {
    if (!fs.fileExists(name)) return name

    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val extension = if (dot > 0) name.substring(dot) else ""

    for (n in 1..MaxAttempts) {
        val candidate = "$base ($n)$extension"
        if (!fs.fileExists(candidate)) return candidate
    }

    error("No free name for $name after $MaxAttempts attempts")
}

private val ForbiddenChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

private const val MaxNameLength = 200
private const val MaxAttempts = 1000
private const val FallbackName = "file"
