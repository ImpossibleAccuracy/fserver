package com.fserver.app.domain.model

sealed interface NetworkInfoDomain {
    val id: String
    val name: String

    data class WiFi(
        val ssid: String,
        val bssid: String,
    ) : NetworkInfoDomain {
        override val id: String = bssid
        override val name: String = ssid
    }

    data class Mobile(
        val carrierName: String,
        val networkType: String,
    ) : NetworkInfoDomain {
        override val id: String = carrierName
        override val name: String = networkType
    }
}
