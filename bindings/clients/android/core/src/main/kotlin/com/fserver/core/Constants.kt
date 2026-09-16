package com.fserver.core

import com.fserver.net.connection.LanPorts

object Constants {
    /** Where an address with no port is dialled - the first port a peer's listener takes. */
    val DEFAULT_PORT = LanPorts.DEFAULT
    val VALID_PORT_RANGE = 1..65535
}
