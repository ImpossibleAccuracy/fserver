package com.fserver.app.presentation.screens.source.setup.pick

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.source.setup.pick.model.SourcePickIntent
import com.fserver.app.presentation.screens.source.setup.pick.model.SourcePickState
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceOptionCard
import com.fserver.app.presentation.screens.source.shared.model.icon
import com.fserver.app.presentation.screens.source.shared.model.subtitleRes
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun SourcePickScreen(
    handler: SourcePickHandler,
    navigateToAccess: (SourceKindUi) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by handler.state.collectAsStateWithLifecycle()

    SourcePickScreenContent(
        state = state,
        onIntent = handler::onIntent,
        navigateToAccess = navigateToAccess,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SourcePickScreenContent(
    state: SourcePickState,
    onIntent: (SourcePickIntent) -> Unit,
    navigateToAccess: (SourceKindUi) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.source_pick_title),
                onBack = navigateUp,
            )
        },
        bottomBar = {
            DkCaption(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(
                        horizontal = DkSpacing.screenPadding,
                        vertical = DkSpacing.md,
                    ),
                text = stringResource(R.string.source_pick_footer),
                textAlign = TextAlign.Center,
            )
        },
    ) { innerPadding ->
        val blockPadding = Modifier.padding(horizontal = DkSpacing.screenPadding)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            Text(
                modifier = blockPadding,
                text = stringResource(R.string.source_pick_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.primary.forEach { kind ->
                SourceOptionCard(
                    modifier = blockPadding,
                    icon = kind.icon,
                    title = stringResource(kind.titleRes),
                    description = stringResource(kind.subtitleRes),
                    onClick = { navigateToAccess(kind) },
                )
            }

            MoreDisclosure(
                expanded = state.moreExpanded,
                onClick = { onIntent(SourcePickIntent.MoreToggled) },
            )

            AnimatedVisibility(visible = state.moreExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.md)) {
                    state.behindMore.forEach { kind ->
                        SourceOptionCard(
                            modifier = blockPadding,
                            icon = kind.icon,
                            title = stringResource(kind.titleRes),
                            description = stringResource(kind.subtitleRes),
                            onClick = { navigateToAccess(kind) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreDisclosure(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.action_more),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DkIcon(
            icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SourcePickScreenPreview() {
    FServerTheme {
        SourcePickScreenContent(
            state = SourcePickState(),
            onIntent = {},
            navigateToAccess = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "More expanded", showBackground = true)
@Composable
private fun SourcePickScreenExpandedPreview() {
    FServerTheme {
        SourcePickScreenContent(
            state = SourcePickState(moreExpanded = true),
            onIntent = {},
            navigateToAccess = {},
            navigateUp = {},
        )
    }
}
