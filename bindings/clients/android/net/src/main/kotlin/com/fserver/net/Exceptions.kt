package com.fserver.net

import com.fserver.common.exception.NetworkException
import com.fserver.net.dictionary.MessageDictionary

/**
 * The peer speaks a dictionary this side declined - reported by the user's own `negotiate`.
 */
class DictionaryMismatchException(
    val remote: MessageDictionary.Descriptor,
    val reason: String,
) : NetworkException.Handshake(
    "dictionary rejected: $reason (remote ${remote.id} v${remote.version})"
)
