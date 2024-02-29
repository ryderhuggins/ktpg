package io.github.ryderhuggins.ktpg.wireprotocol.frontend

import io.github.ryderhuggins.ktpg.wireprotocol.i32ToByteArray

internal data object SyncMessage
internal data object FlushMessage

internal fun SyncMessage.serialize(): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'S'.code.toByte()
    return messageType + i32ToByteArray(4)
}

internal fun FlushMessage.serialize(): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'H'.code.toByte()
    return messageType + i32ToByteArray(4)
}