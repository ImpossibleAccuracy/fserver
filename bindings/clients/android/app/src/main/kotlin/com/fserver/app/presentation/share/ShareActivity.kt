package com.fserver.app.presentation.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.fserver.app.R
import com.fserver.app.presentation.composable.ObserveEffects
import com.fserver.app.presentation.share.model.ShareUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Entry point of `ACTION_SEND` / `ACTION_SEND_MULTIPLE`. Its own activity rather than the main one,
 * whose view model runs the node for the UI. The share grant lives as long as this activity, so it
 * stays until `:core` has copied the files.
 */
class ShareActivity : ComponentActivity() {
    private val viewModel: ShareViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val uris = intent.sharedUris()
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.share_nothing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        viewModel.setUris(uris)

        setContent {
            FServerTheme {
                ObserveEffects(viewModel.uiEffects) { effect ->
                    when (effect) {
                        is ShareUiEffect.Offered -> {
                            val text = resources.getQuantityString(
                                R.plurals.share_offered, effect.count, effect.count, effect.deviceName,
                            )
                            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }

                ShareScreen(viewModel = viewModel, onClose = ::finish)
            }
        }
    }
}

private fun Intent.sharedUris(): List<Uri> {
    val extras = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
    if (extras.isNotEmpty()) return extras.distinct()

    val clip = clipData ?: return emptyList()
    return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.distinct()
}
