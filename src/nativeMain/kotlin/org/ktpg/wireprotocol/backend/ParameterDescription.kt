package org.ktpg.wireprotocol.backend

import io.ktor.utils.io.core.*
import org.ktpg.wireprotocol.frontend.ColumnDescriptor
import org.ktpg.wireprotocol.PgTypes

sealed interface DescribeResponseMessages {
    data object ParseComplete : DescribeResponseMessages
    data object BindComplete : DescribeResponseMessages
    data class ParameterDescription(val parameters: List<PgTypes>) : DescribeResponseMessages
    data class RowDescription(val columns: List<ColumnDescriptor>) : DescribeResponseMessages // TODO
    data object CloseComplete : DescribeResponseMessages
    data class ErrorResponse(val err: Map<String, String>) : DescribeResponseMessages
    data class NoticeResponse(val notice: Map<String, String>) : DescribeResponseMessages
}

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