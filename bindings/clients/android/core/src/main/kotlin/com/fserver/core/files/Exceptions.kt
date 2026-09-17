package com.fserver.core.files

import com.fserver.common.exception.FServerException
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker

/**
 * A source was reached for while the OS was still withholding what reaching it needs.
 *
 * Its own type rather than `RequirementsNotMetException`, which is a detection failure: nothing is
 * being discovered here, and a host that shows a permissions sheet for a scan must not show it for
 * a device search.
 */
class SourceRequirementsNotMetException(
    val location: SourceLocation,
    val report: RequirementReport,
) : FServerException("Cannot use source at $location: $report")

/**
 * Refuses to go near [location] while the OS is still withholding something it needs.
 *
 * Not a security control - the OS enforces the grant either way. This turns "scanned nothing" and
 * "write failed" into an answer the host can put in front of the user before the work starts.
 */
internal suspend fun RequirementsChecker.ensureSourceReachable(location: SourceLocation) {
    val report = forSource(location)

    if (!report.isSatisfied) throw SourceRequirementsNotMetException(location, report)
}
