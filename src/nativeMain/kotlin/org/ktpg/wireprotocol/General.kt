package org.ktpg.wireprotocol

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.ktpg.i32ToByteArray
import org.ktpg.readString

data class PgWireMessage(val messageType: Char, val messageBytes: ByteReadPacket)

data class StartupParameters(val parameterStatusMap: Map<String,String>, val backendKeyDataMap: Map<String,Int>)
data class StartupFailure(val errorString: String)

enum class MessageType(val value: Char) {
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
    BIND_COMPLETE('2')
}

/**
 * TODO - can this throw any exceptions? I don't see any on GitHub
 */
internal suspend fun readMessage(receiveChannel: ByteReadChannel): PgWireMessage {
    val messageType = receiveChannel.readByte().toInt().toChar()
    val messageSize = receiveChannel.readInt()
    val message = receiveChannel.readRemaining((messageSize-4).toLong())
    return PgWireMessage(messageType, message)
}

internal suspend fun sendStartupMessage(sendChannel: ByteWriteChannel, startupMessage: StartupMessage) {
    val startupMessageBytes = serialize(startupMessage)
    sendChannel.writeFully(startupMessageBytes)
}

internal suspend fun readStartupResponse(receiveChannel: ByteReadChannel): Result<StartupParameters, StartupFailure> {
    val parameterStatusMap = mutableMapOf<String, String>()
    val backendKeyDataMap = mutableMapOf<String, Int>()

    var message = readMessage(receiveChannel)

    // setting an upper bound for sanity's sake
    var i = 0
    while (i++ < 200) {
        when(message.messageType) {
            MessageType.ERROR_RESPONSE.value -> {
                println("Got ErrorResponse message from server")
                return Err(StartupFailure("Got ErrorResponse message from server"))
            }
            MessageType.PARAMETER_STATUS.value -> {
                println("Got ParameterStatus message from server")
                val parameterName = readString(message.messageBytes)
                val parameterValue = readString(message.messageBytes)
                parameterStatusMap[parameterName] = parameterValue
                println("Got Parameter $parameterName - $parameterValue")
            }
            MessageType.BACKEND_KEY_DATA.value -> {
                println("Got BackendKeydata message from server")
                val backendProcessId = message.messageBytes.readInt()
                val backendSecretKey = message.messageBytes.readInt()
                println("backendProcessId=$backendProcessId, backendSecretKey=$backendSecretKey")
                backendKeyDataMap["backendProcessId"] = backendProcessId
                backendKeyDataMap["backendSecretKey"] = backendSecretKey
            }
            MessageType.READY_FOR_QUERY.value -> {
                println("Got ReadyForQuery message from server")
                // we're done... return the parameters
                return Ok(StartupParameters(parameterStatusMap, backendKeyDataMap))
            }
        }
        message = readMessage(receiveChannel)
    }

    if (i == 200) {
        println("Exhausted loop iterations!")
    }
    return Err(StartupFailure("oof"))
}

internal suspend fun sendTerminationMessage(sendChannel: ByteWriteChannel) {
    val messageType = ByteArray(1)
    messageType[0] = 'X'.code.toByte()
    val terminationMessage = messageType + i32ToByteArray(4)
    sendChannel.writeFully(terminationMessage)
}

internal fun parseErrorOrNoticeResponseMessage(messageBytes: ByteReadPacket): Map<String, String> {
    val errorInfo = mutableMapOf<String,String>()
    var fieldType: Byte

    // error fields defined here: https://www.postgresql.org/docs/current/protocol-error-fields.html
    var errorKey: String
    var errorValue: String
    repeat (10000) {
        fieldType = messageBytes.readByte()
        if (fieldType.toInt() == 0) {
            messageBytes.discard()
            return errorInfo
        }

        errorKey = when(fieldType.toInt().toChar()) {
            'S' -> "Severity"
            'V' -> "Severity"
            'C' -> "Code"
            'M' -> "Message"
            'D' -> "Detail"
            'H' -> "Hint"
            'P' -> "Position"
            'p' -> "Internal Position"
            'q' -> "Internal Query"
            'W' -> "Where"
            's' -> "Schema"
            't' -> "Table name"
            'c' -> "Column name"
            'd' -> "Data type name"
            'n' -> "Constraint name"
            'F' -> "File"
            'L' -> "Line"
            'R' -> "Routine"
            else -> "UNKNOWN"
        }

        errorValue = readString(messageBytes)
        errorInfo[errorKey] = errorValue
    }

    return errorInfo
}
