package org.ktpg.wireprotocol.frontend

import io.ktor.utils.io.core.*
import org.ktpg.wireprotocol.i32ToByteArray
import org.ktpg.wireprotocol.toAscii
import org.ktpg.wireprotocol.toByteArray
import org.ktpg.wireprotocol.PgTypes


internal data class ParseMessage(
    val name: String,
    val query: String,
    val typeOids: List<PgTypes>
)

internal fun serialize(parseMessage: ParseMessage): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'P'.code.toByte()
    
    val serializedName = if(parseMessage.name.isEmpty()) {
        byteArrayOf(0x0)
    } else {
        parseMessage.name.toByteArray() + 0x0
    }
    
    val serializedTypes = if (parseMessage.typeOids.isEmpty()) {
        byteArrayOf()
    } else {
        parseMessage.typeOids
            .map { i32ToByteArray(it.oid) }
            .reduce { bytes, acc -> bytes + acc }
    }
    
    val size = 4 + serializedName.size + parseMessage.query.length + 1 + 2 + parseMessage.typeOids.size*4
    
    return messageType + i32ToByteArray(size) + serializedName + parseMessage.query.toAscii() + 0x0 + parseMessage.typeOids.size.toShort().toByteArray() + serializedTypes
}