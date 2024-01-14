package org.ktpg.wireprotocol

import org.ktpg.i32ToByteArray
import org.ktpg.toAscii

internal data class SaslInitialResponse(val gs2Header: String, val mechanism: String, val clientSalt: String) {
    val clientFirstMessageBare = "n=,r=$clientSalt"
    val clientFirstData = gs2Header + clientFirstMessageBare
}

internal fun serialize(saslInitialResponse: SaslInitialResponse): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'p'.code.toByte()
    val length = 4 + saslInitialResponse.mechanism.length + 1 + 4 +  saslInitialResponse.clientFirstData.length
    return messageType + i32ToByteArray(length) + saslInitialResponse.mechanism.toAscii() + 0x0 + i32ToByteArray(saslInitialResponse.clientFirstData.length) + saslInitialResponse.clientFirstData.toAscii()
}