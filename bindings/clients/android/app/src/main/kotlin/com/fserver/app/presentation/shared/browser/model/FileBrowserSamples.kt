package com.fserver.app.presentation.shared.browser.model

import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.common.model.FileSize
import kotlin.time.Instant

/** Feed-shaped sample content, for `@Preview` bodies that would otherwise draw a spinner. */
val FileBrowserUi.Companion.SampleFiles: List<FileBrowserUi.File>
    get() = listOf(
        FileBrowserUi.File(
            id = "camera",
            path = "/DCIM/Camera",
            name = "Camera",
            kind = FileKindUi.Folder,
            locator = null,
            size = null,
            extensionLabel = null,
        ),
        FileBrowserUi.File(
            id = "img-0001",
            path = "/DCIM/Camera/IMG_0001.jpg",
            name = "IMG_0001.jpg",
            kind = FileKindUi.Image,
            locator = "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg",
            size = FileSize(4_210_000),
            modifiedAt = Instant.fromEpochMilliseconds(1_757_000_000_000),
            location = FileBrowserUi.File.Location.Local,
            extensionLabel = null,
        ),
        FileBrowserUi.File(
            id = "report",
            path = "/Documents/report.pdf",
            name = "report.pdf",
            kind = FileKindUi.Document,
            locator = "/storage/emulated/0/Documents/report.pdf",
            size = FileSize(820_000),
            modifiedAt = Instant.fromEpochMilliseconds(1_756_000_000_000),
            location = FileBrowserUi.File.Location.Local,
            extensionLabel = "PDF",
        ),
        FileBrowserUi.File(
            id = "backup",
            path = "/Backups/laptop-2026-08.zip",
            name = "laptop-2026-08.zip",
            kind = FileKindUi.Other,
            locator = null,
            size = FileSize(1_920_000_000),
            modifiedAt = Instant.fromEpochMilliseconds(1_754_000_000_000),
            location = FileBrowserUi.File.Location.Remote,
            extensionLabel = "ZIP",
        ),
        FileBrowserUi.File(
            id = "talk",
            path = "/Music/standup.m4a",
            name = "standup.m4a",
            kind = FileKindUi.Audio,
            locator = null,
            size = FileSize(36_400_000),
            modifiedAt = Instant.fromEpochMilliseconds(1_750_000_000_000),
            location = FileBrowserUi.File.Location.Remote,
            extensionLabel = null,
        ),
    )
