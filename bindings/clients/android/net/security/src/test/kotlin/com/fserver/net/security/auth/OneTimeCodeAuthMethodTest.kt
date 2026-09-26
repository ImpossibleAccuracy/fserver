package com.fserver.net.security.auth

import com.fserver.common.exception.NetworkException
import com.fserver.net.security.auth.pake.OneTimeCodeAuthMethod
import com.fserver.net.security.auth.pake.OneTimeCodeAuthMethod.OneTimeCodeParams
import com.fserver.net.security.auth.pake.OneTimeCodeSource
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.X25519CryptoProvider
import com.fserver.net.security.identity.LocalIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneTimeCodeAuthMethodTest {

    @Test
    fun `matching code keys both sides alike and reports the code used`() = runTest {
        val codes = FakeCodes(CODE)
        val (initiatorIo, responderIo) = pairedIo()

        val initiator = runSide(initiatorIo, CryptoProvider.Role.Initiator, codes, CODE)
        val responder = runSide(responderIo, CryptoProvider.Role.Responder, codes, null)

        assertArrayEquals(initiator.await().getOrThrow(), responder.await().getOrThrow())
        assertEquals(1, codes.used)
    }

    @Test
    fun `wrong code fails and still spends the active code`() = runTest {
        val codes = FakeCodes(CODE)
        val (initiatorIo, responderIo) = pairedIo()

        val initiator = runSide(initiatorIo, CryptoProvider.Role.Initiator, codes, "000000")
        val responder = runSide(responderIo, CryptoProvider.Role.Responder, codes, null)

        assertTrue(initiator.await().exceptionOrNull() is NetworkException.AuthenticationRejected)
        assertTrue(responder.await().exceptionOrNull() is NetworkException.AuthenticationRejected)
        assertEquals(null, codes.take())
        assertEquals(0, codes.used)
    }

    @Test
    fun `responder with no active code rejects before any exchange`() = runTest {
        val (_, responderIo) = pairedIo()

        val responder = runSide(responderIo, CryptoProvider.Role.Responder, FakeCodes(null), null)

        assertTrue(responder.await().exceptionOrNull() is NetworkException.AuthenticationRejected)
    }

    private fun CoroutineScope.runSide(
        io: HandshakeIo,
        role: CryptoProvider.Role,
        codes: OneTimeCodeSource,
        code: String?,
    ): Deferred<Result<ByteArray>> = async {
        runCatching {
            val outcome = OneTimeCodeAuthMethod(X25519CryptoProvider, codes).run(
                io = io,
                context = AuthContext(
                    role = role,
                    request = code?.let { AuthRequest(OneTimeCodeAuthMethod.ID, OneTimeCodeParams(it)) },
                    prologue = PROLOGUE,
                    confirmationCode = null,
                    local = LocalIdentity(role.name, role.name, ByteArray(65)),
                ),
            )
            outcome.confirm()
            outcome.sharedSecret()
        }
    }

    private class FakeCodes(private var active: String?) : OneTimeCodeSource {
        var used = 0

        override suspend fun take(): String? = active.also { active = null }

        override fun onUsed() {
            used++
        }
    }

    private fun pairedIo(): Pair<HandshakeIo, HandshakeIo> {
        val aToB = Channel<ByteArray>(Channel.UNLIMITED)
        val bToA = Channel<ByteArray>(Channel.UNLIMITED)
        return PairedIo(aToB, bToA) to PairedIo(bToA, aToB)
    }

    private class PairedIo(
        private val outgoing: Channel<ByteArray>,
        private val incoming: Channel<ByteArray>,
    ) : HandshakeIo {
        override suspend fun send(payload: ByteArray) {
            outgoing.send(payload)
        }

        override suspend fun receive(): ByteArray = incoming.receive()
    }

    private companion object {
        val PROLOGUE = "hello-bytes".encodeToByteArray()
        const val CODE = "482193"
    }
}
