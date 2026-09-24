package com.fserver.app.presentation.shared.browser.layouts

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
import com.fserver.app.presentation.shared.browser.FileBrowser
import com.fserver.app.presentation.shared.browser.FileBrowserHeader
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.FileRadio
import com.fserver.app.presentation.shared.browser.icon
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

private const val HeaderKey = "header"

/** Rows: name, size, and what kind of file it is. The row is the touch target that opens it. */
@Composable
fun BrowserList(
    modifier: Modifier = Modifier,
    preview: FileBrowserUi.PlainList,
    selection: FileBrowserSelection? = null,
    header: @Composable (() -> Unit)? = null,
    onFileClick: (FileBrowserUi.File) -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (header != null) {
            item(key = HeaderKey) { header() }
        }

        items(preview.files, key = { it.path }) { file ->
            DkListRow(
                title = file.name,
                subtitle = file.size?.formatted(),
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
private fun FileBrowserListPreview() {
    FServerTheme {
        FileBrowser(
            preview = FileBrowserUi.PlainList(
                files = listOf(SampleImage, SampleDocument),
            ),
            onFileClick = {},
            header = {
                FileBrowserHeader(
                    title = "/DCIM/Projects",
                    detail = "842 files · 6.1 GB"
                )
            },
        )
    }
}

@Preview(name = "List, selectable", showBackground = true)
@Composable
private fun FileBrowserSelectableListPreview() {
    FServerTheme {
        FileBrowser(
            preview = FileBrowserUi.PlainList(
                files = listOf(SampleImage, SampleDocument),
            ),
            onFileClick = {},
            selection = FileBrowserSelection(
                selected = SampleImage,
                onSelectFile = {},
            ),
        )
    }
}

private val SampleImage = FileBrowserUi.File(
    id = "1",
    path = "primary/DCIM/Camera/IMG_0001.jpg",
    name = "IMG_0001.jpg",
    kind = FileKindUi.Image,
    locator = "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg",
    size = FileSize(4_210_000),
    extensionLabel = null,
)

private val SampleDocument = FileBrowserUi.File(
    id = "2",
    path = "primary/Documents/report.pdf",
    name = "report.pdf",
    kind = FileKindUi.Document,
    locator = "/storage/emulated/0/Documents/report.pdf",
    size = FileSize(820_000),
    extensionLabel = "PDF",
)
