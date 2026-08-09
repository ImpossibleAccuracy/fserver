package com.fserver.core.domain.model

sealed interface DeviceDetectionRequest {
    val method: DetectionMethod

    data class ByMethod(override val method: DetectionMethod) : DeviceDetectionRequest {
        init {
            require(!method.requiresArguments) {
                "DetectionMethod $method requires arguments"
            }
        }
    }

    data class ByManualAddress(
        val ipAddress: String,
        val port: Int? = null,
    ) : DeviceDetectionRequest {
        override val method: DetectionMethod = DetectionMethod.OnDemand.ManualAddress
    }

    data class QrCode(
        val payload: String
    ) : DeviceDetectionRequest {
        override val method: DetectionMethod = DetectionMethod.OnDemand.ManualAddress
    }
}
