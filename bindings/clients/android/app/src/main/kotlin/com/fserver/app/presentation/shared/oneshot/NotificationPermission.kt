package com.fserver.app.presentation.shared.oneshot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for POST_NOTIFICATIONS where it is a runtime permission, at a moment a transfer is about to
 * start. The answer is not waited on: without it the transfer runs, only unseen.
 */
@Composable
fun rememberNotificationPermissionRequest(): Runnable {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return remember { Runnable {} }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    return remember(context, launcher) {
        Runnable {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
