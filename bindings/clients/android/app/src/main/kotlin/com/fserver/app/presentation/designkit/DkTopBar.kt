package com.fserver.app.presentation.designkit

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.fserver.app.R

val DKTopBarColors: TopAppBarColors
    @Composable
    get() = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

val DKTransparentTopBarColors: TopAppBarColors
    @Composable
    get() = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

/**
 * Flat top bar on the screen ground — Nocturne draws no bar fill and no bottom rule,
 * the title simply sits at the top of the content column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DkTopBar(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    colors: TopAppBarColors = DKTopBarColors,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                AnimatedContent(
                    targetState = subtitle,
                    contentKey = { it != null },
                ) { subtitle ->
                    if (subtitle != null) {
                        DkMonoCaption(
                            modifier = Modifier.padding(top = DkSpacing.xs),
                            text = subtitle
                        )
                    }
                }
            }
        },
        colors = colors,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        actions = actions,
    )
}
