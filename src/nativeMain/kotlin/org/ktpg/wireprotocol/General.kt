package org.ktpg.wireprotocol

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.ktpg.i32ToByteArray

typealias ErrorResponse = Map<String,String>
typealias NoticeResponse = Map<String,String>

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
    BIND_COMPLETE('2'),
    CLOSE_COMPLETE('3'),
    PARAMETER_DESCRIPTION('t')
}

/**
 * This will read a string until null termination or until end of packet data
 */
internal fun readString(packet: ByteReadPacket): String {
    var current: Int = packet.readByte().toInt()

    if (current.toInt() == 0) {
        return ""
    }

    val s = StringBuilder()
    while(current != 0 && !packet.endOfInput) {
        s.append(current.toChar())
        current = packet.readByte().toInt()
    }
    if (current != 0) {
        // this just means we hit end of input, but still need to append the last character read above
        s.append(current.toChar())
    }

    return s.toString()
}

internal fun readString(packet: ByteReadPacket, limit: Int): String {
    var current: Int

    val s = StringBuilder()
    var count = 0
    do {
        current = packet.readByte().toInt()
        s.append(current.toChar())
        count++
    } while(current != 0 && !packet.endOfInput && count < limit)

    return s.toString()
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
//                println("Got ErrorResponse message from server")
                return Err(StartupFailure("Got ErrorResponse message from server"))
            }
            MessageType.PARAMETER_STATUS.value -> {
//                println("Got ParameterStatus message from server")
                val parameterName = readString(message.messageBytes)
                val parameterValue = readString(message.messageBytes)
                parameterStatusMap[parameterName] = parameterValue
//                println("Got Parameter $parameterName - $parameterValue")
            }
            MessageType.BACKEND_KEY_DATA.value -> {
//                println("Got BackendKeydata message from server")
                val backendProcessId = message.messageBytes.readInt()
                val backendSecretKey = message.messageBytes.readInt()
//                println("backendProcessId=$backendProcessId, backendSecretKey=$backendSecretKey")
                backendKeyDataMap["backendProcessId"] = backendProcessId
                backendKeyDataMap["backendSecretKey"] = backendSecretKey
            }
            MessageType.READY_FOR_QUERY.value -> {
//                println("Got ReadyForQuery message from server")
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
