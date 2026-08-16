package com.fserver.core.domain.model.requirement

import android.content.pm.PackageManager
import android.provider.Settings
import com.fserver.core.domain.model.network.NetworkCapability

/**
 * One thing standing between any action and a chance of succeeding.
 *
 * These are reported, never enforced: the OS enforces permissions, the radio enforces physics.
 * A [Requirement] exists so the UI can explain a failure *before* it happens instead of turning
 * a denied permission into "found nothing".
 *
 * Requirements are handed to the host as raw platform tokens - permission names, settings intent
 * actions - rather than as an abstract vocabulary the host would have to translate back.
 * The SDK-version branching that picks those tokens is the part worth centralizing, and it lives here.
 */
sealed interface Requirement {

    /**
     * Runtime permissions the app holds a manifest declaration for but has not been granted.
     *
     * Collapsed into one requirement rather than one per permission: the host asks for them in a
     * single `RequestMultiplePermissions` launch, and a partial grant is answered by checking
     * again, not by walking a list.
     */
    data class RuntimePermission(val permissions: List<String>) : Requirement

    /**
     * A radio or system service the user has switched off.
     */
    data class SystemToggle(val kind: Kind) : Requirement {
        /** Settings screen that flips this toggle. Start it as an `Intent` action. */
        val settingsAction: String get() = kind.settingsAction

        enum class Kind(val settingsAction: String) {
            BLUETOOTH(Settings.ACTION_BLUETOOTH_SETTINGS),
            WIFI(Settings.ACTION_WIFI_SETTINGS),
            LOCATION_SERVICES(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        }
    }

    /**
     * Google Play services missing, disabled or too old - Nearby Connections is delivered through
     * it and cannot run without it.
     *
     * @property statusCode a `ConnectionResult` code, to be passed back to
     * `GoogleApiAvailability.getErrorDialog` when [isUserResolvable].
     */
    data class PlayServices(
        val statusCode: Int,
        val isUserResolvable: Boolean,
    ) : Requirement

    /**
     * The device has no such radio. Never resolvable - this is the one requirement that makes a
     * detection method permanently unavailable on this hardware.
     */
    data class MissingHardware(val feature: Feature) : Requirement {
        enum class Feature(val platformFeature: String) {
            BLUETOOTH(PackageManager.FEATURE_BLUETOOTH),
            BLUETOOTH_LE(PackageManager.FEATURE_BLUETOOTH_LE),
            WIFI(PackageManager.FEATURE_WIFI),
            WIFI_DIRECT(PackageManager.FEATURE_WIFI_DIRECT),
        }
    }

    /**
     * The current transport does not carry a capability the method needs - mobile data and
     * multicast, say. Only a different network fixes it, which the app cannot do on the user's
     * behalf.
     */
    data class MissingNetworkCapability(val capability: NetworkCapability) : Requirement

    /** No network at all. */
    data object NoConnectivity : Requirement
}
