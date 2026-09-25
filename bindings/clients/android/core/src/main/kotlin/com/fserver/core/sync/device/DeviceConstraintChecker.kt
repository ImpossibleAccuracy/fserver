package com.fserver.core.sync.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.flow.first


/** Check if the device meets the specified constraints for synchronization. */
class DeviceConstraintChecker(
    private val context: Context,
    private val networkInfoRepository: NetworkInfoRepository,
) {
    suspend operator fun invoke(constraints: SourceEntry.Preferences.DeviceConstraints): Boolean {
        if (constraints.wifiRequired) {
            val network = networkInfoRepository.networkInfo.first()
            val isNetworkAllowed = network is NetworkInfo.WiFi || network is NetworkInfo.Wired

            if (!isNetworkAllowed) {
                return false
            }
        }

        if (constraints.chargingRequired) {
            if (!isDeviceCharging()) {
                return false
            }
        }

        return true
    }

    private fun isDeviceCharging(): Boolean {
        // Get the current battery status sticky intent
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

        // Extract the charging status
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        // The device is charging if the status is CHARGING or FULL
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }
}
