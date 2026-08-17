package com.fserver.app.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkNavigationRow
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.theme.FServerTheme

/**
 * Settings root.
 *
 * Four rows, no state: every line either leads further in or does not belong here. The supporting
 * line describes what the screen behind it covers — never a count or a value, because a root that
 * reports state is a root the user has to come back to for it.
 */
@Composable
fun SettingsScreen(
    navigateToDevices: () -> Unit,
    navigateToSecurity: () -> Unit,
    navigateToDiagnostics: () -> Unit,
    navigateToAbout: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { DkTopBar(title = stringResource(R.string.settings_title)) },
    ) { innerPadding ->
        // Rows carry the gutter themselves, so only the labels between them need one.
        val gutter = Modifier.padding(horizontal = DkSpacing.screenPadding)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            DkSectionLabel(
                modifier = gutter,
                text = stringResource(R.string.settings_section_connection),
            )

            DkNavigationRow(
                title = stringResource(R.string.settings_devices),
                supportingText = stringResource(R.string.settings_devices_desc),
                onClick = navigateToDevices,
            )
            DkFadingDivider()

            DkNavigationRow(
                title = stringResource(R.string.settings_security),
                supportingText = stringResource(R.string.settings_security_desc),
                onClick = navigateToSecurity,
            )

            DkSectionLabel(
                modifier = gutter,
                text = stringResource(R.string.settings_section_other),
            )

            DkNavigationRow(
                title = stringResource(R.string.settings_diagnostics),
                supportingText = stringResource(R.string.settings_diagnostics_desc),
                onClick = navigateToDiagnostics,
            )
            DkFadingDivider()

            DkNavigationRow(
                title = stringResource(R.string.settings_about),
                supportingText = stringResource(R.string.settings_about_desc),
                onClick = navigateToAbout,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SettingsScreenPreview() {
    FServerTheme {
        SettingsScreen(
            navigateToDevices = {},
            navigateToSecurity = {},
            navigateToDiagnostics = {},
            navigateToAbout = {},
        )
    }
}
