package com.github.ryderhuggins

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import toAscii

class PgConnection(val host: String, val port: Int, val user: String, val database: String, val optionalParameters: Map<String, Unit>) {
    private lateinit var socket: Socket
    private lateinit var sendChannel: ByteWriteChannel
    private lateinit var receiveChannel: ByteReadChannel
    private lateinit var selectorManager: SelectorManager
    private var initialized: Boolean = false

    suspend fun initialize() {
        if (!initialized) {
            selectorManager = SelectorManager(Dispatchers.IO)
            socket = aSocket(selectorManager).tcp().connect(host, port)
            receiveChannel = socket.openReadChannel()
            sendChannel = socket.openWriteChannel(autoFlush = true)
            initialized = true
        }
    }

    suspend fun sendStartupMessageNoAuthentication() {
        val parameters = listOf("user", user, "database", database)
        val size = parameters.sumOf { it.length + 1 } + 9 // 4 for protocol version, 4 for size, 1 for extra null byte
        val startupMessage = i32ToByteArray(size) + 0x0 + 0x03 + 0x0 + 0x0 + parameters.flatMap { it.toAscii() + 0x0 } + 0x0
        sendChannel.writeFully(startupMessage)
    }

    private suspend fun sendTerminationMessage() {
        val messageType = ByteArray(1)
        messageType[0] = 'X'.code.toByte()
        val terminationMessage = messageType + i32ToByteArray(4)
        sendChannel.writeFully(terminationMessage)
    }

    /**
     * If the function returns normally, this implies that the ReadyForQuery response was received from the server
     */
    suspend fun readStartupResponseNoAuthentication(): Result<StartupMessageResponse> {
        // in this scenario, we should just read:
        // - AuthenticationOk, a list of ParameterStatus, a list of BackendKeyData, and ReadyForQuery
        // then return the two lists or an error
        val parameterStatusMap = emptyMap<String, String>().toMutableMap()
        val backendKeyDataMap = emptyMap<String, Int>().toMutableMap()

        var message = readMessage().getOrThrow()

        // setting an upper bound for sanity's sake
        var i = 0
        while (i++ < 200) {
            when(message.messageType) {
                MessageType.ERROR_RESPONSE.value -> {
                    println("Got ErrorResponse message from server")
                    return Result.failure(Throwable("Got ErrorResponse message from server"))
                }
                MessageType.AUTHENTICATION.value -> {
                    println("Got Authentication message from server")

                    val authStatus = message.message.readInt()
                    println("Authentication status: $authStatus")
                    if (authStatus != 0) {
                        return Result.failure(Throwable("Authentication status was $authStatus"))
                    }
                }
                MessageType.PARAMETER_STATUS.value -> {
                    println("Got ParameterStatus message from server")
                    val parameterName = readString(message.message)
                    val parameterValue = readString(message.message)
                    parameterStatusMap[parameterName] = parameterValue
                    println("Got Parameter $parameterName - $parameterValue")
                }
                MessageType.BACKEND_KEY_DATA.value -> {
                    println("Got BackendKeydata message from server")
                    val backendProcessId = message.message.readInt()
                    val backendSecretKey = message.message.readInt()
                    println("backendProcessId=$backendProcessId, backendSecretKey=$backendSecretKey")
                    backendKeyDataMap["backendProcessId"] = backendProcessId
                    backendKeyDataMap["backendSecretKey"] = backendSecretKey
                }
                MessageType.READY_FOR_QUERY.value -> {
                    println("Got ReadyForQuery message from server")
                    // we're done... return the parameters
                    return Result.success(StartupMessageResponse(parameterStatusMap, backendKeyDataMap))
                }
            }
            message = readMessage().getOrThrow()
        }

        if (i == 200) {
            println("Exhausted loop iterations!")
        }
        return Result.failure(Throwable("oof"))
    }

    suspend fun readMessage(): Result<PgWireMessage> {
        val messageType = receiveChannel.readByte().toInt().toChar()
        val messageSize = receiveChannel.readInt()
        val message = receiveChannel.readRemaining((messageSize-4).toLong())
        return Result.success(PgWireMessage(messageType, message))
    }

    suspend fun close() {
        sendTerminationMessage()
        socket.close()
        selectorManager.close()
    }
}