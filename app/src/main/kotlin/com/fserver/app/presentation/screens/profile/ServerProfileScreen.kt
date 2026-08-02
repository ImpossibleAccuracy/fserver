package com.fserver.app.presentation.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardKicker
import com.fserver.app.presentation.designkit.DkCardTitle
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTextField
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.screens.profile.model.ServerProfileIntent
import com.fserver.app.presentation.screens.profile.model.ServerProfileState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ServerProfileScreen(
    viewModel: ServerProfileViewModel = koinViewModel(),
    navigateToFiles: () -> Unit,
    navigateToManualEditor: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ServerProfileScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToFiles = navigateToFiles,
        navigateToManualEditor = navigateToManualEditor,
        navigateUp = navigateUp,
    )
}

/**
 * What the scanned code turned out to contain, shown before anything is applied.
 *
 * The profile is presented as the server's claim, not as settled fact — the user can
 * still override it manually. Everything the code asserted is listed in the open,
 * including that the fingerprint check already passed.
 */
@Composable
private fun ServerProfileScreen(
    state: ServerProfileState,
    onIntent: (ServerProfileIntent) -> Unit,
    navigateToFiles: () -> Unit,
    navigateToManualEditor: () -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.profile_title),
                onBack = navigateUp,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.padding(DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkPrimaryButton(
                    text = stringResource(R.string.profile_apply),
                    onClick = navigateToFiles,
                    modifier = Modifier.fillMaxWidth(),
                )
                DkGhostButton(
                    text = stringResource(R.string.profile_edit_manually),
                    onClick = navigateToManualEditor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            DkCard {
                DkCardKicker(stringResource(R.string.profile_kicker_from_code))
                DkCardTitle(state.profile.deviceName)
                Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.profile_address, state.profile.address),
                        style = DkType.monoLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.profile_access_password),
                        style = DkType.monoLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.profile.fingerprintVerified) {
                        Text(
                            text = stringResource(R.string.profile_fingerprint_verified),
                            style = DkType.monoLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            DkTextField(
                label = stringResource(R.string.profile_password_label),
                value = state.password,
                onValueChange = { onIntent(ServerProfileIntent.PasswordChanged(it)) },
                isPassword = true,
            )

            DkInfoBox(text = stringResource(R.string.profile_expiry_note))
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ServerProfileScreenPreview() {
    FServerTheme {
        ServerProfileScreen(
            state = ServerProfileState(profile = SampleData.scannedProfile),
            onIntent = {},
            navigateToFiles = {},
            navigateToManualEditor = {},
            navigateUp = {},
        )
    }
}
