package org.ktpg.wireprotocol.frontend

import org.ktpg.wireprotocol.i32ToByteArray

data object TerminationMessage

internal fun TerminationMessage.serialize(): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'X'.code.toByte()
    return messageType + i32ToByteArray(4)
}