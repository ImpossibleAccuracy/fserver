package com.fserver.app.presentation.screens.settings.storage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun StorageScreen(
    modifier: Modifier = Modifier,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.storage_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        DkPlaceholderBox(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.lg)
                .fillMaxWidth()
                .height(240.dp),
            label = stringResource(R.string.storage_placeholder),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun StorageScreenPreview() {
    FServerTheme {
        StorageScreen(navigateUp = {})
    }
}
