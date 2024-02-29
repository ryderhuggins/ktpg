package io.github.ryderhuggins.ktpg.wireprotocol.frontend

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.github.ryderhuggins.ktpg.wireprotocol.*
import io.github.ryderhuggins.ktpg.wireprotocol.backend.*
import io.github.ryderhuggins.ktpg.wireprotocol.backend.parseErrorOrNoticeResponseMessage
import io.github.ryderhuggins.ktpg.wireprotocol.backend.readMessage
import io.github.ryderhuggins.ktpg.wireprotocol.backend.readRowDescriptionMessage
import io.github.ryderhuggins.ktpg.wireprotocol.i32ToByteArray
import io.github.ryderhuggins.ktpg.wireprotocol.readString

data class ColumnDescriptor(
    val name: String,
    val tableOid: Int,
    val columnId: Short,
    val dataTypeOid: Int,
    val dataTypeSize: Short,
    val typeModifier: Int,
    val formatCode: Short
)

data class SimpleQueryResponse(
    val commandTag: String,
    val columns: List<ColumnDescriptor>,
    val dataRows: List<Map<String,String>>,
    val error: Map<String,String>,
    val notice: Map<String, String>
)

data class SimpleQueryError(val errorString: String)

typealias SimpleQuery = String

internal fun serialize(simpleQuery: SimpleQuery): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'Q'.code.toByte()
    return messageType + i32ToByteArray(4 + simpleQuery.length + 1) + simpleQuery.toAscii() + 0x0
}

internal suspend fun sendSimpleQueryMessage(sendChannel: ByteWriteChannel, sql: SimpleQuery) {
    // send string
    val messageBytes = serialize(sql)
    sendChannel.writeFully(messageBytes)
}

internal suspend fun readSimpleQueryResponseMessages(receiveChannel: ByteReadChannel): Result<List<SimpleQueryResponse>, SimpleQueryError> {
    var message: PgWireMessage
    // we want to populate a list of columns and list of rows
    var columns = emptyList<ColumnDescriptor>()
    var dataRows = mutableListOf<Map<String, String>>()
    var commandTag = ""
    var notices = mapOf<String,String>()
    val result = mutableListOf<SimpleQueryResponse>()

    repeat(10000) {
        message = readMessage(receiveChannel)

        when(message.messageType) {
            BackendMessageType.ROW_DESCRIPTION.value -> {
                columns = readRowDescriptionMessage(message.messageBytes)
            }
            BackendMessageType.DATA_ROW.value -> {
                val columnCount = message.messageBytes.readShort()
                val dataRow = mutableMapOf<String,String>()

                fields@ for (i in 0..<columnCount) {
                    val fieldLength = message.messageBytes.readInt()
                    if (fieldLength == -1) {
                        println("Field length -1. Continuing to next iteration...")
                        continue@fields
                    }
                    val columnValue = readString(message.messageBytes, fieldLength)
                    dataRow[columns[i].name] = columnValue
                }
                dataRows.add(dataRow)
            }
            BackendMessageType.COMMAND_COMPLETE.value -> {
                commandTag = readString(message.messageBytes)
                result.add(SimpleQueryResponse(commandTag, columns, dataRows, emptyMap(), notices))
                commandTag = ""
                columns = mutableListOf<ColumnDescriptor>()
                dataRows = mutableListOf<Map<String, String>>()
                notices = mutableMapOf<String,String>()
            }
            BackendMessageType.EMPTY_QUERY_RESPONSE.value -> {
                // not really anything to do here
                println("Received Empty Query Response message")
            }
            BackendMessageType.COPY_IN_RESPONSE.value -> {
                println("Received Copy In Response message")
            }
            BackendMessageType.COPY_OUT_RESPONSE.value -> {
                println("Received Copy Out Response message")
            }
            BackendMessageType.ERROR_RESPONSE.value -> {
                val err = parseErrorOrNoticeResponseMessage(message.messageBytes)
//                println("Received error message from server: $err")
                result.add(SimpleQueryResponse(commandTag, columns, dataRows, err, notices))
                commandTag = ""
                columns = mutableListOf<ColumnDescriptor>()
                dataRows = mutableListOf<Map<String, String>>()
                notices = mutableMapOf<String,String>()
            }
            BackendMessageType.NOTICE_RESPONSE.value -> {
                notices = parseErrorOrNoticeResponseMessage(message.messageBytes)
//                println("Received notice message from server: $notices")
            }
            BackendMessageType.READY_FOR_QUERY.value -> {
                val status = message.messageBytes.readByte().toInt().toChar()
//                println("Received Ready For Query with status: $status")
                return Ok(result)
            }
        }
    }
    return Err(SimpleQueryError("Exhausted loop counter when reading simple query response..."))
}