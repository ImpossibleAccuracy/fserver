package com.fserver.net.support

import com.fserver.net.dictionary.DictionaryDecision
import com.fserver.net.dictionary.DictionaryDescriptor
import com.fserver.net.dictionary.MessageCodec
import com.fserver.net.dictionary.MessageDictionary

/** A two-word vocabulary. Enough to prove `:net` never looks inside one. */
sealed interface TestMessage {
    data class Ask(val text: String) : TestMessage
    data class Answer(val text: String) : TestMessage
    data class Notice(val text: String) : TestMessage
}

class TestCodec : MessageCodec<TestMessage> {
    override fun encode(message: TestMessage): ByteArray = when (message) {
        is TestMessage.Ask -> "A:${message.text}"
        is TestMessage.Answer -> "R:${message.text}"
        is TestMessage.Notice -> "N:${message.text}"
    }.encodeToByteArray()

    override fun decode(bytes: ByteArray): TestMessage {
        val raw = bytes.decodeToString()
        val body = raw.substring(2)
        return when (raw.first()) {
            'A' -> TestMessage.Ask(body)
            'R' -> TestMessage.Answer(body)
            'N' -> TestMessage.Notice(body)
            else -> throw IllegalArgumentException("unknown message: $raw")
        }
    }
}

class TestDictionary(
    id: String = "test.dictionary",
    version: Int = 1,
    supported: IntRange = 1..1,
) : MessageDictionary<TestMessage> {
    override val descriptor = DictionaryDescriptor(id = id, version = version, supported = supported)

    override val codec = TestCodec()

    override fun negotiate(remote: DictionaryDescriptor): DictionaryDecision = when {
        remote.id != descriptor.id -> DictionaryDecision.Reject("foreign dictionary ${remote.id}")
        remote.version !in descriptor.supported -> DictionaryDecision.Reject("version ${remote.version} unsupported")
        else -> DictionaryDecision.Accept(minOf(descriptor.version, remote.version))
    }
}
