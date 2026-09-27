package com.fserver.app.presentation.designkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun DkProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    val barModifier = modifier
        .fillMaxWidth()
        .height(4.dp)
        .clip(RoundedCornerShape(2.dp))

    if (progress == null) {
        LinearProgressIndicator(
            modifier = barModifier,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            gapSize = 0.dp,
        )
        return
    }

    LinearProgressIndicator(
        progress = { progress },
        modifier = barModifier,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** The small spinner that sits inline in the "still searching…" row. */
@Composable
fun DkInlineSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(14.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        strokeWidth = 2.dp,
    )
}

/**
 * Where a fixed-length flow is: one segment per step, filled up to and including [currentStep]
 * (1-based). Sits under the top bar, so the step count is read before the question.
 */
@Composable
fun DkStepBar(
    modifier: Modifier = Modifier,
    stepCount: Int,
    currentStep: Int,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        repeat(stepCount) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < currentStep) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
            )
        }
    }
}

@Preview
@Composable
private fun DkStepBarPreview() {
    FServerTheme {
        DkSurfacePreview {
            DkStepBar(
                modifier = Modifier.padding(DkSpacing.lg),
                stepCount = 3,
                currentStep = 2,
            )
        }
    }
}
