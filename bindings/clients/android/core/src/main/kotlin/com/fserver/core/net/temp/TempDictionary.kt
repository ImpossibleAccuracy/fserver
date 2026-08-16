package com.fserver.core.net.temp

import com.fserver.net.dictionary.MessageCodec
import com.fserver.net.dictionary.MessageDictionary

internal class TempDictionary : MessageDictionary<TempMessages> {
    override val descriptor: MessageDictionary.Descriptor = MessageDictionary.Descriptor(
        id = "HelloWorld",
        version = 1,
    )

    override val codec: MessageCodec<TempMessages> = object : MessageCodec<TempMessages> {
        override fun encode(message: TempMessages): ByteArray {
            return ByteArray(0) // Placeholder implementation
        }

        override fun decode(bytes: ByteArray): TempMessages {
            return TempMessages.Hello // Placeholder implementation
        }
    }

    override fun negotiate(remote: MessageDictionary.Descriptor): MessageDictionary.Decision =
        MessageDictionary.Decision.Accept(remote.version)
}
