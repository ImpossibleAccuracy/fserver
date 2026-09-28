package com.fserver.app.presentation.screens.settings.storage.main.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.LinkDirectionUi
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.StorageUsageUi
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.common.model.FileSize
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class StorageState(
    val isLoading: Boolean = true,
    val usage: StorageUsageUi? = null,
    val links: List<LinkUi> = emptyList(),
    val appData: List<AppDataUi> = emptyList(),
    val freeable: List<FreeableUi> = emptyList(),
) {
    val linksBytes: Long
        get() = links.sumOf { it.bytes }

    val appDataBytes: Long
        get() = appData.sumOf { it.bytes }

    val showsLinksTotal: Boolean
        get() = links.size > 1

    val freeableBytes: Long
        get() = freeable.sumOf { it.bytes }

    val defaultFreeSelection: Set<String>
        get() = freeable.filter { it.selectedByDefault }.mapTo(mutableSetOf()) { it.key }

    @Immutable
    data class LinkUi(
        val id: String,
        val label: String,
        val peer: PeerUi,
        val direction: LinkDirectionUi,
        val files: Int,
        val bytes: Long,
    )

    @Immutable
    data class AppDataUi(
        val kind: AppDataKindUi,
        val bytes: Long,
        val files: Int? = null,
    )

    enum class AppDataKindUi {
        Downloaded,
        Received,
        Cache,
        EvictionPreviews,
        Incomplete;

        val isFreeable: Boolean
            get() = this == Downloaded || this == Cache || this == EvictionPreviews || this == Incomplete

        val isDestructive: Boolean
            get() = this == EvictionPreviews
    }

    @Immutable
    sealed interface FreeableUi {
        val key: String
        val bytes: Long
        val selectedByDefault: Boolean
        val isDestructive: Boolean

        data class AppData(val data: AppDataUi) : FreeableUi {
            override val key: String get() = keyOf(data.kind)
            override val bytes: Long get() = data.bytes
            override val selectedByDefault: Boolean get() = !data.kind.isDestructive
            override val isDestructive: Boolean get() = data.kind.isDestructive

            companion object {
                fun keyOf(kind: AppDataKindUi): String = "app:$kind"
            }
        }

        data class LinkCopies(
            val sourceId: String,
            val label: String,
            val deviceName: String,
            val files: Int,
            override val bytes: Long,
            val previews: List<FileBrowserUi.File> = emptyList(),
        ) : FreeableUi {
            override val key: String get() = "link:$sourceId"
            override val selectedByDefault: Boolean get() = false
            override val isDestructive: Boolean get() = false

            val morePreviews: Int
                get() = files - previews.size
        }
    }

    companion object {
        private val SampleServer = PeerUi("Server", DeviceKind.Nas)
        private val SamplePc = PeerUi("Home PC", DeviceKind.Desktop)
        private val SampleLaptop = PeerUi("Laptop", DeviceKind.Laptop)
        private val SampleCache = AppDataUi(AppDataKindUi.Cache, 3_100_000_000)
        private val SampleDownloaded = AppDataUi(AppDataKindUi.Downloaded, 14_000_000_000, files = 86)
        private val SampleEvictionPreviews = AppDataUi(AppDataKindUi.EvictionPreviews, 48_000_000)
        private val SampleIncomplete = AppDataUi(AppDataKindUi.Incomplete, 1_200_000_000, files = 3)

        val Sample = StorageState(
            isLoading = false,
            usage = StorageUsageUi.Sample.copy(remoteOnlyFiles = 0, remoteOnlyBytes = 0),
            links = listOf(
                LinkUi("camera", "Camera", SampleServer, LinkDirectionUi.Outgoing, 1342, 41_000_000_000),
                LinkUi("downloads", "Downloads", SamplePc, LinkDirectionUi.Incoming, 214, 9_400_000_000),
                LinkUi("whatsapp", "WhatsApp Media", SampleServer, LinkDirectionUi.Outgoing, 2870, 6_200_000_000),
                LinkUi("documents", "Documents", SampleLaptop, LinkDirectionUi.Mirror, 391, 1_800_000_000),
            ),
            appData = listOf(
                SampleDownloaded,
                AppDataUi(AppDataKindUi.Received, 4_300_000_000),
                SampleCache,
                SampleEvictionPreviews,
                SampleIncomplete,
            ),
            freeable = listOf(
                FreeableUi.AppData(SampleDownloaded),
                FreeableUi.AppData(SampleCache),
                FreeableUi.AppData(SampleEvictionPreviews),
                FreeableUi.AppData(SampleIncomplete),
                FreeableUi.LinkCopies(
                    sourceId = "camera",
                    label = "Camera",
                    deviceName = "Server",
                    files = 1118,
                    bytes = 36_000_000_000,
                    previews = List(5) { index ->
                        FileBrowserUi.File(
                            id = "$index",
                            path = "Camera/IMG_$index.jpg",
                            name = "IMG_$index.jpg",
                            kind = FileKindUi.Image,
                            locator = null,
                            size = FileSize(4_000_000),
                            extensionLabel = null,
                        )
                    },
                ),
            ),
        )

        val SampleEmpty = StorageState(
            isLoading = false,
            usage = Sample.usage,
        )
    }
}
