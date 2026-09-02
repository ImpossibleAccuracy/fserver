package com.fserver.app.presentation.screens.source.shared.preview.composable.layouts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.screens.source.shared.preview.composable.FileRadio
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreview
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreviewHeader
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreviewSelection
import com.fserver.app.presentation.screens.source.shared.preview.composable.icon
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

private const val HeaderKey = "header"

/** Rows: name, size, and what kind of file it is. The row is the touch target that opens it. */
@Composable
fun PreviewList(
    modifier: Modifier = Modifier,
    preview: SourcePreviewUi.PlainList,
    selection: SourcePreviewSelection? = null,
    header: @Composable (() -> Unit)? = null,
    onFileClick: (SourcePreviewUi.File) -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (header != null) {
            item(key = HeaderKey) { header() }
        }

        items(preview.files, key = { it.path }) { file ->
            DkListRow(
                title = file.name,
                subtitle = file.size.formatted(),
                onClick = { onFileClick(file) },
                leading = { DkThumbnail(icon = file.kind.icon()) },
                trailing = {
                    FileRadio(
                        file = file,
                        selection = selection
                    )
                },
            )
            DkFadingDivider()
        }
    }
}

@Preview(name = "List", showBackground = true)
@Composable
private fun SourcePreviewListPreview() {
    FServerTheme {
        SourcePreview(
            preview = SourcePreviewUi.PlainList(
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

@Preview(name = "List, selectable", showBackground = true)
@Composable
private fun SourcePreviewSelectableListPreview() {
    FServerTheme {
        SourcePreview(
            preview = SourcePreviewUi.PlainList(
                files = listOf(SampleImage, SampleDocument),
            ),
            onFileClick = {},
            selection = SourcePreviewSelection(
                selected = SampleImage,
                onSelectFile = {},
            ),
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
