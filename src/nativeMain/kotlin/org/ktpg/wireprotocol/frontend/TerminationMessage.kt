package io.github.ryderhuggins.ktpg.wireprotocol.frontend

import io.github.ryderhuggins.ktpg.wireprotocol.i32ToByteArray

data object TerminationMessage

internal fun TerminationMessage.serialize(): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'X'.code.toByte()
    return messageType + i32ToByteArray(4)
}