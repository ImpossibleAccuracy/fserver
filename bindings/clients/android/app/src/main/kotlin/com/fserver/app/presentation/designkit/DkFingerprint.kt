package com.fserver.app.presentation.designkit

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.theme.FServerTheme

/**
 * The server key fingerprint, grouped two columns wide so it can be read against the
 * screen of the other device. This block is the only defence against a substituted
 * server, so it is drawn large, monospaced and never truncated.
 *
 * [groups] are the pre-split chunks (e.g. `9f2c 4a01`); the caller owns the grouping.
 */
@Composable
fun DkFingerprintBlock(
    groups: List<String>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
            .padding(14.dp)
            .semantics { contentDescription = groups.joinToString(" ") },
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        groups.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                row.forEach { group ->
                    Text(
                        text = group,
                        style = DkType.monoFingerprint,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Text("", modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Preview
@Composable
private fun DkFingerprintPreview() {
    FServerTheme {
        DkSurfacePreview {
            DkFingerprintBlock(
                groups = listOf("9f2c 4a01", "b7d3 e820", "15aa cc94", "0f6b 7e31"),
                modifier = Modifier.padding(DkSpacing.lg),
            )
        }
    }
}
