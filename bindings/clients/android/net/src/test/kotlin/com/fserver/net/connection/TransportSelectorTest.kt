package com.fserver.net.connection

import com.fserver.net.connection.impl.TransportSelector
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.support.DEAD
import com.fserver.net.support.DeadEndpoint
import com.fserver.net.support.DeadTransport
import com.fserver.net.support.LOOPBACK
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.LoopbackNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransportSelectorTest {

    private val loopback = LoopbackNetwork().transport("alice")
    private val dead = DeadTransport()
    private val selector = TransportSelector(listOf(dead, loopback))

    @Test
    fun `without a policy order, routes follow the order the transports were registered in`() {
        val ordered = selector.order(listOf(loopbackRoute, deadRoute), ConnectionPolicy())

        assertEquals(listOf(DEAD, LOOPBACK), ordered.map { it.transport })
    }

    @Test
    fun `the policy's order wins over the registration order`() {
        val ordered = selector.order(
            listOf(deadRoute, loopbackRoute),
            ConnectionPolicy(transportOrder = listOf(LOOPBACK, DEAD)),
        )

        assertEquals(listOf(LOOPBACK, DEAD), ordered.map { it.transport })
    }

    @Test
    fun `a route nobody expressed a preference about goes last, not away`() {
        val ghost = PeerRef("bob", SpiId("ghost"), LoopbackEndpoint("bob"))

        val ordered = selector.order(
            listOf(ghost, deadRoute),
            ConnectionPolicy(transportOrder = listOf(DEAD)),
        )

        assertEquals(listOf(DEAD, SpiId("ghost")), ordered.map { it.transport })
    }

    @Test
    fun `a transport that does not support the endpoint is not chosen for it`() {
        val foreign = object : TransportEndpoint {
            // Right transport id, wrong endpoint type - matching on the id alone would pick it.
            override val transport = LOOPBACK
            override val address = "somewhere"
        }

        assertEquals(loopback, selector.forEndpoint(LoopbackEndpoint("bob")))
        assertEquals(dead, selector.forEndpoint(DeadEndpoint("bob")))
        assertNull(selector.forEndpoint(foreign))
    }

    private val loopbackRoute = PeerRef("bob", LOOPBACK, LoopbackEndpoint("bob"))
    private val deadRoute = PeerRef("bob", DEAD, DeadEndpoint("bob"))
}
