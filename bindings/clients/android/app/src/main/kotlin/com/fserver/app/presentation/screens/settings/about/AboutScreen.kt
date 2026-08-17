package com.fserver.app.presentation.screens.settings.about

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.fserver.app.R
import com.fserver.app.presentation.composable.LocalSnackbarController
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkNavigationRow
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.theme.FServerTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * A static page.
 *
 * Every row below the version block is a placeholder: there is no update channel, no issue tracker
 * link and no licence report in this build. They are drawn rather than dropped so the screen keeps
 * the shape it will have, and each one says so when tapped.
 */
@Composable
fun AboutScreen(
    navigateUp: () -> Unit,
) {
    val snackbar = LocalSnackbarController.current
    val placeholder = stringResource(R.string.about_placeholder)

    AboutScreen(
        navigateUp = navigateUp,
        onPlaceholderClick = { snackbar.showSnackbar(placeholder) },
    )
}

@Composable
private fun AboutScreen(
    navigateUp: () -> Unit,
    onPlaceholderClick: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember(context) { context.versionLine() }

    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.about_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        // Rows carry the gutter themselves; the header and footer get it explicitly.
        val gutter = Modifier.padding(horizontal = DkSpacing.screenPadding)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = gutter
                    .fillMaxWidth()
                    .padding(top = DkSpacing.lg, bottom = DkSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkPlaceholderBox(
                    label = stringResource(R.string.about_logo),
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DkMonoCaption(text = version)
            }

            val rows = listOf(
                R.string.about_check_updates,
                R.string.about_source,
                R.string.about_report,
                R.string.about_licenses,
                R.string.about_privacy,
            )

            rows.forEachIndexed { index, row ->
                DkNavigationRow(
                    title = stringResource(row),
                    onClick = onPlaceholderClick,
                )
                if (index != rows.lastIndex) DkFadingDivider()
            }

            // Same voice as `DkCaption`, centred — the deck closes the page on this line.
            Text(
                modifier = gutter
                    .fillMaxWidth()
                    .padding(top = DkSpacing.xl, bottom = DkSpacing.lg),
                text = stringResource(R.string.about_footer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Protocol version is part of the string resource: `:core` does not publish one yet. */
private fun Context.versionLine(): String {
    val info = packageManager.getPackageInfo(packageName, 0)
    return getString(
        R.string.about_version,
        info.versionName.orEmpty(),
        PackageInfoCompat.getLongVersionCode(info),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun AboutScreenPreview() {
    FServerTheme {
        AboutScreen(navigateUp = {}, onPlaceholderClick = {})
    }
}
