package org.ktpg.wireprotocol.frontend

import io.ktor.utils.io.core.*
import org.ktpg.wireprotocol.i32ToByteArray

internal enum class StatementOrPortal { PreparedStatement, Portal }

internal data class CloseMessage(
    val closeTarget: StatementOrPortal,
    val name: String
)

internal fun CloseMessage.serialize(): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'C'.code.toByte()

    val targetType = ByteArray(1)
    targetType[0] = when (this.closeTarget) {
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