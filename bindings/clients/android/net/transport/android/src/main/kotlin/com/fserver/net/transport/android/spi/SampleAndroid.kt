package com.fserver.net.transport.android.spi

import android.content.Context
import com.fserver.net.config.networkConfig
import com.fserver.net.NetworkNode
import com.fserver.net.dictionary.MessageCodec
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.IdentityStore
import com.fserver.net.security.LocalIdentity
import com.fserver.net.session.CloseReason
import com.fserver.net.transport.android.spi.ip.DirectIpSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsScanParams
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsScanParams
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

public class SampleAndroid(
    public val context: Context,
) {
    public suspend fun start(): Unit = coroutineScope {
        val config = networkConfig(dictionary = HelloWorldDictionary()) {
            identityStore = AuthStore()

            install(
                DirectIpSPI.create(),
                MulticastDnsSPI.create(context),
                NearbyConnectionsSPI.create(
                    context = context,
                    config = NearbyConnectionsSPI.Config(
                        serviceId = "_hello_world._tcp.",
                    ),
                ),
            )
        }

        NetworkNode.create(config).use { node ->
            node.discovery.startAdvertising()

            val scanOptions = listOf(
                NearbyConnectionsScanParams,
                MulticastDnsScanParams
            )

            scanOptions.forEach {
                launch { node.discovery.scan(it) }
            }

            launch {
                node.discovery.peers.collect {
                    Timber.d("Discovered peers: $it")
                }
            }

            launch {
                node.connections.incoming.collect { connection ->
                    launch {
                        delay(5000.milliseconds) // user action simulation

                        // auto-accept
                        connection.accept().getOrThrow()
                    }
                }
            }

            launch {
                val firstDevice = node.discovery.peers.first { it.isNotEmpty() }.first()

                val connection = node.connections.connect(firstDevice)
                    .getOrThrow()

                launch {
                    connection.incoming.collect {
                        Timber.d("Received message: $it")
                    }
                }

                connection.send(HelloWorldMessages.Hello).getOrThrow()

                connection.request(HelloWorldMessages.Greeting("Hello from Android!"))
                    .onSuccess { response ->
                        Timber.d("Received response: $response")
                    }
                    .onFailure { error ->
                        Timber.e(error, "Failed to send greeting")
                    }

                connection.close(CloseReason.Normal)
            }
        }
    }
}

private sealed interface HelloWorldMessages {
    data object Hello : HelloWorldMessages

    data class Greeting(val text: String) : HelloWorldMessages

    data class GreetingResponse(val text: String) : HelloWorldMessages
}

private class HelloWorldDictionary : MessageDictionary<HelloWorldMessages> {
    override val descriptor: MessageDictionary.Descriptor = MessageDictionary.Descriptor(
        id = "HelloWorld",
        version = 1,
    )

    override val codec: MessageCodec<HelloWorldMessages>
        get() = TODO("Not yet implemented")

    override fun negotiate(remote: MessageDictionary.Descriptor): MessageDictionary.Decision =
        MessageDictionary.Decision.Accept(remote.version)
}

private class AuthStore : IdentityStore {
    override val local: LocalIdentity
        get() = LocalIdentity(
            deviceId = UUID.randomUUID().toString(),
            displayName = "Sample Android Device",
            publicKey = "abcd".toByteArray(),
        )
}
