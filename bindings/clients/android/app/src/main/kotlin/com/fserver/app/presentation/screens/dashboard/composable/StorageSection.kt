package com.fserver.app.presentation.screens.dashboard.composable

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.dashboard.model.DashboardState
import com.fserver.app.presentation.theme.FServerTheme

/** How full the phone is, split into what this app holds, everything else, and what is left. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageSection(
    modifier: Modifier = Modifier,
    storage: DashboardState.StorageUi,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    val appColor = colors.primary
    val otherColor = colors.outline
    val freeColor = colors.surfaceVariant

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = size(storage.usedBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Text(
                modifier = Modifier.padding(start = DkSpacing.sm, bottom = DkSpacing.xs),
                text = stringResource(R.string.dashboard_storage_used, size(storage.totalBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }

        val total = storage.totalBytes.coerceAtLeast(1).toFloat()
        val appFraction = storage.appBytes / total
        val otherFraction = storage.otherBytes / total

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(freeColor)
                .drawBehind {
                    val appWidth = size.width * appFraction
                    drawRect(appColor, size = Size(appWidth, size.height))
                    drawRect(
                        color = otherColor,
                        topLeft = Offset(appWidth, 0f),
                        size = Size(size.width * otherFraction, size.height),
                    )
                },
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
        ) {
            LegendItem(
                text = stringResource(R.string.dashboard_storage_app, size(storage.appBytes)),
                color = appColor,
            )
            LegendItem(
                text = stringResource(R.string.dashboard_storage_other, size(storage.otherBytes)),
                color = otherColor,
            )
            LegendItem(
                text = stringResource(R.string.dashboard_storage_free, size(storage.freeBytes)),
                color = freeColor,
            )
        }
    }
}

@Composable
private fun LegendItem(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StorageSectionPreview() {
    FServerTheme {
        StorageSection(
            modifier = Modifier.padding(DkSpacing.screenPadding),
            storage = DashboardState.Sample.storage!!,
        )
    }
}
