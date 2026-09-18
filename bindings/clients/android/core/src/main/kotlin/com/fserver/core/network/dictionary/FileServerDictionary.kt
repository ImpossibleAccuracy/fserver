package com.fserver.core.network.dictionary

import com.fserver.core.network.dictionary.codec.UploadChunkCodec
import com.fserver.net.dictionary.MessageCodec
import com.fserver.net.dictionary.MessageDictionary
import kotlinx.serialization.json.Json

internal class FileServerDictionary : MessageDictionary<FileServerMessages> {
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
            coerceInputValues = true
        }

        override fun encode(message: FileServerMessages): ByteArray {
            if (message is FileServerMessages.UploadChunk) {
                return UploadChunkCodec.encode(message)
            }

            return json.encodeToString(message).encodeToByteArray()
        }

        override fun decode(bytes: ByteArray): FileServerMessages {
            if (UploadChunkCodec.isUploadChunk(bytes)) {
                return UploadChunkCodec.decode(bytes)
            }

            val string = bytes.decodeToString()
            return json.decodeFromString(string)
        }
    }
}
