package com.fserver.app.data.detection.model

import com.fserver.app.domain.model.exception.DetectionFailedException

class DeviceConnectionException : DetectionFailedException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
