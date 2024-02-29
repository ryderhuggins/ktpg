package io.github.ryderhuggins.ktpg.wireprotocol.frontend

import io.github.ryderhuggins.ktpg.wireprotocol.i32ToByteArray
import io.github.ryderhuggins.ktpg.wireprotocol.toAscii

internal data class CleartextPasswordMessage(val password: String)

internal fun serialize(cleartextPasswordMessage: CleartextPasswordMessage): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'p'.code.toByte()
    val sizeOf = cleartextPasswordMessage.password.length + 1 + 4
    return messageType + i32ToByteArray(sizeOf) + cleartextPasswordMessage.password.toAscii() + 0x0
}