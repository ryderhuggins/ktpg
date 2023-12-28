package com.github.ryderhuggins

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.kotlincrypto.hash.md.MD5
import toAscii

class PgConnection(private val host: String, private val port: Int, private val user: String, private val password: String, private val database: String, private val optionalParameters: Map<String, String>) {
    private lateinit var socket: Socket
    private lateinit var sendChannel: ByteWriteChannel
    private lateinit var receiveChannel: ByteReadChannel
    private lateinit var selectorManager: SelectorManager
    private var initialized: Boolean = false
    private lateinit var startupParameters: StartupMessageResponse

    suspend fun initialize() {
        if (!initialized) {
            selectorManager = SelectorManager(Dispatchers.IO)
            socket = aSocket(selectorManager).tcp().connect(host, port)
            receiveChannel = socket.openReadChannel()
            sendChannel = socket.openWriteChannel(autoFlush = true)
            initialized = true
        }
    }

    suspend fun sendStartupMessage() {
        val parameters = listOf("user", user, "database", database)
        val size = parameters.sumOf { it.length + 1 } + 9 // 4 for protocol version, 4 for size, 1 for extra null byte
        val startupMessage = i32ToByteArray(size) + 0x0 + 0x03 + 0x0 + 0x0 + parameters.flatMap { it.toAscii() + 0x0 } + 0x0
        sendChannel.writeFully(startupMessage)
    }


    suspend fun sendCleartextPasswordResponse(): Result<AuthenticationResponse> {
        val messageType = ByteArray(1)
        messageType[0] = 'p'.code.toByte()
        val sizeOf = password.length + 1 + 4
        val cleartextPasswordMessage = messageType + i32ToByteArray(sizeOf) + password.toAscii() + 0x0
        sendChannel.writeFully(cleartextPasswordMessage)

        val authenticationOk = readAuthenticationResponse().getOrElse {
            println("Failed to read message from server")
            return Result.failure(it)
        }
        if (authenticationOk !is AuthenticationResponse.AuthenticationOk) {
            println("Unexpected message type in cleartext password authentication process. Expected AuthenticationOk. Got: $authenticationOk")
            return Result.failure(Throwable("Unexpected message type in cleartext password authentication process. Expected AuthenticationOk. Got: $authenticationOk"))
        }

        return Result.success(authenticationOk)
    }

    suspend fun sendMd5PasswordResponse(salt: String) {
        val messageType = ByteArray(1)
        messageType[0] = 'p'.code.toByte()
        val inner = MD5().digest("$password$user".toByteArray())
        val passwordHash = "md5".toByteArray() + MD5().digest(inner + salt.toByteArray())
        val sizeOf = password.length + 4
        val cleartextPasswordMessage = messageType + i32ToByteArray(sizeOf) + passwordHash
        sendChannel.writeFully(cleartextPasswordMessage)
    }

    suspend fun performSha256Authentication(): Result<AuthenticationResponse> {
        // send SASLInitialResponse message
        val messageType = ByteArray(1)
        messageType[0] = 'p'.code.toByte()
        val mechanism = "SCRAM-SHA-256"
        // hard-coded gs2 header for now
        val gs2Header = "n,,"
        val clientFirstMessageBare = "n=,r=" + getRandomString(24)
        val clientFirstData = gs2Header + clientFirstMessageBare
        println("sending client-first data: $clientFirstData")
        val length = 4 + mechanism.length + 1 + 4 +  clientFirstData.length
        val saslInitialResponseMessage = messageType + i32ToByteArray(length) + mechanism.toAscii() + 0x0 + i32ToByteArray(clientFirstData.length) + clientFirstData.toAscii()
        sendChannel.writeFully(saslInitialResponseMessage)
        println("Done sending scram-sha-256 initial response")

        val saslContinuationMessage = readAuthenticationResponse().getOrElse {
            println("Failed to read message from server")
            return Result.failure(it)
        }
        if (saslContinuationMessage !is AuthenticationResponse.SaslAuthenticationContinue) {
            println("Unexpected message type")
            return Result.failure(Throwable("Unexpected message type in SCRAM-SHA-256 authentication process. Message type: $saslContinuationMessage"))
        }

        println("sasl continuation data: ${saslContinuationMessage.saslData}")
        // TODO: here we need to check that the first part of the 'r' value is equal to the clientFirstData random string
        // s is the base64-encoded salt
        // i is the iteration count
        val serverFirstMessage = parseServerFirstMessage(saslContinuationMessage.saslData).getOrElse {
            return Result.failure(it)
        }

        val clientFinalMessageText = getScramClientFinalMessage(password, serverFirstMessage.r, serverFirstMessage.s, serverFirstMessage.i, clientFirstMessageBare, saslContinuationMessage.saslData)
        val clientFinalMessageSize = 4 + clientFinalMessageText.length
        val clientFinalMessageType = ByteArray(1)
        clientFinalMessageType[0] = 'p'.code.toByte()
        val finalMessage = clientFinalMessageType + i32ToByteArray(clientFinalMessageSize) + clientFinalMessageText.toAscii()
        sendChannel.writeFully(finalMessage)
        println("Done sending client final message")

        val saslFinalResponse = readAuthenticationResponse().getOrElse {
            println("Failed to read message from server")
            return Result.failure(it)
        }
        if (saslFinalResponse !is AuthenticationResponse.AuthenticationSASLFinal) {
            println("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationSASLFinal. Got: $saslContinuationMessage")
            return Result.failure(Throwable("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationSASLFinal. Got: $saslContinuationMessage"))
        }

        val authenticationOk = readAuthenticationResponse().getOrElse {
            println("Failed to read message from server")
            return Result.failure(it)
        }
        if (authenticationOk !is AuthenticationResponse.AuthenticationOk) {
            println("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationOk. Got: $saslContinuationMessage")
            return Result.failure(Throwable("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationOk. Got: $saslContinuationMessage"))
        }

        return Result.success(authenticationOk)
    }

    suspend fun connect(): Boolean {
        initialize()
        sendStartupMessage()
        val res = readAuthenticationResponse().getOrElse {
            println("Failed to read auth response with error: $it")
            close()
        }
        when (res) {
            is AuthenticationResponse.AuthenticationOk -> println("Authentication succeeded - no password required")
            is AuthenticationResponse.CleartextPasswordRequest -> {
                println("Password requested from server")
                sendCleartextPasswordResponse().getOrElse {
                    return false
                }
            }
            is AuthenticationResponse.Md5PasswordRequest -> {
                println("MD5 password requested from server with salt = ${res.salt}")
                sendMd5PasswordResponse(res.salt)
            }
            is AuthenticationResponse.SaslAuthenticationRequest -> {
                println("SASL authentication requested from server with mechanism - ${res.mechanism}")
                // TODO: mechanism is actually a list of mechanisms
                if (res.mechanism != "SCRAM-SHA-256") {
                    println("Error: Unsupported mechanism received from server - ${res.mechanism}")
                }
                performSha256Authentication().getOrElse {
                    return false
                }
            }
        }

        this.startupParameters = readStartupResponse().getOrElse {
            return false
        }

        return true
    }

    suspend fun readAuthenticationResponse(): Result<AuthenticationResponse> {
        val message = readMessage().getOrElse {
            println("Failed to read message entirely.")
            return Result.failure(Throwable("Failed to read message"))
        }

        if (message.messageType != MessageType.AUTHENTICATION.value) {
            println("Received unexpected message from server. Expected Authentication Response, got: ${message.messageType}")
            return Result.failure(Throwable("Received unexpected message from server. Expected Authentication Response, got: ${message.messageType}"))
        }

        // read integer to determine message status
        val authenticationStatus = message.message.readInt()
        when (authenticationStatus) {
            0 -> return Result.success(AuthenticationResponse.AuthenticationOk())
            3 -> return Result.success(AuthenticationResponse.CleartextPasswordRequest())
            5 -> {
                val salt = readString(message.message)
                return Result.success(AuthenticationResponse.Md5PasswordRequest(salt))
            }
            10 -> {
                val mechanism = readString(message.message)
                return Result.success(AuthenticationResponse.SaslAuthenticationRequest(mechanism))
            }
            11 -> {
                val saslData = readString(message.message)
                return Result.success(AuthenticationResponse.SaslAuthenticationContinue(saslData))
            }
            12 -> return Result.success(AuthenticationResponse.AuthenticationSASLFinal())
            else -> return Result.failure(Throwable("Unidentified authentication status value: $authenticationStatus"))
        }
    }

    /**
     * If the function returns normally, this implies that the ReadyForQuery response was received from the server
     */
    suspend fun readStartupResponse(): Result<StartupMessageResponse> {
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

    private suspend fun sendTerminationMessage() {
        val messageType = ByteArray(1)
        messageType[0] = 'X'.code.toByte()
        val terminationMessage = messageType + i32ToByteArray(4)
        sendChannel.writeFully(terminationMessage)
    }

    private suspend fun readMessage(): Result<PgWireMessage> {
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