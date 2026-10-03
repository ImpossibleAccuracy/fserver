package com.fserver.app.presentation.shared.viewer.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.ViewerTitleHeight

/** Said in place of a document the in-app viewer could not read, with a way out to another app. */
@Composable
internal fun ViewerFailure(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.padding(horizontal = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.file_viewer_unreadable),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        DkSecondaryButton(
            text = stringResource(R.string.file_viewer_open_elsewhere),
            onClick = { context.openInSystemViewer(file) },
        )
    }
}

/** Keeps a scrolling document's ends clear of the title on top and the navigation bar below. */
@Composable
internal fun documentPadding(): PaddingValues {
    val status = WindowInsets.statusBars.asPaddingValues()
    val navigation = WindowInsets.navigationBars.asPaddingValues()

    return PaddingValues(
        top = status.calculateTopPadding() + ViewerTitleHeight,
        bottom = navigation.calculateBottomPadding() + DkSpacing.lg,
    )
}
