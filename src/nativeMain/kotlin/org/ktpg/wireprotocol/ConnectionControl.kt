package io.github.ryderhuggins.ktpg.wireprotocol

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.github.ryderhuggins.ktpg.wireprotocol.backend.BackendMessageType
import io.github.ryderhuggins.ktpg.wireprotocol.backend.readMessage
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.StartupMessage
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.TerminationMessage
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.serialize


data class StartupParameters(val parameterStatusMap: Map<String,String>, val backendKeyDataMap: Map<String,Int>)
data class StartupFailure(val errorString: String)


internal suspend fun sendStartupMessage(sendChannel: ByteWriteChannel, startupMessage: StartupMessage) {
    sendChannel.writeFully(startupMessage.serialize())
}

internal suspend fun readStartupResponse(receiveChannel: ByteReadChannel): Result<StartupParameters, StartupFailure> {
    val parameterStatusMap = mutableMapOf<String, String>()
    val backendKeyDataMap = mutableMapOf<String, Int>()

    var message = readMessage(receiveChannel)

    // setting an upper bound for sanity's sake
    var i = 0
    while (i++ < 200) {
        when(message.messageType) {
            BackendMessageType.ERROR_RESPONSE.value -> {
//                println("Got ErrorResponse message from server")
                return Err(StartupFailure("Got ErrorResponse message from server"))
            }
            BackendMessageType.PARAMETER_STATUS.value -> {
//                println("Got ParameterStatus message from server")
                val parameterName = readString(message.messageBytes)
                val parameterValue = readString(message.messageBytes)
                parameterStatusMap[parameterName] = parameterValue
//                println("Got Parameter $parameterName - $parameterValue")
            }
            BackendMessageType.BACKEND_KEY_DATA.value -> {
//                println("Got BackendKeydata message from server")
                val backendProcessId = message.messageBytes.readInt()
                val backendSecretKey = message.messageBytes.readInt()
//                println("backendProcessId=$backendProcessId, backendSecretKey=$backendSecretKey")
                backendKeyDataMap["backendProcessId"] = backendProcessId
                backendKeyDataMap["backendSecretKey"] = backendSecretKey
            }
            BackendMessageType.READY_FOR_QUERY.value -> {
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
    sendChannel.writeFully(TerminationMessage.serialize())
}