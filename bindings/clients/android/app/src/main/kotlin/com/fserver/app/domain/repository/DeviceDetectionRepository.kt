package com.fserver.app.domain.repository

import com.fserver.app.domain.model.FoundDeviceDomain
import kotlinx.coroutines.flow.Flow

interface DeviceDetectionRepository {
    val onlineDevices: Flow<List<FoundDeviceDomain>>

    val isScanning: Flow<Boolean>

    /**
     * Starts mDNS discovery for devices on the local network.
     */
    suspend fun startNetworkServiceDiscovery()

    /**
     * Starts scanning entire subnet for devices on the local network.
     */
    suspend fun startSubnetScan()

    /**
     * Manually adds a device to the list of known devices.
     *
     * @param ipAddress The IP address of the device to ping.
     * @param port The port of the device to ping. If null, the default port will be used.
     *
     * @return true if the device was successfully added, false if it was already known or could not be reached.
     */
    suspend fun pingDevice(ipAddress: String, port: Int?): Boolean

    /**
     * Starts Device discovery API.
     */
    suspend fun startAndroidDiscovery()
}
