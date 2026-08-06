package com.fserver.app.data

import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCheckState
import com.fserver.app.presentation.model.DeviceUi
import com.fserver.app.presentation.model.DiagnosticCheckUi
import com.fserver.app.presentation.model.FileAvailabilityUi
import com.fserver.app.presentation.model.FileKindUi
import com.fserver.app.presentation.model.FileUi
import com.fserver.app.presentation.model.IncomingFileUi
import com.fserver.app.presentation.model.IncomingRequestUi
import com.fserver.app.presentation.model.PairingCandidateUi
import com.fserver.app.presentation.model.PickedEntryUi
import com.fserver.app.presentation.model.ServerProfileUi
import com.fserver.app.presentation.model.TransferUi
import com.fserver.app.presentation.model.TreeNodeUi

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

    val devices = listOf(
        DeviceUi(
            id = "macbook",
            name = "MacBook-Pro.local",
            address = "192.168.1.14:8384",
            online = true,
        ),
        DeviceUi(
            id = "nas",
            name = "HOME-NAS",
            address = "nas.local:8384",
            online = false,
            lastSeenLabel = "2 h",
        ),
    )

    val pairingCandidate = PairingCandidateUi(
        deviceName = "MacBook-Pro.local",
        address = "192.168.1.14:8384",
        technicalLine = "192.168.1.14:8384 · TLS 1.3 · protocol v1",
        fingerprintGroups = listOf("9f2c 4a01", "b7d3 e820", "15aa cc94", "0f6b 7e31"),
    )

    val scannedProfile = ServerProfileUi(
        deviceName = "HOME-NAS",
        address = "nas.local:8384",
        fingerprintVerified = true,
    )

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

    /** What the picker screen shows before anything real is selected. */
    val pickedEntries = listOf(
        PickedEntryUi(
            id = "p-shoot",
            name = "Shoot",
            path = "/storage/emulated/0/DCIM/Shoot",
            kind = FileKindUi.Folder,
            isDirectory = true,
            detailLabel = "14 files · 2.1 GB",
        ),
        PickedEntryUi(
            id = "p-img4831",
            name = "IMG_4831.RAW",
            path = "/storage/emulated/0/DCIM/IMG_4831.RAW",
            kind = FileKindUi.Image,
            isDirectory = false,
            detailLabel = "28.4 MB",
        ),
        PickedEntryUi(
            id = "p-interview",
            name = "interview_02.wav",
            path = "/storage/emulated/0/Recordings/interview_02.wav",
            kind = FileKindUi.Audio,
            isDirectory = false,
            detailLabel = "112 MB",
        ),
        PickedEntryUi(
            id = "p-estimate",
            name = "estimate_final.pdf",
            path = "/storage/emulated/0/Documents/estimate_final.pdf",
            kind = FileKindUi.Document,
            isDirectory = false,
            detailLabel = "1.2 MB",
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

    val transfers = listOf(
        TransferUi.Running(
            id = "tr-clip",
            fileName = "clip_final.mp4",
            progress = 0.62f,
            transferredLabel = "1.1",
            totalLabel = "1.8 GB",
            speedLabel = "41 MB/s",
            etaLabel = "18 s",
        ),
        TransferUi.Queued(id = "tr-interview", fileName = "interview_02.wav"),
        TransferUi.Interrupted(id = "tr-raw", fileName = "IMG_4830.RAW", stoppedAtPercent = 74),
        TransferUi.Completed(id = "tr-estimate", fileName = "estimate_final.pdf"),
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