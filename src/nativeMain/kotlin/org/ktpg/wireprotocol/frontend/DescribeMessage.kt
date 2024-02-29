package io.github.ryderhuggins.ktpg.wireprotocol.frontend

import io.ktor.utils.io.core.*
import io.github.ryderhuggins.ktpg.wireprotocol.i32ToByteArray

internal data class DescribeMessage(
    val describeTarget: StatementOrPortal,
    val name: String
)

internal fun DescribeMessage.serialize(): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'D'.code.toByte()

    val targetType = ByteArray(1)
    targetType[0] = when (this.describeTarget) {
        StatementOrPortal.PreparedStatement -> 'S'.code.toByte()
        StatementOrPortal.Portal -> 'P'.code.toByte()
    }

    val serializedName = if(this.name.isEmpty()) {
        byteArrayOf(0x0)
    } else {
        this.name.toByteArray() + 0x0
    }

    val messageSize = 4 + 1 + serializedName.size

    return messageType + i32ToByteArray(messageSize) + targetType + serializedName
}