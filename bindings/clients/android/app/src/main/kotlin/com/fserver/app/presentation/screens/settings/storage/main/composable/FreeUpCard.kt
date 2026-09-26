package com.fserver.app.presentation.screens.settings.storage.main.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

/** The one suggestion on the storage screen: how much could go, and the way into the sheet. */
@Composable
fun FreeUpCard(
    modifier: Modifier = Modifier,
    freeableBytes: Long,
    onFreeUp: () -> Unit,
) {
    DkCard(modifier = modifier, outlined = true) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
            ) {
                Text(
                    text = stringResource(
                        R.string.storage_free_up_to,
                        FileSize(freeableBytes).formatted(),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DkCaption(text = stringResource(R.string.storage_free_hint))
            }
            DkPrimaryButton(
                text = stringResource(R.string.storage_free_action),
                onClick = onFreeUp,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun FreeUpCardPreview() {
    FServerTheme {
        FreeUpCard(
            modifier = Modifier.padding(DkSpacing.screenPadding),
            freeableBytes = 54_300_000_000,
            onFreeUp = {},
        )
    }
}
