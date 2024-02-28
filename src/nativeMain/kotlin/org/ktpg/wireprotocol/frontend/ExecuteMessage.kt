package org.ktpg.wireprotocol.frontend

import io.ktor.utils.io.core.toByteArray
import org.ktpg.wireprotocol.i32ToByteArray

internal data class ExecuteMessage(
    val portalName: String = "",
    val maxReturnRows: Int = 0
)

internal fun serialize(executeMessage: ExecuteMessage): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'E'.code.toByte()
    
    val serializedName = if (executeMessage.portalName.isEmpty()) {
        byteArrayOf(0x0)
    } else {
        executeMessage.portalName.toByteArray() + 0x0
    }
    
    val size = 4 + serializedName.size + 4
    return messageType + i32ToByteArray(size) + serializedName + i32ToByteArray(executeMessage.maxReturnRows)
}