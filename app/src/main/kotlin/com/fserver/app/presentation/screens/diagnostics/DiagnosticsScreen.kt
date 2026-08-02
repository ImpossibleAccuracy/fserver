package com.fserver.app.presentation.screens.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkStatusRow
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.DiagnosticCheckUi
import com.fserver.app.presentation.screens.diagnostics.model.DiagnosticsIntent
import com.fserver.app.presentation.screens.diagnostics.model.DiagnosticsState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel = koinViewModel(),
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DiagnosticsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

@Composable
private fun DiagnosticsScreen(
    state: DiagnosticsState,
    onIntent: (DiagnosticsIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    val context = LocalContext.current

    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.diagnostics_title),
                onBack = navigateUp,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.padding(DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkPrimaryButton(
                    text = stringResource(R.string.diagnostics_recheck),
                    onClick = { onIntent(DiagnosticsIntent.RecheckClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DkSecondaryButton(
                    text = stringResource(R.string.diagnostics_copy_report),
                    onClick = {
                        context.copyReport(
                            state.checks.asReport(context)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DkSpacing.screenPadding),
        ) {
            state.checks.forEach { check ->
                DkStatusRow(
                    title = stringResource(check.titleRes),
                    detail = stringResource(check.detailRes),
                    state = check.state,
                )
                DkFadingDivider()
            }

            DkInfoBox(
                text = stringResource(R.string.diagnostics_hint),
                modifier = Modifier.padding(top = DkSpacing.lg),
            )
        }
    }
}

/** Plain text, in the same order as the screen, ready to paste into a bug report. */
private fun List<DiagnosticCheckUi>.asReport(context: Context): String =
    joinToString("\n") { check ->
        "[${check.state}] ${context.getString(check.titleRes)} — ${context.getString(check.detailRes)}"
    }

private fun Context.copyReport(report: String) {
    val label = getString(R.string.diagnostics_title)
    getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(label, report))
}

@Preview(showBackground = true)
@Composable
private fun DiagnosticsScreenPreview() {
    FServerTheme {
        DiagnosticsScreen(
            state = DiagnosticsState(checks = SampleData.diagnosticChecks),
            onIntent = {},
            navigateUp = {},
        )
    }
}
