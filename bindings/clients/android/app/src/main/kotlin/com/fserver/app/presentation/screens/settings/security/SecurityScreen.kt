package com.fserver.app.presentation.screens.settings.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSettingsRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkSwitch
import com.fserver.app.presentation.designkit.DkSwitchRow
import com.fserver.app.presentation.designkit.DkTextField
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.settings.security.model.SecurityIntent
import com.fserver.app.presentation.screens.settings.security.model.SecurityState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel = koinViewModel(),
    navigateToPinChange: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SecurityScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToPinChange = navigateToPinChange,
        navigateUp = navigateUp,
    )
}

/** What a dialog is currently editing. Kept in the composition — none of it outlives the screen. */
private enum class SecurityEditor { None, DeviceName, ServerPassword }

/**
 * Visibility above, confirmation methods below.
 *
 * "Change" appears only where there is something to edit, and a switch that would close the last
 * peer method refuses instead of leaving the phone unreachable.
 */
@Composable
private fun SecurityScreen(
    state: SecurityState,
    onIntent: (SecurityIntent) -> Unit,
    navigateToPinChange: () -> Unit,
    navigateUp: () -> Unit,
) {
    var editor by remember { mutableStateOf(SecurityEditor.None) }

    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.security_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        // Rows carry the gutter themselves; labels and the warning box get it explicitly.
        val gutter = Modifier.padding(horizontal = DkSpacing.screenPadding)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            DkSectionLabel(
                modifier = gutter,
                text = stringResource(R.string.security_section_visibility),
            )

            DkSwitchRow(
                title = stringResource(R.string.security_discoverable),
                supportingText = stringResource(R.string.security_discoverable_desc),
                checked = state.isDiscoverable,
                onCheckedChange = { onIntent(SecurityIntent.DiscoverableChanged(it)) },
            )
            DkFadingDivider()

            DkSwitchRow(
                title = stringResource(R.string.security_discovery),
                supportingText = stringResource(R.string.security_discovery_desc),
                checked = state.isDiscoveryEnabled,
                onCheckedChange = { onIntent(SecurityIntent.DiscoveryChanged(it)) },
            )
            DkFadingDivider()

            DkSettingsRow(
                title = stringResource(R.string.security_device_name),
                supportingText = state.deviceName.ifEmpty {
                    stringResource(R.string.security_device_name_desc)
                },
                trailing = {
                    ChangeButton(onClick = { editor = SecurityEditor.DeviceName })
                },
            )

            DkSectionLabel(
                modifier = gutter,
                text = stringResource(R.string.security_section_methods),
            )

            if (state.showLastMethodWarning) {
                DkInfoBox(
                    text = stringResource(R.string.security_last_method_warning),
                    modifier = gutter.padding(bottom = DkSpacing.sm),
                )
            }

            DkSwitchRow(
                title = stringResource(R.string.auth_method_confirm_fingerprint),
                supportingText = stringResource(R.string.security_method_sas_desc),
                checked = state.isCodeComparison,
                onCheckedChange = { onIntent(SecurityIntent.CodeComparisonChanged(it)) },
            )
            DkFadingDivider()

            DkSettingsRow(
                title = stringResource(R.string.auth_method_password),
                supportingText = stringResource(R.string.security_method_password_desc),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChangeButton(onClick = { editor = SecurityEditor.ServerPassword })
                        DkSwitch(
                            checked = state.isServerPassword,
                            onCheckedChange = { onIntent(SecurityIntent.ServerPasswordChanged(it)) },
                        )
                    }
                },
            )
            DkFadingDivider()

            DkSettingsRow(
                title = stringResource(R.string.security_method_pin),
                supportingText = stringResource(R.string.security_method_pin_desc),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChangeButton(onClick = navigateToPinChange)
                        DkSwitch(
                            checked = state.isPinEnabled,
                            onCheckedChange = { onIntent(SecurityIntent.PinChanged(it)) },
                        )
                    }
                },
            )
            DkFadingDivider()

            // Nothing to stand in for while there is no PIN, so the switch stays shut.
            DkSwitchRow(
                title = stringResource(R.string.security_method_biometric),
                supportingText = stringResource(R.string.security_method_biometric_desc),
                checked = state.isBiometricUnlock,
                enabled = state.isPinEnabled,
                onCheckedChange = { onIntent(SecurityIntent.BiometricChanged(it)) },
            )
            DkFadingDivider()

            DkSwitchRow(
                title = stringResource(R.string.security_method_qr),
                supportingText = stringResource(R.string.security_method_qr_desc),
                checked = state.isQrConnect,
                onCheckedChange = { onIntent(SecurityIntent.QrConnectChanged(it)) },
            )
        }
    }

    when (editor) {
        SecurityEditor.None -> Unit

        SecurityEditor.DeviceName -> TextEditorDialog(
            title = stringResource(R.string.security_rename_title),
            label = stringResource(R.string.security_device_name),
            initialValue = state.deviceName,
            onDismiss = { editor = SecurityEditor.None },
            onConfirm = {
                onIntent(SecurityIntent.DeviceRenamed(it))
                editor = SecurityEditor.None
            },
        )

        // Never pre-filled: the field is this device's own secret, and putting it back on screen
        // to be edited is one shoulder-surf away from giving it up.
        SecurityEditor.ServerPassword -> TextEditorDialog(
            title = stringResource(R.string.security_password_title),
            label = stringResource(R.string.auth_method_password),
            initialValue = "",
            hint = stringResource(R.string.security_password_hint),
            isPassword = true,
            onDismiss = { editor = SecurityEditor.None },
            onConfirm = {
                onIntent(SecurityIntent.ServerPasswordSet(it))
                editor = SecurityEditor.None
            },
        )
    }
}

@Composable
private fun ChangeButton(onClick: () -> Unit) {
    DkGhostButton(
        text = stringResource(R.string.security_action_change),
        onClick = onClick,
    )
}

@Composable
private fun TextEditorDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    hint: String? = null,
    isPassword: Boolean = false,
) {
    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.md)) {
                if (hint != null) DkInfoBox(text = hint)
                DkTextField(
                    label = label,
                    value = value,
                    onValueChange = { value = it },
                    isPassword = isPassword,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SecurityScreenPreview() {
    FServerTheme {
        SecurityScreen(
            state = SecurityState(
                isDiscoverable = true,
                isDiscoveryEnabled = true,
                deviceName = "Pixel 8",
                isCodeComparison = true,
                isServerPassword = true,
                isPinEnabled = true,
                isBiometricUnlock = false,
                isQrConnect = true,
            ),
            onIntent = {},
            navigateToPinChange = {},
            navigateUp = {},
        )
    }
}
