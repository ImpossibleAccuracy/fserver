package com.fserver.core.data.detection.model

import com.fserver.core.domain.model.exception.DetectionFailedException

class DeviceScanningException : DetectionFailedException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
