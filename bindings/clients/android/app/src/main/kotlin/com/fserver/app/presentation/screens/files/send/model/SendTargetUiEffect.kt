package com.fserver.app.presentation.screens.files.send.model

sealed interface SendTargetUiEffect {
    /** The user confirmed; the send flow is over and the file list takes the screen back. */
    data object SendStarted : SendTargetUiEffect
}
