package com.fserver.app.data

import com.fserver.app.R
import com.fserver.app.presentation.composable.IncomingFileUi
import com.fserver.app.presentation.composable.IncomingRequestUi
import com.fserver.app.presentation.composable.model.DiagnosticCheckUi
import com.fserver.app.presentation.composable.model.FileAvailabilityUi
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.FileUi
import com.fserver.app.presentation.composable.model.TreeNodeUi
import com.fserver.app.presentation.designkit.DkCheckState

/**
 * Fixtures the MVP screens render until `:core` is wired up. Kept in one place so the
 * screens never grow their own literals — swapping this out for real state is a
 * constructor change, not a screen rewrite.
 */
object SampleData {

    const val NETWORK_NAME = "Home_5G"
    const val CURRENT_SERVER = "MacBook-Pro"
    const val BREADCRUMB = "/ Documents / Shoot"
    const val DOWNLOAD_FOLDER = "/Exchange"

    /** What the demo reticle "decodes" until a camera is bound. */
    const val QR_PAYLOAD = """{"ip":"192.168.1.42","port":8384}"""

    val files = listOf(
        FileUi(
            id = "drafts",
            name = "Drafts",
            kind = FileKindUi.Folder,
            childCount = 14,
        ),
        FileUi(
            id = "img4831",
            name = "IMG_4831.RAW",
            kind = FileKindUi.Image,
            sizeLabel = "28.4 MB",
            dateLabel = "yesterday",
            availability = FileAvailabilityUi.OnServer,
        ),
        FileUi(
            id = "interview",
            name = "interview_02.wav",
            kind = FileKindUi.Audio,
            sizeLabel = "112 MB",
            dateLabel = "Jul 28",
            availability = FileAvailabilityUi.OnDevice,
        ),
        FileUi(
            id = "estimate",
            name = "estimate_final.pdf",
            kind = FileKindUi.Document,
            sizeLabel = "1.2 MB",
            dateLabel = "Jul 26",
            availability = FileAvailabilityUi.OnDevice,
        ),
        FileUi(
            id = "clip",
            name = "clip_final.mp4",
            kind = FileKindUi.Video,
            sizeLabel = "1.8 GB",
            dateLabel = "Jul 24",
            availability = FileAvailabilityUi.OnServer,
        ),
    )

    const val GRID_ITEM_COUNT = 42

    val gridTiles = listOf(
        FileUi("g1", "IMG_4831.RAW", FileKindUi.Image, availability = FileAvailabilityUi.OnServer),
        FileUi("g2", "IMG_4832.RAW", FileKindUi.Image, availability = FileAvailabilityUi.OnDevice),
        FileUi("g3", "IMG_4833.RAW", FileKindUi.Image, availability = FileAvailabilityUi.OnServer),
        FileUi("g4", "IMG_4834.RAW", FileKindUi.Image, availability = FileAvailabilityUi.OnDevice),
        FileUi(
            id = "g5",
            name = "clip_final.mp4",
            kind = FileKindUi.Video,
            durationLabel = "0:42",
            availability = FileAvailabilityUi.OnDevice,
        ),
        FileUi("g6", "IMG_4836.RAW", FileKindUi.Image, availability = FileAvailabilityUi.OnDevice),
        FileUi("g7", "IMG_4837.RAW", FileKindUi.Image, availability = FileAvailabilityUi.OnDevice),
        FileUi("g8", "IMG_4838.RAW", FileKindUi.Image, availability = FileAvailabilityUi.OnServer),
        FileUi("g9", "IMG_4839.RAW", FileKindUi.Image, availability = FileAvailabilityUi.OnDevice),
        FileUi(
            id = "g10",
            name = "estimate_final.pdf",
            kind = FileKindUi.Document,
            extensionLabel = "pdf",
            availability = FileAvailabilityUi.OnDevice,
        ),
        FileUi(
            id = "g11",
            name = "interview_02.wav",
            kind = FileKindUi.Audio,
            extensionLabel = "wav",
            availability = FileAvailabilityUi.OnDevice,
        ),
        FileUi("g12", "IMG_4840.RAW", FileKindUi.Image, availability = FileAvailabilityUi.OnDevice),
    )

    val tree = listOf(
        TreeNodeUi("t-docs", "Documents", depth = 0, isFolder = true, expanded = true),
        TreeNodeUi("t-shoot", "Shoot", depth = 1, isFolder = true, expanded = true),
        TreeNodeUi(
            id = "t-img",
            name = "IMG_4831.RAW",
            depth = 2,
            isFolder = false,
            availability = FileAvailabilityUi.OnServer,
        ),
        TreeNodeUi(
            id = "t-clip",
            name = "clip_final.mp4",
            depth = 2,
            isFolder = false,
            availability = FileAvailabilityUi.OnServer,
        ),
        TreeNodeUi(
            id = "t-drafts",
            name = "Drafts",
            depth = 1,
            isFolder = true,
            childCountLabel = "14",
        ),
        TreeNodeUi(
            id = "t-estimate",
            name = "estimate_final.pdf",
            depth = 1,
            isFolder = false,
            availability = FileAvailabilityUi.OnDevice,
        ),
        TreeNodeUi("t-music", "Music", depth = 0, isFolder = true, childCountLabel = "210"),
        TreeNodeUi("t-projects", "Projects", depth = 0, isFolder = true, childCountLabel = "38"),
    )

    /**
     * Two of these come back "warning" on a perfectly working setup — a router that drops
     * multicast, a server one protocol version behind. Both are facts with a consequence,
     * not failures: the negotiated-down protocol still works, and manual connection works
     * without mDNS.
     */
    val diagnosticChecks = listOf(
        DiagnosticCheckUi(
            id = "wifi",
            titleRes = R.string.diagnostics_wifi,
            detailRes = R.string.diagnostics_wifi_value,
            state = DkCheckState.Ok,
        ),
        DiagnosticCheckUi(
            id = "permission",
            titleRes = R.string.diagnostics_permission,
            detailRes = R.string.diagnostics_permission_value,
            state = DkCheckState.Ok,
        ),
        DiagnosticCheckUi(
            id = "mdns",
            titleRes = R.string.diagnostics_mdns,
            detailRes = R.string.diagnostics_mdns_value,
            state = DkCheckState.Warning,
        ),
        DiagnosticCheckUi(
            id = "tls",
            titleRes = R.string.diagnostics_tls,
            detailRes = R.string.diagnostics_tls_value,
            state = DkCheckState.Ok,
        ),
        DiagnosticCheckUi(
            id = "protocol",
            titleRes = R.string.diagnostics_protocol,
            detailRes = R.string.diagnostics_protocol_value,
            state = DkCheckState.Warning,
        ),
    )

    val incomingRequest = IncomingRequestUi(
        fromDeviceName = "MacBook-Pro",
        totalSizeLabel = "214 MB",
        files = listOf(
            IncomingFileUi("IMG_4831.RAW", "28.4 MB"),
            IncomingFileUi("interview_02.wav", "112 MB"),
            IncomingFileUi("clip_preview.mp4", "73.2 MB"),
        ),
        destinationLabel = "Downloads/Exchange",
    )
}