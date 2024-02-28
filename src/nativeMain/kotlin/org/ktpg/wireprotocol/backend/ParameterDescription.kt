package org.ktpg.wireprotocol.backend

import io.ktor.utils.io.core.*
import org.ktpg.wireprotocol.PgTypes


internal fun readParameterDescriptionMessage(
    messageBytes: ByteReadPacket
): List<PgTypes> {
    val parameterDescription = mutableListOf<PgTypes>()
    val parameterCount = messageBytes.readShort()
    for (i in 0..<parameterCount) {
        val typeOid = messageBytes.readInt()
        parameterDescription.add(PgTypes.getByValue(typeOid) ?: PgTypes.UNKNOWN)
    }
    return parameterDescription
}