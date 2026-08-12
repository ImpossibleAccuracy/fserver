package com.fserver.app.presentation.screens.discovery.manual.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.pairing.model.PairingTarget

@Immutable
data class ManualAddressState(
    val host: String = "",
    /** Kept as text: an empty field means "the default port", which is not a number. */
    val port: String = "",
    val isChecking: Boolean = false,
    val error: Error? = null,
    /** Set once the address answered; the sheet navigates on and clears it. */
    val found: PairingTarget? = null,
) {
    val canConnect: Boolean
        get() = host.isNotBlank() && !isChecking

    sealed interface Error {
        data object InvalidPort : Error
        data object Unreachable : Error
        data class Unknown(val message: String?) : Error
    }
}
