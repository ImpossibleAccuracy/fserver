package com.fserver.core.requirement

import com.fserver.core.network.TransportKind

/**
 * Answers "what is still in the way?" for the operations `:core` cannot perform on its own
 * authority - the ones the OS gates behind permissions, radios and hardware.
 *
 * Its own seam rather than a method on each repository: the checks are the same machinery
 * regardless of what is being checked, they share one version-mapping table, and a host that
 * only wants to render a permissions screen should not have to reach through the detection
 * engine to do it.
 *
 * None of this is a security control. The OS enforces permissions; this exists so a denied one
 * can be explained up front instead of arriving later as "found nothing".
 */
interface RequirementsChecker {

    /**
     * What is missing before [method] could run. An empty report means ready to run.
     *
     * Keyed on the method, not a whole request: what the OS gates is the transport, and the
     * arguments a request carries - an address, a scanned payload - change nothing about it.
     */
    suspend fun forTransport(method: TransportKind): RequirementReport

    /**
     * What is missing before [NetworkInfoRepository.networkInfo] can name the network rather than
     * just type it.
     *
     * Android treats SSID and BSSID as location data, so reading them is gated on a location
     * permission and on location services being switched on. Without them the transport is still
     * reported, but the name it carries is a redacted placeholder.
     */
    suspend fun forNetworkInfo(): RequirementReport
}
