package org.ktpg.wireprotocol

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.ktpg.*
import org.ktpg.i32ToByteArray
import org.ktpg.readString

data class ColumnDescriptor(val name: String,
                            val tableOid: Int,
                            val columnId: Short,
                            val dataTypeOid: Int,
                            val dataTypeSize: Short,
                            val typeModifier: Int,
                            val formatCode: Short)

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
    var columns = mutableListOf<ColumnDescriptor>()
    var dataRows = mutableListOf<Map<String, String>>()
    var commandTag = ""
    var notices = mapOf<String,String>()
    val result = mutableListOf<SimpleQueryResponse>()

    repeat(10000) {
        message = readMessage(receiveChannel)

        when(message.messageType) {
            MessageType.ROW_DESCRIPTION.value -> {
                val columnCount = message.messageBytes.readShort()
                // TODO: what if this is 0?
                for (i in 0..<columnCount) {
                    val name = readString(message.messageBytes)
                    val tableOid = message.messageBytes.readInt()
                    val columnId = message.messageBytes.readShort()
                    val dataTypeOid = message.messageBytes.readInt()
                    val dataTypeSize = message.messageBytes.readShort()
                    val typeModifier = message.messageBytes.readInt()
                    val formatCode = message.messageBytes.readShort()
                    val c = ColumnDescriptor(name, tableOid, columnId, dataTypeOid, dataTypeSize, typeModifier, formatCode)
                    columns.add(c)
                }
            }
            MessageType.DATA_ROW.value -> {
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
            MessageType.COMMAND_COMPLETE.value -> {
                commandTag = readString(message.messageBytes)
                result.add(SimpleQueryResponse(commandTag, columns, dataRows, emptyMap(), notices))
                commandTag = ""
                columns = mutableListOf<ColumnDescriptor>()
                dataRows = mutableListOf<Map<String, String>>()
                notices = mutableMapOf<String,String>()
            }
            MessageType.EMPTY_QUERY_RESPONSE.value -> {
                // not really anything to do here
                println("Received Empty Query Response message")
            }
            MessageType.COPY_IN_RESPONSE.value -> {
                println("Received Copy In Response message")
            }
            MessageType.COPY_OUT_RESPONSE.value -> {
                println("Received Copy Out Response message")
            }
            MessageType.ERROR_RESPONSE.value -> {
                val err = parseErrorOrNoticeResponseMessage(message.messageBytes)
//                println("Received error message from server: $err")
                result.add(SimpleQueryResponse(commandTag, columns, dataRows, err, notices))
                commandTag = ""
                columns = mutableListOf<ColumnDescriptor>()
                dataRows = mutableListOf<Map<String, String>>()
                notices = mutableMapOf<String,String>()
            }
            MessageType.NOTICE_RESPONSE.value -> {
                notices = parseErrorOrNoticeResponseMessage(message.messageBytes)
//                println("Received notice message from server: $notices")
            }
            MessageType.READY_FOR_QUERY.value -> {
                val status = message.messageBytes.readByte().toInt().toChar()
//                println("Received Ready For Query with status: $status")
                return Ok(result)
            }
        }
    }
    return Err(SimpleQueryError("Exhausted loop counter when reading simple query response..."))
}