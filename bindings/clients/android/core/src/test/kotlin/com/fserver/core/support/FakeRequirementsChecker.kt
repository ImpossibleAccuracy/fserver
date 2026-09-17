package com.fserver.core.support

import com.fserver.core.files.SourceLocation
import com.fserver.core.network.TransportKind
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker

/**
 * A device with nothing in the way, unless a test says otherwise.
 *
 * Requirements are about the OS, not about the engine, so every test that is not *about* them
 * wants them satisfied and out of the way.
 */
internal class FakeRequirementsChecker(
    var transport: RequirementReport = RequirementReport.Satisfied,
    var networkInfo: RequirementReport = RequirementReport.Satisfied,
    var source: RequirementReport = RequirementReport.Satisfied,
) : RequirementsChecker {
    override suspend fun forTransport(method: TransportKind): RequirementReport = transport

    override suspend fun forNetworkInfo(): RequirementReport = networkInfo

    override suspend fun forSource(location: SourceLocation): RequirementReport = source
}
