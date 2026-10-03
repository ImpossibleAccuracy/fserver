package com.fserver.app.presentation.shared.export.composable

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.fserver.app.R
import com.fserver.app.presentation.composable.LocalSnackbarController
import com.fserver.app.presentation.composable.ObserveEffects
import com.fserver.app.presentation.shared.export.model.ExportResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Asks where to save an archive and hands the picked document to [onPicked]. The returned
 * function opens the picker, suggesting `fserver-<name>-<date>.zip`.
 */
@Composable
fun rememberExportLauncher(onPicked: (Uri) -> Unit): (name: String) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ArchiveMimeType),
    ) { uri -> uri?.let(onPicked) }

    return { name -> launcher.launch(archiveName(name)) }
}

/** Tells the user how an export ended, on the app's snackbar. */
@Composable
fun ObserveExportResults(results: Flow<ExportResult>) {
    val snackbar = LocalSnackbarController.current
    val resources = LocalResources.current

    ObserveEffects(results) { result ->
        when (result) {
            is ExportResult.Finished -> snackbar.showSnackbar(
                if (result.skipped == 0) {
                    resources.getQuantityString(R.plurals.export_done, result.files, result.files)
                } else {
                    resources.getString(R.string.export_done_skipped, result.files, result.skipped)
                }
            )

            ExportResult.Failed -> snackbar.showSnackbar(R.string.export_failed)
        }
    }
}

private fun archiveName(name: String): String {
    val safe = name.replace(Regex("[^\\p{L}\\p{N}._-]+"), "-").trim('-').ifEmpty { "export" }
    return "fserver-$safe-${LocalDate.now()}.zip"
}

private const val ArchiveMimeType = "application/zip"
