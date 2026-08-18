package com.fserver.core.requirement.impl

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.fserver.core.network.TransportKind
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.requirement.Requirement
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Resolves [RequirementRules] against what this device is actually doing right now.
 *
 * Everything version-dependent already happened in [RequirementRules];
 * this only reads live state and sorts what it finds into the two halves of a [RequirementReport].
 */
internal class RequirementsCheckerImpl(
    context: Context,
    private val networkInfoRepository: NetworkInfoRepository,
) : RequirementsChecker {
    private val context: Context = context.applicationContext

    override suspend fun forTransport(method: TransportKind): RequirementReport {
        val rules = detectionRequirementRules(method, Build.VERSION.SDK_INT)

        val network = if (rules.needsNetwork) {
            NetworkSnapshot.Known(readNetwork())
        } else {
            NetworkSnapshot.Unknown
        }

        return resolve(rules = rules, network = network)
    }

    override suspend fun forNetworkInfo(): RequirementReport =
        resolve(
            rules = networkInfoRequirementRules(Build.VERSION.SDK_INT),
            network = NetworkSnapshot.Unknown,
        )

    /**
     * Read transport as [NetworkInfoRepository] sees it.
     */
    private suspend fun readNetwork() =
        withTimeoutOrNull(NETWORK_READ_TIMEOUT_MS.milliseconds) {
            networkInfoRepository.networkInfo.first()
        }

    /**
     * Sorts the rules into what the device is missing and cannot fix,
     * and what it is missing but could fix if the user cooperates.
     */
    private fun resolve(rules: RequirementRules, network: NetworkSnapshot): RequirementReport {
        val blockers = mutableListOf<Requirement>()
        val solvable = mutableListOf<Requirement>()

        // ---------------- Check missing hardware ----------------
        rules.hardware
            .filterNot(::hasHardware)
            .mapTo(blockers) { Requirement.MissingHardware(it) } // Always blocker

        // ---------------- Check Google Play services ----------------
        if (rules.requiresPlayServices) {
            playServicesRequirement()?.let { requirement ->
                // Solvability depends on the error code: some are resolvable, some are not.
                if (requirement.isUserResolvable) solvable += requirement
                else blockers += requirement
            }
        }

        // ---------------- Check permissions ----------------
        val missingPermissions = rules.permissions.filterNot(::isPermissionGranted)
        if (missingPermissions.isNotEmpty()) {
            // Solvable, user can grant them
            solvable += Requirement.RuntimePermission(missingPermissions)
        }

        // ---------------- Check system features enabled ----------------
        // What the transport features is missing
        val missingNetworkRequirements = when (network) {
            NetworkSnapshot.Unknown -> emptySet()
            is NetworkSnapshot.Known -> rules.missingNetworkRequirements(network.value)
        }

        // Is the transport sufficient to satisfy the rules?
        val transportSuffices = rules.needsNetwork &&
                network is NetworkSnapshot.Known &&
                network.value != null &&
                missingNetworkRequirements.isEmpty()

        val unmetToggles = rules.toggles
            .filterNot {
                // If the transport is sufficient, don't ask the user to flip toggle,
                // it is already on and carrying the right traffic,
                // or current network can do everything the rules ask for.
                transportSuffices && it == Requirement.SystemToggle.Kind.WIFI
            }
            .filterNot { kind ->
                // Check if app need permission before checking actual toggle state
                togglePermission(
                    kind,
                    Build.VERSION.SDK_INT
                )?.let { it in missingPermissions } == true
            }
            .filterNot(::isToggleOn)

        unmetToggles.mapTo(solvable) {
            // Solvable, user can flip them.
            Requirement.SystemToggle(it)
        }

        // ---------------- Check network ----------------
        // Suppressed while Wi-Fi is off, because flipping it lands the device
        // on a different transport - anything measured now describes a network the user is not on.
        val wifiIsOn = Requirement.SystemToggle.Kind.WIFI !in unmetToggles
        if (wifiIsOn) {
            // Blocker, user cannot flip a toggle to fix it, only join a different network.
            blockers += missingNetworkRequirements
        }

        return RequirementReport(blockers = blockers, solvable = solvable)
    }

    private fun isPermissionGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun hasHardware(feature: Requirement.MissingHardware.Feature): Boolean =
        context.packageManager.hasSystemFeature(feature.platformFeature)

    @SuppressLint("MissingPermission")
    private fun isToggleOn(kind: Requirement.SystemToggle.Kind): Boolean = when (kind) {
        Requirement.SystemToggle.Kind.BLUETOOTH ->
            context.getSystemService(BluetoothManager::class.java)
                ?.adapter?.isEnabled
                ?: false

        Requirement.SystemToggle.Kind.WIFI ->
            context.getSystemService(WifiManager::class.java)
                ?.isWifiEnabled
                ?: false

        Requirement.SystemToggle.Kind.LOCATION_SERVICES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(LocationManager::class.java)
                ?.isLocationEnabled
                ?: false
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF,
            ) != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    private fun playServicesRequirement(): Requirement.PlayServices? {
        val availability = GoogleApiAvailability.getInstance()
        val status = availability.isGooglePlayServicesAvailable(context)

        return if (status == ConnectionResult.SUCCESS) {
            null
        } else {
            Requirement.PlayServices(
                statusCode = status,
                isUserResolvable = availability.isUserResolvableError(status),
            )
        }
    }

}

private sealed interface NetworkSnapshot {
    data object Unknown : NetworkSnapshot

    data class Known(val value: NetworkInfo?) : NetworkSnapshot
}

/** Long enough for a platform callback to fire, short enough not to stall a scan behind it. */
private const val NETWORK_READ_TIMEOUT_MS = 1_000L
