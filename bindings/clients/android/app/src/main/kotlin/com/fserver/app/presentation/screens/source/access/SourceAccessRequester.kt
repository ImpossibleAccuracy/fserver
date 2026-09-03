package com.fserver.app.presentation.screens.source.access

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.fserver.app.presentation.screens.source.shared.model.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import timber.log.Timber

/** What Android handed over for one source branch. */
sealed interface SourceAccessGrant {
    val accessType: SourceAccessUi

    /** The gallery or any other media */
    data class Media(val access: SourceAccessUi) : SourceAccessGrant {
        override val accessType: SourceAccessUi
            get() = access
    }

    /** One SAF tree */
    data class Tree(val uri: Uri, val label: String) : SourceAccessGrant {
        override val accessType: SourceAccessUi
            get() = SourceAccessUi.Full
    }

    /** The whole device, or at least the parts Android lets the app see. */
    data object AllFiles : SourceAccessGrant {
        override val accessType: SourceAccessUi
            get() = SourceAccessUi.Full
    }

    /**
     * The app's own private storage. Nothing is asked of Android for it.
     *
     * TODO: remove before production, along with [SourceKindUi.AppStorage].
     */
    data object Internal : SourceAccessGrant {
        override val accessType: SourceAccessUi
            get() = SourceAccessUi.Full
    }

    data object Denied : SourceAccessGrant {
        override val accessType: SourceAccessUi
            get() = SourceAccessUi.Full
    }
}

/** The one bucket the development source reads. TODO: remove before production. */
const val DevSourceBucket: String = "debug"

/** Raises the system dialog a branch needs and reports what came back. */
@Composable
fun rememberSourceAccessRequester(
    onGrant: (SourceAccessGrant) -> Unit,
): SourceAccessRequester {
    val context = LocalContext.current
    val currentOnGrant by rememberUpdatedState(onGrant)

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        currentOnGrant(uri?.let { context.persistTree(it) } ?: SourceAccessGrant.Denied)
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { currentOnGrant(context.mediaGrant()) }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        currentOnGrant(
            if (grants.values.all { it }) SourceAccessGrant.AllFiles else SourceAccessGrant.Denied
        )
    }

    // The all-files toggle reports nothing back through the result, so the state is re-read on
    // return whether the user flipped it or walked away.
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        currentOnGrant(
            if (hasAllFilesAccess()) {
                SourceAccessGrant.AllFiles
            } else {
                SourceAccessGrant.Denied
            }
        )
    }

    return remember(context, treeLauncher, mediaLauncher, legacyStorageLauncher, allFilesLauncher) {
        SourceAccessRequester(
            context = context,
            onGrant = { currentOnGrant(it) },
            requestTree = { treeLauncher.launch(null) },
            requestMedia = { mediaLauncher.launch(it) },
            requestLegacyStorage = { legacyStorageLauncher.launch(it) },
            openAllFilesSettings = { allFilesLauncher.launchSafely(it) },
        )
    }
}

@Stable
class SourceAccessRequester internal constructor(
    private val context: Context,
    private val onGrant: (SourceAccessGrant) -> Unit,
    private val requestTree: () -> Unit,
    private val requestMedia: (Array<String>) -> Unit,
    private val requestLegacyStorage: (Array<String>) -> Unit,
    private val openAllFilesSettings: (Intent) -> Unit,
) {
    /**
     * Asks for what [kind] needs. Safe to call again after a denial: re-launching the media
     * request is also how a partial grant is widened, since that is the dialog Android reopens.
     */
    fun request(kind: SourceKindUi) {
        when (kind) {
            SourceKindUi.Media -> requestMedia()
            SourceKindUi.Folder -> requestTree()
            SourceKindUi.WholeDevice -> requestWholeDevice()
            SourceKindUi.AppStorage -> onGrant(SourceAccessGrant.Internal)
        }
    }

    private fun requestMedia() {
        if (context.mediaGrant() == SourceAccessGrant.Media(SourceAccessUi.Full)) {
            onGrant(SourceAccessGrant.Media(SourceAccessUi.Full))
        } else {
            requestMedia(mediaPermissions().toTypedArray())
        }
    }

    private fun requestWholeDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestLegacyStorage(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            return
        }

        if (hasAllFilesAccess()) {
            onGrant(SourceAccessGrant.AllFiles)
            return
        }

        openAllFilesSettings(
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null),
            )
        )
    }
}

/**
 * Permissions covering the gallery on this API level.
 *
 * From API 33 the storage permission is split per media type; from API 34 the user may answer
 * with a hand-picked subset, which arrives as its own permission. Requesting that one alongside
 * the others is what makes the subset answer possible at all.
 */
private fun mediaPermissions(): List<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )

    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * What the app holds over the gallery right now. Both visual types have to be granted for the
 * branch to call itself full — one of them missing is the same partial view as a hand-picked set.
 */
@SuppressLint("InlinedApi")
private fun Context.mediaGrant(): SourceAccessGrant {
    val full = mediaPermissions()
        .filterNot { it == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
        .all { holds(it) }

    return when {
        full -> SourceAccessGrant.Media(SourceAccessUi.Full)

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                holds(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ->
            SourceAccessGrant.Media(SourceAccessUi.Partial)

        else -> SourceAccessGrant.Denied
    }
}

private fun Context.holds(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun hasAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

/**
 * Takes the read grant for the whole tree so it outlives the process, and labels it by the
 * document id the provider already encodes — no query, and closer to what the user picked than
 * the raw URI.
 */
private fun Context.persistTree(uri: Uri): SourceAccessGrant {
    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

    val label = runCatching { DocumentsContract.getTreeDocumentId(uri) }
        .getOrNull()
        ?.substringAfter(':')
        ?.takeIf { it.isNotEmpty() }
        ?: uri.lastPathSegment.orEmpty()

    return SourceAccessGrant.Tree(uri = uri, label = "/$label")
}

/**
 * A settings screen is not guaranteed to exist on every build of Android, and a missing one is
 * something the user has to solve their own way — not a crash.
 */
private fun ManagedActivityResultLauncher<Intent, ActivityResult>.launchSafely(intent: Intent) {
    try {
        launch(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No activity handles %s", intent.action)
    }
}
