package com.fserver.app.presentation.composable

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.RequirementRowUi
import com.fserver.app.presentation.composable.model.firstAction
import com.fserver.app.presentation.composable.model.toRows
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkSurfacePreview
import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.permission.RequirementAction
import com.fserver.app.presentation.permission.RequirementResolver
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.requirement.Requirement
import com.fserver.core.requirement.RequirementReport

/**
 * What an action is still waiting on, over whatever screen asked for it.
 *
 * The screen-level counterpart of the per-method sheet on the Connect screen: same rows, but
 * raised by a failure rather than opened on purpose, so it names what broke first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequirementsSheet(
    report: RequirementReport,
    resolver: RequirementResolver?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: UiText = UiText.of(R.string.error_requirements_title),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        RequirementsSheetContent(
            title = title.asString(),
            solvable = report.solvable.toRows(),
            blockers = report.blockers.toRows(),
            firstAction = report.firstAction,
            resolver = resolver,
        )
    }
}

/**
 * The body of a requirements sheet: unmet rows and the buttons that clear them.
 *
 * Only unmet requirements appear — `RequirementReport` reports nothing else, and a list of things
 * already granted would be reassurance rather than information.
 */
@Composable
fun RequirementsSheetContent(
    title: String,
    solvable: List<RequirementRowUi>,
    blockers: List<RequirementRowUi>,
    firstAction: RequirementAction?,
    resolver: RequirementResolver?,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DkSpacing.screenPadding)
            .padding(bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        description?.let { DkCaption(text = it) }

        if (solvable.isNotEmpty()) {
            DkSectionLabel(
                text = stringResource(R.string.method_requirements_label),
                trailing = {
                    DkCaption(
                        text = pluralStringResource(
                            R.plurals.method_requirements_left,
                            solvable.size,
                            solvable.size,
                        )
                    )
                },
            )

            solvable.forEachIndexed { index, row ->
                RequirementRow(row = row, onGrant = { resolver?.resolve(it) })
                if (index != solvable.lastIndex) {
                    DkFadingDivider()
                }
            }
        }

        if (blockers.isNotEmpty()) {
            DkSectionLabel(text = stringResource(R.string.method_blockers_label))

            blockers.forEach { row ->
                RequirementRow(row = row, onGrant = null)
            }
        }

        if (solvable.isNotEmpty()) {
            DkInfoBox(text = stringResource(R.string.method_setup_note))

            DkPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.method_grant_rest),
                enabled = firstAction != null,
                onClick = { firstAction?.let { resolver?.resolve(it) } },
            )
        }
    }
}

@Composable
fun RequirementRow(
    row: RequirementRowUi,
    onGrant: ((RequirementAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val action = row.action
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = if (row.resolvable) {
                Icons.Default.RadioButtonUnchecked
            } else {
                Icons.Default.ErrorOutline
            },
            contentDescription = null,
            tint = if (row.resolvable) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(row.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DkCaption(text = stringResource(row.detailRes))
        }
        if (action != null && onGrant != null) {
            DkSecondaryButton(
                text = stringResource(R.string.method_grant),
                onClick = { onGrant(action) },
            )
        }
    }
}

@SuppressLint("InlinedApi")
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RequirementsSheetContentPreview() {
    FServerTheme {
        DkSurfacePreview {
            RequirementsSheetContent(
                title = stringResource(R.string.error_requirements_title),
                solvable = listOf(
                    Requirement.RuntimePermission(
                        listOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    )
                ).toRows(),
                blockers = emptyList(),
                firstAction = RequirementAction.RequestPermissions(
                    listOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
                ),
                resolver = null,
            )
        }
    }
}
