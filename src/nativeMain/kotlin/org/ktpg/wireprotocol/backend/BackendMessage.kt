package io.github.ryderhuggins.ktpg.wireprotocol.backend

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.github.ryderhuggins.ktpg.wireprotocol.PgTypes
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.ColumnDescriptor

typealias ErrorResponse = Map<String,String>
typealias NoticeResponse = Map<String,String>

data class PgWireMessage(val messageType: Char, val messageBytes: ByteReadPacket)

/**
 * TODO - can this throw any exceptions? I don't see any on GitHub
 */
internal suspend fun readMessage(receiveChannel: ByteReadChannel): PgWireMessage {
    val messageType = receiveChannel.readByte().toInt().toChar()
    val messageSize = receiveChannel.readInt()
    val message = receiveChannel.readRemaining((messageSize-4).toLong())
    return PgWireMessage(messageType, message)
}

enum class BackendMessageType(val value: Char) {
    BACKEND_KEY_DATA('K'),
    ERROR_RESPONSE('E'),
    AUTHENTICATION('R'),
    PARAMETER_STATUS('S'),
    READY_FOR_QUERY('Z'),
    COMMAND_COMPLETE('C'),
    COPY_IN_RESPONSE('G'),
    COPY_OUT_RESPONSE('H'),
    ROW_DESCRIPTION('T'),
    DATA_ROW('D'),
    EMPTY_QUERY_RESPONSE('I'),
    NOTICE_RESPONSE('N'),
    PARSE_COMPLETE('1'),
    BIND_COMPLETE('2'),
    CLOSE_COMPLETE('3'),
    PARAMETER_DESCRIPTION('t')
}

sealed interface BackendMessage {
    data object ParseComplete : BackendMessage
    data object BindComplete : BackendMessage
    data class ParameterDescription(val parameters: List<PgTypes>) : BackendMessage
    data class RowDescription(val columns: List<ColumnDescriptor>) : BackendMessage
    data object CloseComplete : BackendMessage
    data class ErrorResponse(val err: Map<String, String>) : BackendMessage
    data class NoticeResponse(val notice: Map<String, String>) : BackendMessage
}