package com.fserver.app.presentation.designkit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fserver.app.presentation.theme.FServerTheme

/**
 * Nocturne card: a surface block with an optional accent kicker above a heading.
 * [outlined] swaps the fill for an accent hairline — the deck uses that variant to mark
 * a row that needs attention (an interrupted transfer) without turning it into an error.
 * Passing [onClick] makes the whole card a target, which is how the connection screen
 * offers its three ways of reaching a server.
 */
@Composable
fun DkCard(
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(
        containerColor = if (outlined) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val border = if (outlined) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer)
    } else {
        null
    }
    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(DkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            content = content,
        )
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
            content = body,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
            content = body,
        )
    }
}

@Composable
fun DkCardKicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
fun DkCardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Small technical line under a card body — sizes, speeds, states. */
@Composable
fun DkCardMeta(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        Text(
            text = text,
            style = DkType.mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                trailing()
            }
        }
    }
}

@Preview
@Composable
private fun DkCardPreview() {
    FServerTheme {
        DkSurfacePreview {
            Column(
                modifier = Modifier.padding(DkSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
            ) {
                DkCard {
                    DkCardKicker("Server")
                    DkCardTitle("MacBook-Pro.local")
                    DkCardMeta("192.168.1.14:8384 · TLS 1.3 · protocol v1")
                }
                DkCard(outlined = true) {
                    DkCardTitle("IMG_4830.RAW")
                    DkCardMeta("Stopped at 74 % · connection lost")
                }
            }
        }
    }
}
