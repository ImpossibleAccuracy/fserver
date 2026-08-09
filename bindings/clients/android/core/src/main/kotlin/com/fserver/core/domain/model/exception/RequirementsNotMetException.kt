package com.fserver.core.domain.model.exception

import com.fserver.core.domain.model.requirement.RequirementReport

/**
 * Detection request was started while something it needs was missing.
 */
class RequirementsNotMetException(
    val report: RequirementReport,
) : DetectionFailedException("Requirements not met: $report")
