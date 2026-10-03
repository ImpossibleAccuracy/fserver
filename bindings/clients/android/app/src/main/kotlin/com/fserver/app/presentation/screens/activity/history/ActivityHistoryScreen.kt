package com.fserver.app.presentation.screens.activity.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.activity.history.composable.HistoryFilterSheet
import com.fserver.app.presentation.screens.activity.history.model.ActivityHistoryIntent
import com.fserver.app.presentation.screens.activity.history.model.ActivityHistoryState
import com.fserver.app.presentation.shared.journal.composable.JournalEntryRow
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ActivityHistoryScreen(
    modifier: Modifier = Modifier,
    key: Destination.Activity.History,
    viewModel: ActivityHistoryViewModel = koinViewModel { parametersOf(key) },
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ActivityHistoryScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

@Composable
private fun ActivityHistoryScreenContent(
    modifier: Modifier = Modifier,
    state: ActivityHistoryState,
    onIntent: (ActivityHistoryIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }

    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.journal_history_title),
                onBack = navigateUp,
                actions = {
                    FilterAction(isFiltered = state.isFiltered, onClick = { showFilters = true })
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isEmpty) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(horizontal = DkSpacing.screenPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        DkCaption(
                            text = stringResource(R.string.journal_history_empty),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            state.days.forEach { day ->
                item(key = "day-${day.date}") {
                    DkSectionLabel(
                        modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                        text = day.date.label(),
                    )
                }
                items(day.entries, key = { it.id }) { entry ->
                    JournalEntryRow(
                        entry = entry,
                        onDismiss = { onIntent(ActivityHistoryIntent.DismissClicked(entry.id)) }
                            .takeIf { entry.isOpenIssue },
                    )
                    DkFadingDivider()
                }
            }
        }
    }

    if (showFilters) {
        HistoryFilterSheet(
            groups = state.groups,
            sources = state.sources,
            sourceIds = state.sourceIds,
            devices = state.devices,
            deviceIds = state.deviceIds,
            onApply = { groups, sourceIds, deviceIds ->
                onIntent(ActivityHistoryIntent.FiltersApplied(groups, sourceIds, deviceIds))
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun FilterAction(
    modifier: Modifier = Modifier,
    isFiltered: Boolean,
    onClick: () -> Unit,
) {
    IconButton(modifier = modifier, onClick = onClick) {
        BadgedBox(badge = { if (isFiltered) Badge() }) {
            Icon(
                imageVector = Icons.Default.FilterAlt,
                contentDescription = stringResource(R.string.journal_filter_title),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun LocalDate.label(): String = DateUtils.getRelativeTimeSpanString(
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    System.currentTimeMillis(),
    DateUtils.DAY_IN_MILLIS,
).toString()

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ActivityHistoryScreenPreview() {
    FServerTheme {
        ActivityHistoryScreenContent(
            state = ActivityHistoryState.Sample,
            onIntent = {},
            navigateUp = {},
        )
    }
}
