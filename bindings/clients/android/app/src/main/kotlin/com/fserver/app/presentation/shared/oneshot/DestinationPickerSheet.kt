package com.fserver.app.presentation.shared.oneshot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fserver.app.R
import com.fserver.app.domain.oneshot.OneShotDestinations
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.source.shared.composable.SourceChoiceRow
import com.fserver.app.presentation.screens.source.shared.model.readablePath
import com.fserver.core.files.SourceLocation

/** Where received files go, as the user reads it. */
@Composable
fun SourceLocation.Hostable.destinationLabel(): String = when (this) {
    is SourceLocation.Internal -> stringResource(R.string.oneshot_destination_app_storage)
    is SourceLocation.Downloads -> "Download/$directory"
    is SourceLocation.Tree, is SourceLocation.Directory -> readablePath() ?: stringResource(R.string.value_unknown)
}

/**
 * Downloads, app storage, or a folder picked in the system dialog. A picked folder is answered
 * only once its write grant is taken, so a destination the app cannot write to never gets chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationPickerSheet(
    current: SourceLocation.Hostable,
    onPick: (SourceLocation.Hostable) -> Unit,
    onError: (Throwable) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.takeTreeGrant(uri).fold(onSuccess = onPick, onFailure = onError)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DkSpacing.lg)
                .padding(bottom = DkSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.oneshot_destination_title),
                style = MaterialTheme.typography.titleLarge,
            )

            SourceChoiceRow(
                title = stringResource(R.string.oneshot_destination_downloads),
                description = OneShotDestinations.Downloads.destinationLabel(),
                selected = current is SourceLocation.Downloads,
                onSelect = { onPick(OneShotDestinations.Downloads) },
            )

            SourceChoiceRow(
                title = stringResource(R.string.oneshot_destination_app_storage),
                description = stringResource(R.string.oneshot_destination_app_storage_desc),
                selected = current is SourceLocation.Internal,
                onSelect = { onPick(OneShotDestinations.AppStorage) },
            )

            SourceChoiceRow(
                title = stringResource(R.string.oneshot_destination_folder),
                description = (current as? SourceLocation.Tree)?.destinationLabel()
                    ?: stringResource(R.string.oneshot_destination_folder_desc),
                selected = current is SourceLocation.Tree,
                navigates = true,
                onSelect = { folderLauncher.launch(null) },
            )
        }
    }
}

private fun Context.takeTreeGrant(uri: Uri): Result<SourceLocation.Tree> = runCatching {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    contentResolver.takePersistableUriPermission(uri, flags)
    // Fails fast on a uri that is not a tree, rather than on the first file written.
    DocumentsContract.getTreeDocumentId(uri)
    SourceLocation.Tree(uri.toString())
}
