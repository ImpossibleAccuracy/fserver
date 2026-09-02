package com.fserver.app.presentation.screens.source.shared.preview.composable.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.designkit.DkMediaTile
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.source.shared.preview.composable.FileRadio
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreviewHeader
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreviewSelection
import com.fserver.app.presentation.screens.source.shared.preview.composable.rememberSourceThumbnail
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

private val GridItemSpanMax = GridItemSpan(3)
private const val HeaderKey = "header"

/** Tiles: a gallery is judged by what it looks like, so nothing but the files is on screen. */
@Composable
fun PreviewGallery(
    modifier: Modifier = Modifier,
    preview: SourcePreviewUi.Gallery,
    selection: SourcePreviewSelection? = null,
    header: @Composable (() -> Unit)? = null,
    onFileClick: (SourcePreviewUi.File) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = DkSpacing.screenPadding,
            vertical = DkSpacing.sm,
        ),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (header != null) {
            item(key = HeaderKey, span = { GridItemSpanMax }) { header() }
        }

        items(items = preview.files, key = { it.path }) { file ->
            PreviewGalleryTile(
                file = file,
                selection = selection,
                onFileClick = onFileClick,
            )
        }
    }
}

@Composable
fun PreviewGalleryTile(
    file: SourcePreviewUi.File,
    selection: SourcePreviewSelection?,
    onFileClick: (SourcePreviewUi.File) -> Unit,
) {
    // TODO: replace with coil
    val thumbnail by rememberSourceThumbnail(file)

    Box {
        DkMediaTile(
            extensionLabel = file.extensionLabel,
            thumbnail = thumbnail,
            onClick = { onFileClick(file) },
        )
        // On the tile rather than beside it: a grid has no gutter to put a control in.
        Box(modifier = Modifier.align(Alignment.TopStart)) {
            FileRadio(
                file = file,
                selection = selection
            )
        }
    }
}

@Preview(name = "Gallery", showBackground = true)
@Composable
private fun SourcePreviewGalleryPreview() {
    FServerTheme {
        PreviewGallery(
            preview = SourcePreviewUi.Gallery(
                files = listOf(SampleImage, SampleDocument),
            ),
            onFileClick = {},
            header = {
                SourcePreviewHeader(
                    title = "/DCIM/Projects",
                    detail = "842 files · 6.1 GB"
                )
            },
        )
    }
}

private val SampleImage = SourcePreviewUi.File(
    path = "primary/DCIM/Camera/IMG_0001.jpg",
    name = "IMG_0001.jpg",
    kind = FileKindUi.Image,
    locator = "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg",
    size = FileSize(4_210_000),
    extensionLabel = null,
)

private val SampleDocument = SourcePreviewUi.File(
    path = "primary/Documents/report.pdf",
    name = "report.pdf",
    kind = FileKindUi.Document,
    locator = "/storage/emulated/0/Documents/report.pdf",
    size = FileSize(820_000),
    extensionLabel = "PDF",
)
