package com.fserver.app.presentation.screens.source.request.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkStepBar
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.screens.source.request.model.SyncRequestState
import com.fserver.app.presentation.theme.FServerTheme

/** Top bar of every answering step: title, the source as subtitle, and where in the three steps. */
@Composable
fun SyncRequestTopBar(
    modifier: Modifier = Modifier,
    title: String,
    step: Int,
    onBack: () -> Unit,
    subtitle: String? = null,
) {
    Column(modifier = modifier) {
        DkTopBar(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            actions = {
                Text(
                    modifier = Modifier.padding(end = DkSpacing.screenPadding),
                    text = stringResource(
                        R.string.sync_request_step,
                        step,
                        SyncRequestState.StepCount,
                    ),
                    style = DkType.mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        DkStepBar(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
            stepCount = SyncRequestState.StepCount,
            currentStep = step,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SyncRequestTopBarPreview() {
    FServerTheme {
        SyncRequestTopBar(
            title = "Where to keep it?",
            subtitle = "/Projects",
            step = 2,
            onBack = {},
        )
    }
}
