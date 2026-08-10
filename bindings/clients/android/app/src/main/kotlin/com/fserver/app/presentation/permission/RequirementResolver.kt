package com.fserver.app.presentation.permission

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.GoogleApiAvailability
import timber.log.Timber

/**
 * Runs a [RequirementAction] and reports back when the user is done with it.
 *
 * [onResolved] fires after every attempt, granted or not: what the user actually did is only
 * knowable by checking the requirement again, so nothing here tries to guess the outcome.
 */
@Composable
fun rememberRequirementResolver(onResolved: () -> Unit): RequirementResolver {
    val activity = LocalActivity.current
    val currentOnResolved by rememberUpdatedState(onResolved)

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { currentOnResolved() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        currentOnResolved()

        // An empty result is a dialog the user backed out of, which says nothing about whether
        // asking again would work.
        if (grants.isEmpty() || activity == null) return@rememberLauncherForActivityResult

        // Denying twice retires the request for good: every later launch returns the same denial
        // without showing anything. The app's own settings page is the only way left, so the user
        // goes there instead of tapping a button that no longer does anything.
        val retired = grants.any { (permission, granted) ->
            !granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        if (retired) settingsLauncher.launchSafely(appDetailsIntent(activity))
    }

    return remember(activity, permissionLauncher, settingsLauncher) {
        RequirementResolver(
            activity = activity,
            permissionLauncher = permissionLauncher,
            settingsLauncher = settingsLauncher,
            onResolved = { currentOnResolved() },
        )
    }
}

@Stable
class RequirementResolver internal constructor(
    private val activity: Activity?,
    private val permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    private val settingsLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    private val onResolved: () -> Unit,
) {
    fun resolve(action: RequirementAction) {
        when (action) {
            is RequirementAction.RequestPermissions ->
                permissionLauncher.launch(action.permissions.toTypedArray())

            is RequirementAction.OpenSettings ->
                settingsLauncher.launchSafely(Intent(action.intentAction))

            RequirementAction.ResolvePlayServices -> resolvePlayServices()
        }
    }

    /**
     * Play services knows what its own status code means — missing, disabled, too old — so it is
     * asked to repair itself rather than the app reimplementing that decision.
     */
    private fun resolvePlayServices() {
        val activity = activity ?: return
        GoogleApiAvailability.getInstance()
            .makeGooglePlayServicesAvailable(activity)
            .addOnCompleteListener { onResolved() }
    }
}

private fun appDetailsIntent(activity: Activity) = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", activity.packageName, null),
)

/**
 * A settings screen is not guaranteed to exist on every build of Android, and a missing one is a
 * requirement the user has to solve their own way — not a crash.
 */
private fun ManagedActivityResultLauncher<Intent, ActivityResult>.launchSafely(intent: Intent) {
    try {
        launch(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No activity handles %s", intent.action)
    }
}
