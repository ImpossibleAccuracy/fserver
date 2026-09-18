package com.fserver.app.presentation.error

import com.fserver.app.R
import com.fserver.app.presentation.model.UiText
import com.fserver.common.exception.DetectionFailedException
import com.fserver.common.exception.FileSystemException
import com.fserver.common.exception.MalformedQrException
import com.fserver.common.exception.NetworkException
import com.fserver.common.exception.SyncException
import com.fserver.common.exception.TransferException
import com.fserver.core.files.SourceRequirementsNotMetException
import com.fserver.core.network.DeviceUnreachableException
import com.fserver.core.network.PeerIdentityMismatchException
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.sync.progress.SyncFailure
import java.io.IOException

/**
 * The one place a `Throwable` becomes something a user can read.
 *
 * Only the project's own exception types get a sentence of their own — those are the failures
 * whose cause is known. Anything else is reported as unknown rather than leaking an English
 * `message` written for a log into the UI.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is RequirementsNotMetException -> report.toAppError()

    is SourceRequirementsNotMetException -> report.toAppError()

    is DeviceUnreachableException -> AppError(
        message = UiText.of(R.string.error_device_unreachable),
        detail = UiText.of(R.string.error_device_unreachable_detail),
    )

    is MalformedQrException -> AppError(UiText.of(R.string.error_qr_malformed))

    is PeerIdentityMismatchException -> AppError(
        message = UiText.of(R.string.error_identity_mismatch),
        detail = UiText.of(R.string.error_identity_mismatch_detail),
    )

    is DetectionFailedException -> AppError(UiText.of(R.string.error_detection_failed))

    is NetworkException -> toAppError()
    is SyncException -> toAppError()
    is TransferException -> toAppError()
    is FileSystemException -> toAppError()

    is SecurityException -> AppError(UiText.of(R.string.error_permission_denied))
    is IOException -> AppError(UiText.of(R.string.error_io))

    else -> AppError(UiText.of(R.string.error_unknown))
}

/**
 * An unmet report read as a failure. [RequirementReport.isResolvableByUser] decides the wording:
 * a blocker is not something a button can clear, so it is not offered as one.
 */
fun RequirementReport.toAppError(): AppError = AppError(
    message = UiText.of(
        if (isResolvableByUser) R.string.error_requirements_solvable
        else R.string.error_requirements_blocked
    ),
    requirements = this,
)

/**
 * A pass that gave up, read as a failure.
 *
 * [SyncFailure.reason] picks the sentence; the report it carries - when it carries one - is what
 * turns the snackbar into a sheet with a button on it. Only a refusal shows the engine's own
 * wording, because the peer's reason is the only thing that says which refusal this was.
 */
fun SyncFailure.toAppError(): AppError = AppError(
    message = UiText.of(
        when (reason) {
            SyncFailure.Reason.Unreachable -> R.string.error_sync_unreachable
            SyncFailure.Reason.Refused -> R.string.error_sync_refused
            SyncFailure.Reason.NotAllowed -> R.string.error_sync_not_allowed
            SyncFailure.Reason.SourceUnavailable -> R.string.error_sync_source_unavailable
            SyncFailure.Reason.TransferFailed -> R.string.error_sync_transfer_failed
            SyncFailure.Reason.NotConverged -> R.string.error_sync_not_converged
            SyncFailure.Reason.Failed -> R.string.error_sync_failed
        }
    ),
    detail = detail?.takeIf { reason == SyncFailure.Reason.Refused }?.let(UiText::Text),
    requirements = requirements,
)

private fun NetworkException.toAppError(): AppError = when (this) {
    is NetworkException.NoRoute -> AppError(UiText.of(R.string.error_no_route))

    is NetworkException.Transport -> AppError(UiText.of(R.string.error_transport))

    is NetworkException.RequestTimeout -> AppError(UiText.of(R.string.error_timeout))

    is NetworkException.SessionClosed,
    is NetworkException.SessionLinkLost,
        -> AppError(UiText.of(R.string.error_session_lost))

    // The peer said no and said why. Its reason is the only thing that tells the two cases apart —
    // a wrong password from a device that refuses this one outright — so it is shown as given.
    is NetworkException.AuthenticationRejected -> AppError(
        message = UiText.of(R.string.error_auth_rejected),
        detail = message?.let(UiText::Text),
    )

    is NetworkException.Handshake -> AppError(UiText.of(R.string.error_handshake))

    is NetworkException.Protocol,
    is NetworkException.FrameTooLarge,
        -> AppError(UiText.of(R.string.error_protocol))
}

private fun SyncException.toAppError(): AppError = when (this) {
    is SyncException.DuplicateSourceException ->
        AppError(UiText.of(R.string.error_source_duplicate))

    is SyncException.RemoteRejectedException -> AppError(
        message = UiText.of(R.string.error_source_rejected),
        detail = message?.let(UiText::Text),
    )

    is SyncException.MaxRetriesExceededException,
    is SyncException.ActionFailedException,
        -> AppError(UiText.of(R.string.error_sync_failed))
}

private fun TransferException.toAppError(): AppError = when (this) {
    is TransferException.FileNotFoundException,
    is TransferException.UploadNotFoundException,
        -> AppError(UiText.of(R.string.error_file_missing))

    is TransferException.UploadHashMismatchException ->
        AppError(UiText.of(R.string.error_transfer_corrupted))

    is TransferException.TooManyUploadsException,
    is TransferException.PendingChunksOverflowException,
        -> AppError(UiText.of(R.string.error_transfer_busy))
}

private fun FileSystemException.toAppError(): AppError = when (this) {
    is FileSystemException.InvalidPath,
    is FileSystemException.NotDirectory,
        -> AppError(UiText.of(R.string.error_path_invalid))

    is FileSystemException.AlreadyExists -> AppError(UiText.of(R.string.error_file_exists))
    is FileSystemException.CreationFailed -> AppError(UiText.of(R.string.error_file_create_failed))
}
