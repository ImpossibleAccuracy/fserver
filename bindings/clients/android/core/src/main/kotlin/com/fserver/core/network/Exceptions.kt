package com.fserver.core.network

import com.fserver.common.exception.DetectionFailedException
import com.fserver.common.exception.NetworkException
import com.fserver.core.requirement.RequirementReport

/**
 * Detection request was started while something it needs was missing.
 */
class RequirementsNotMetException(
    val report: RequirementReport,
) : DetectionFailedException("Requirements not met: $report")

/**
 * A device from the trust records could not be reached again: nothing that was written down about
 * it can be dialled, and discovery is not reporting it now.
 *
 * [transport] is the kind the last known route belonged to, null when no route was ever recorded.
 */
class DeviceUnreachableException(
    val deviceId: String,
    val transport: TransportKind?,
    cause: Throwable? = null,
) : DetectionFailedException(
    transport?.let { "Cannot reconnect using $it" } ?: "No known route to device $deviceId",
    cause,
)

/**
 * The device that completed the handshake is not the device that was dialled.
 *
 * Anything that names a device before the handshake - an advertisement, a written-down route - is
 * unauthenticated, so a caller asking for one device can be answered by another. Only the identity
 * the handshake proved counts, and a session under the wrong one is closed rather than returned.
 */
class PeerIdentityMismatchException(
    val expected: String,
    val actual: String,
) : NetworkException.Handshake("dialled $expected but $actual answered")
