package com.fserver.app.data

import com.fserver.app.presentation.model.DeviceUi
import com.fserver.app.presentation.model.DiagnosticCheckUi
import com.fserver.app.presentation.model.FileUi
import com.fserver.app.presentation.model.IncomingRequestUi
import com.fserver.app.presentation.model.PairingCandidateUi
import com.fserver.app.presentation.model.ServerProfileUi
import com.fserver.app.presentation.model.TransferUi
import com.fserver.app.presentation.model.TreeNodeUi

/**
 * Where the screens get their content until `:core` exists.
 *
 * This is a deliberate seam, not a repository: the ViewModels depend on this type through
 * Koin, so replacing it with a `:core`-backed implementation is a change in the DI module
 * and nowhere else. Nothing here decides anything — it hands back fixtures.
 */
interface DemoContentSource {
    fun devices(): List<DeviceUi>
    fun networkName(): String
    fun pairingCandidate(deviceId: String): PairingCandidateUi
    fun scannedProfile(): ServerProfileUi
    fun serverName(): String
    fun breadcrumb(): String
    fun files(): List<FileUi>
    fun gridTiles(): List<FileUi>
    fun tree(): List<TreeNodeUi>
    fun itemCount(): Int
    fun transfers(): List<TransferUi>
    fun incomingRequest(): IncomingRequestUi
    fun downloadFolder(): String
    fun diagnosticChecks(): List<DiagnosticCheckUi>
}

class SampleContentSource : DemoContentSource {
    override fun devices(): List<DeviceUi> = SampleData.devices
    override fun networkName(): String = SampleData.NETWORK_NAME

    /**
     * The MVP shows the same candidate whatever was tapped — a real implementation reads
     * the fingerprint the handshake actually offered for [deviceId].
     */
    override fun pairingCandidate(deviceId: String): PairingCandidateUi =
        SampleData.pairingCandidate

    override fun scannedProfile(): ServerProfileUi = SampleData.scannedProfile
    override fun serverName(): String = SampleData.CURRENT_SERVER
    override fun breadcrumb(): String = SampleData.BREADCRUMB
    override fun files(): List<FileUi> = SampleData.files
    override fun gridTiles(): List<FileUi> = SampleData.gridTiles
    override fun tree(): List<TreeNodeUi> = SampleData.tree
    override fun itemCount(): Int = SampleData.GRID_ITEM_COUNT
    override fun transfers(): List<TransferUi> = SampleData.transfers
    override fun incomingRequest(): IncomingRequestUi = SampleData.incomingRequest
    override fun downloadFolder(): String = SampleData.DOWNLOAD_FOLDER
    override fun diagnosticChecks(): List<DiagnosticCheckUi> = SampleData.diagnosticChecks
}
