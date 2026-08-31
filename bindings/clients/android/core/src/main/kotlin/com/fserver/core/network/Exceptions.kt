package com.fserver.core.network

import com.fserver.common.exception.DetectionFailedException
import com.fserver.core.requirement.RequirementReport

/**
 * Detection request was started while something it needs was missing.
 */
class RequirementsNotMetException(
    val report: RequirementReport,
) : DetectionFailedException("Requirements not met: $report")
