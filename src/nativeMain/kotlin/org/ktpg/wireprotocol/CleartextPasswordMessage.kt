package org.ktpg.wireprotocol

import org.ktpg.i32ToByteArray
import org.ktpg.toAscii

internal data class CleartextPasswordMessage(val password: String)

internal fun serialize(cleartextPasswordMessage: CleartextPasswordMessage): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'p'.code.toByte()
    val sizeOf = cleartextPasswordMessage.password.length + 1 + 4
    return messageType + i32ToByteArray(sizeOf) + cleartextPasswordMessage.password.toAscii() + 0x0
}