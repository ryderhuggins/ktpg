package org.ktpg.wireprotocol

import io.ktor.utils.io.core.*
import org.ktpg.i32ToByteArray

internal enum class CloseTarget { PreparedStatement, Portal }

internal data class CloseMessage(
    val closeTarget: CloseTarget,
    val name: String
)

internal fun CloseMessage.serialize(): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'C'.code.toByte()

    val targetType = ByteArray(1)
    targetType[0] = when (this.closeTarget) {
        CloseTarget.PreparedStatement -> 'S'.code.toByte()
        CloseTarget.Portal -> 'P'.code.toByte()
    }

    val serializedName = if(this.name.isEmpty()) {
        byteArrayOf(0x0)
    } else {
        this.name.toByteArray() + 0x0
    }

    val messageSize = 1 + 4 + 1 + serializedName.size

    println("writing $messageSize bytes on Close message")

    return messageType + i32ToByteArray(messageSize) + targetType + serializedName
}