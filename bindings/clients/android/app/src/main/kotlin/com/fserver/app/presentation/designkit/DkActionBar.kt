package com.fserver.app.presentation.designkit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.presentation.theme.FServerTheme

/**
 * The controls that end a screen, pinned to its bottom instead of floating after the content.
 *
 * A flow step's continue and back are the same two buttons in the same place on every screen,
 * so they belong to the frame rather than to the scroll: a long form should not push them out
 * of reach, and a short one should not leave them halfway up.
 *
 * Nocturne separates it from the content with the same hairline the navigation bar uses — no
 * fill, no elevation. Buttons stack full-width in the order given, most important first.
 */
@Composable
fun DkActionBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        content = content,
    )
}

@Preview
@Composable
private fun DkActionBarPreview() {
    FServerTheme {
        DkSurfacePreview {
            DkActionBar {
                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Continue",
                    onClick = {},
                )
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Back",
                    onClick = {},
                )
            }
        }
    }
}
