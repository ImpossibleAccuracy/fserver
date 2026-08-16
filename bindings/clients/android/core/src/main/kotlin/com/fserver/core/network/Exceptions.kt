package com.fserver.core.network

import com.fserver.core.requirement.RequirementReport

open class DetectionFailedException : RuntimeException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}

class MalformedQrException : DetectionFailedException()

/**
 * Detection request was started while something it needs was missing.
 */
class RequirementsNotMetException(
    val report: RequirementReport,
) : DetectionFailedException("Requirements not met: $report")
