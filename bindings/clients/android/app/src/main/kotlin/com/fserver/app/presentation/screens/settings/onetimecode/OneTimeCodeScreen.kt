package com.fserver.app.presentation.screens.settings.onetimecode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.screens.settings.onetimecode.model.OneTimeCodeIntent
import com.fserver.app.presentation.screens.settings.onetimecode.model.OneTimeCodeState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun OneTimeCodeScreen(
    modifier: Modifier = Modifier,
    viewModel: OneTimeCodeViewModel = koinViewModel(),
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    OneTimeCodeScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

@Composable
private fun OneTimeCodeScreenContent(
    modifier: Modifier = Modifier,
    state: OneTimeCodeState,
    onIntent: (OneTimeCodeIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.one_time_code_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
        ) {
            CodeCard(state = state)

            DkInfoBox(text = stringResource(R.string.one_time_code_hint))

            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = DkSpacing.lg)
            )

            Column(
                modifier = Modifier.padding(bottom = DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.one_time_code_new),
                    onClick = { onIntent(OneTimeCodeIntent.NewCode) },
                )
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_cancel),
                    onClick = navigateUp,
                )
            }
        }
    }
}

@Composable
private fun CodeCard(
    modifier: Modifier = Modifier,
    state: OneTimeCodeState,
) {
    DkCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = DkSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            if (state.isActive) {
                Text(
                    text = state.codeGroups.joinToString(" "),
                    style = DkType.monoCode,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.one_time_code_expires_in, state.remaining.format()),
                    style = DkType.monoLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(state.status.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val OneTimeCodeState.Status.messageRes: Int
    get() = when (this) {
        OneTimeCodeState.Status.Idle, OneTimeCodeState.Status.Active -> R.string.one_time_code_idle
        OneTimeCodeState.Status.Expired -> R.string.one_time_code_expired
        OneTimeCodeState.Status.Spent -> R.string.one_time_code_spent
        OneTimeCodeState.Status.Used -> R.string.one_time_code_used
    }

private fun Duration.format(): String = toComponents { minutes, seconds, _ ->
    "%d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun OneTimeCodeScreenPreview() {
    FServerTheme {
        OneTimeCodeScreenContent(
            state = OneTimeCodeState(
                status = OneTimeCodeState.Status.Active,
                codeGroups = listOf("482", "193"),
                remaining = 105.seconds,
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Spent", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun OneTimeCodeScreenSpentPreview() {
    FServerTheme {
        OneTimeCodeScreenContent(
            state = OneTimeCodeState(status = OneTimeCodeState.Status.Spent),
            onIntent = {},
            navigateUp = {},
        )
    }
}
