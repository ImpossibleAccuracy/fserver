package com.fserver.app.presentation.screens.files.composable

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.theme.FServerTheme

/**
 * Path from the top of the tree to the opened folder. Every crumb but the last is a jump back up;
 * the row keeps the last one in view as it grows.
 */
@Composable
fun Breadcrumbs(
    modifier: Modifier = Modifier,
    rootLabel: String,
    crumbs: List<String>,
    onRootClick: () -> Unit,
    onCrumbClick: (index: Int) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(crumbs.size) {
        listState.animateScrollToItem(crumbs.size)
    }

    LazyRow(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = DkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Crumb(text = rootLabel, current = crumbs.isEmpty(), onClick = onRootClick)
        }
        itemsIndexed(crumbs) { index, crumb ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, size = 16.dp)
                Crumb(
                    text = crumb,
                    current = index == crumbs.lastIndex,
                    onClick = { onCrumbClick(index) },
                )
            }
        }
    }
}

@Composable
private fun Crumb(text: String, current: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !current) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (current) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.primary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun BreadcrumbsPreview() {
    FServerTheme {
        Breadcrumbs(
            rootLabel = "Files",
            crumbs = listOf("DCIM", "Camera", "2026"),
            onRootClick = {},
            onCrumbClick = {},
        )
    }
}
