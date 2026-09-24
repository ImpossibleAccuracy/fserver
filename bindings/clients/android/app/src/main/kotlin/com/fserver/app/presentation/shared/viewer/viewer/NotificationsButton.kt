package com.fserver.app.presentation.shared.viewer.viewer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fserver.app.R

/**
 * Asks for the notifications that background playback needs; shown only while they are off.
 * Once the system stops showing the prompt, a tap opens the app's notification settings instead.
 */
@Composable
internal fun NotificationsButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var isEnabled by remember { mutableStateOf(context.notificationsEnabled()) }
    var rationaleBefore by remember { mutableStateOf(false) }

    LifecycleResumeEffect(context) {
        isEnabled = context.notificationsEnabled()
        onPauseOrDispose {}
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        isEnabled = context.notificationsEnabled()
        // No rationale before or after a denial: the system refused without showing the prompt.
        if (!granted && !rationaleBefore && activity?.showsRationale() == false) {
            context.openNotificationSettings()
        }
    }

    if (isEnabled) return

    IconButton(
        modifier = modifier,
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasPermission()) {
                rationaleBefore = activity?.showsRationale() == true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openNotificationSettings()
            }
        },
    ) {
        Icon(
            imageVector = Icons.Default.NotificationsOff,
            contentDescription = stringResource(R.string.file_viewer_enable_notifications),
            tint = Color.White,
        )
    }
}

private fun Context.notificationsEnabled(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

private fun Context.hasPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Activity.showsRationale(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)

private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
