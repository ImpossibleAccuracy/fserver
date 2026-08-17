package com.fserver.app.presentation.composable.model

import androidx.annotation.StringRes
import com.fserver.app.R
import com.fserver.core.network.auth.AuthMethod

/**
 * What a method is called where it is being *described* — the settings list, a device's record.
 * The pairing screen has its own labels, because there the same method is an instruction.
 */
@get:StringRes
val AuthMethod.labelRes: Int
    get() = when (this) {
        AuthMethod.ConfirmFingerprint -> R.string.auth_method_confirm_fingerprint
        AuthMethod.NearbySas -> R.string.auth_method_nearby_sas
        AuthMethod.Password -> R.string.auth_method_password
    }
