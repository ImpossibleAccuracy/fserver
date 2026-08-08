package com.fserver.app.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkNavigationRow
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSettingsRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkSwitchRow
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkValueRow
import com.fserver.app.presentation.screens.settings.model.SettingsIntent
import com.fserver.app.presentation.screens.settings.model.SettingsState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    navigateToDevices: () -> Unit,
    navigateToTrustedFingerprints: () -> Unit,
    navigateToDiagnostics: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToDevices = navigateToDevices,
        navigateToTrustedFingerprints = navigateToTrustedFingerprints,
        navigateToDiagnostics = navigateToDiagnostics,
    )
}

/**
 * Settings.
 *
 * The encryption row states the threat model in the row itself rather than hiding it in
 * help: the server holds the keys, so this protects against a stolen drive, not against
 * the server. It is reported, not toggled — the client does not get to claim it enforces
 * something the server owns.
 */
@Composable
private fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    navigateToDevices: () -> Unit,
    navigateToTrustedFingerprints: () -> Unit,
    navigateToDiagnostics: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { DkTopBar(title = stringResource(R.string.settings_title)) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DkSpacing.screenPadding),
        ) {
            DkSectionLabel(text = stringResource(R.string.settings_section_connection))

            DkNavigationRow(
                title = stringResource(R.string.settings_devices),
                value = state.deviceCount.toString(),
                onClick = navigateToDevices,
            )
            DkFadingDivider()

            DkSwitchRow(
                title = stringResource(R.string.settings_wifi_only),
                checked = state.wifiOnly,
                onCheckedChange = { onIntent(SettingsIntent.WifiOnlyChanged(it)) },
            )
            DkFadingDivider()

            DkSwitchRow(
                title = stringResource(R.string.settings_compress),
                checked = state.compressOnTheFly,
                onCheckedChange = { onIntent(SettingsIntent.CompressChanged(it)) },
            )
            DkFadingDivider()

            DkSectionLabel(text = stringResource(R.string.settings_section_security))

            DkSettingsRow(
                title = stringResource(R.string.settings_storage_encryption),
                supportingText = stringResource(R.string.settings_storage_encryption_desc),
                trailing = {
                    if (state.storageEncryptionOn) {
                        DkTag(stringResource(R.string.settings_value_on), style = DkTagStyle.Accent)
                    }
                },
            )
            DkFadingDivider()

            DkNavigationRow(
                title = stringResource(R.string.settings_trusted_fingerprints),
                onClick = navigateToTrustedFingerprints,
            )
            DkFadingDivider()

            DkSectionLabel(text = stringResource(R.string.settings_section_other))

            DkValueRow(
                title = stringResource(R.string.settings_download_folder),
                value = state.downloadFolder,
            )
            DkFadingDivider()

            DkNavigationRow(
                title = stringResource(R.string.settings_diagnostics),
                onClick = navigateToDiagnostics,
                accented = true,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SettingsScreenPreview() {
    FServerTheme {
        SettingsScreen(
            state = SettingsState(
                deviceCount = 5,
                downloadFolder = SampleData.DOWNLOAD_FOLDER,
            ),
            onIntent = {},
            navigateToDevices = {},
            navigateToTrustedFingerprints = {},
            navigateToDiagnostics = {},
        )
    }
}
