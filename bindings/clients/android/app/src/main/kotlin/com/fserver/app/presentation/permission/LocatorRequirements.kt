package com.fserver.app.presentation.permission

import com.fserver.core.network.TransportKind
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker

/**
 * The transport a locator will be dialled over, or null when the dial itself decides.
 *
 * A device named by id can be answered by discovery or by any route in the trust records, so
 * there is no single set of rules to check it against — gating it on one transport's would refuse
 * attempts that another route would have carried.
 */
val PeerLocator.transportKind: TransportKind?
    get() = when (this) {
        is PeerLocator.Ip,
        is PeerLocator.QrPayload,
            -> TransportKind.ManualAddress

        is PeerLocator.NearbyEndpoint -> TransportKind.NearbyConnections

        is PeerLocator.DiscoveredDevice,
        is PeerLocator.KnownDevice,
            -> null
    }

/** What is in the way of dialling [locator]; satisfied when the transport is not decided yet. */
suspend fun RequirementsChecker.forLocator(locator: PeerLocator): RequirementReport =
    locator.transportKind
        ?.let { forTransport(it) }
        ?: RequirementReport.Satisfied
