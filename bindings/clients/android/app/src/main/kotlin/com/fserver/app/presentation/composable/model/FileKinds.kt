package com.fserver.app.presentation.composable.model

/** The name is all an index row or a scan result carries, so the extension decides the kind. */
fun fileKindOf(name: String): FileKindUi = when (name.fileExtension) {
    in ImageExtensions -> FileKindUi.Image
    in VideoExtensions -> FileKindUi.Video
    in AudioExtensions -> FileKindUi.Audio
    in DocumentExtensions -> FileKindUi.Document
    else -> FileKindUi.Other
}

/** Lowercased and without the dot, empty when the name has none. */
val String.fileExtension: String
    get() = substringAfterLast('.', missingDelimiterValue = "").lowercase()

private val ImageExtensions =
    setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "dng")

private val VideoExtensions = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "mpg")

private val AudioExtensions = setOf("mp3", "aac", "flac", "wav", "ogg", "m4a", "opus", "amr")

private val DocumentExtensions =
    setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf", "odt", "epub")
