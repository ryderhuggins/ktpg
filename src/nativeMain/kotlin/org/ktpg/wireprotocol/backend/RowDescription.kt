package io.github.ryderhuggins.ktpg.wireprotocol.backend

import io.ktor.utils.io.core.*
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.ColumnDescriptor
import io.github.ryderhuggins.ktpg.wireprotocol.readString

internal fun readRowDescriptionMessage(
    messageBytes: ByteReadPacket,
): List<ColumnDescriptor> {
    val columns = mutableListOf<ColumnDescriptor>()
    val columnCount = messageBytes.readShort()
    // TODO: what if this is 0?
    for (i in 0..<columnCount) {
        val name = readString(messageBytes)
        val tableOid = messageBytes.readInt()
        val columnId = messageBytes.readShort()
        val dataTypeOid = messageBytes.readInt()
        val dataTypeSize = messageBytes.readShort()
        val typeModifier = messageBytes.readInt()
        val formatCode = messageBytes.readShort()
        val c = ColumnDescriptor(name, tableOid, columnId, dataTypeOid, dataTypeSize, typeModifier, formatCode)
        columns.add(c)
    }
    return columns
}