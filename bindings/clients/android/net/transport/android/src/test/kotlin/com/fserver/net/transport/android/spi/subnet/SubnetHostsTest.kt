package com.fserver.net.transport.android.spi.subnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class SubnetHostsTest {

    @Test
    fun `a 24 skips network and broadcast`() {
        val hosts = SubnetHosts.hosts(ip("192.168.1.37"), 24)

        assertEquals(254, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
    }

    @Test
    fun `a wide subnet is narrowed to the 24 around the device`() {
        val hosts = SubnetHosts.hosts(ip("10.20.30.40"), 8)

        assertEquals(254, hosts.size)
        assertTrue(hosts.all { it.startsWith("10.20.30.") })
    }

    @Test
    fun `a narrow subnet is kept as is`() {
        assertEquals(
            listOf("172.16.0.9", "172.16.0.10", "172.16.0.11", "172.16.0.12", "172.16.0.13", "172.16.0.14"),
            SubnetHosts.hosts(ip("172.16.0.10"), 29),
        )
    }

    @Test
    fun `point to point links have nothing to sweep`() {
        assertTrue(SubnetHosts.hosts(ip("10.0.0.1"), 31).isEmpty())
        assertTrue(SubnetHosts.hosts(ip("10.0.0.1"), 32).isEmpty())
    }

    private fun ip(value: String): Inet4Address = InetAddress.getByName(value) as Inet4Address
}
