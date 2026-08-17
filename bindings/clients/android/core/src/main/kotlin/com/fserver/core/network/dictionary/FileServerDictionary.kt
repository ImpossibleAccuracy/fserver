package com.fserver.core.network.dictionary

import com.fserver.net.dictionary.MessageCodec
import com.fserver.net.dictionary.MessageDictionary
import kotlinx.serialization.json.Json

class FileServerDictionary : MessageDictionary<FileServerMessages> {
    override val descriptor: MessageDictionary.Descriptor = MessageDictionary.Descriptor(
        id = "FServer",
        version = 1,
    )

    override val codec: MessageCodec<FileServerMessages> = Codec()

    override fun negotiate(remote: MessageDictionary.Descriptor): MessageDictionary.Decision =
        MessageDictionary.Decision.Accept(remote.version)

    private class Codec : MessageCodec<FileServerMessages> {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        override fun encode(message: FileServerMessages): ByteArray =
            json.encodeToString(message).encodeToByteArray()

        override fun decode(bytes: ByteArray): FileServerMessages {
            val string = bytes.decodeToString()
            return json.decodeFromString(string)
        }
    }
}
