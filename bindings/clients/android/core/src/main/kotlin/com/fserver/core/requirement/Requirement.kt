package com.fserver.core.requirement

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.provider.Settings
import com.fserver.core.network.info.model.NetworkCapability

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
     * Permissions this operation needs that the host's manifest does not declare - so no dialog
     * exists for them and `checkSelfPermission` will answer "denied" forever.
     *
     * Never solvable, which is the point: the optional permissions are the host's to declare, and
     * one it left out is a feature it chose not to ship rather than a grant the user could still
     * give. Reported rather than swallowed, because the same absence is what a typo in the manifest
     * looks like.
     *
     * Also covers a permission the device itself has retired - one past its `maxSdkVersion` is
     * dropped at parse time and never appears as declared.
     */
    data class UndeclaredPermission(val permissions: List<String>) : Requirement

    /**
     * A permission no runtime dialog can grant — the user switches it on in Settings and comes
     * back. Unlike a [RuntimePermission] it is never asked for in a launcher, and unlike a
     * [SystemToggle] it is this app's alone rather than a device-wide switch.
     */
    data class SpecialPermission(val kind: Kind) : Requirement {
        /** Settings screen that grants this. Start it as an `Intent` action. */
        val settingsAction: String get() = kind.settingsAction

        @SuppressLint("InlinedApi")
        enum class Kind(val settingsAction: String) {
            /** Reading and writing paths outside the app's own storage, from API 30 on. */
            ALL_FILES_ACCESS(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        }
    }

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
