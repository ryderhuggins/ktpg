import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.kotlincrypto.hash.md.MD5

import com.github.michaelbull.result.*


import org.ktpg.*

fun String.toAscii() = this.map { it.code.toByte() }

sealed interface PgConnectionResult

data class PgConnection internal constructor(
    val host: String,
    val port: Int,
    val user: String,
    internal val password: String,
    val database: String,
    val optionalParameters: Map<String, String>,
    internal val socket: Socket,
    internal val receiveChannel: ByteReadChannel,
    internal val sendChannel: ByteWriteChannel,
    internal val selectorManager: SelectorManager
) : PgConnectionResult

data class PgConnectionFailure internal constructor(
    val errorString: String
) : PgConnectionResult

data class PgWireMessage(val messageType: Char, val messageBytes: ByteReadPacket)

sealed interface AuthenticationResponse
data object AuthenticationOk : AuthenticationResponse
data object CleartextPasswordRequest : AuthenticationResponse
data class Md5PasswordRequest(val salt: String) : AuthenticationResponse
data class SaslAuthenticationRequest(val mechanism: String) : AuthenticationResponse
data class SaslAuthenticationContinue(val saslData: String) : AuthenticationResponse
data object AuthenticationSASLFinal : AuthenticationResponse
data class AuthenticationFailure(val errorString: String) : AuthenticationResponse
// TODO other authentication schemes

sealed interface StartupResponse
data class StartupParameters(val parameterStatusMap: Map<String,String>, val backendKeyDataMap: Map<String,Int>) : StartupResponse
data class StartupFailure(val errorString: String) : StartupResponse

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
    NOTICE_RESPONSE('N')
}

// TODO: use some kind of result type for this
suspend fun getConnection(host: String, port: Int, user: String, password: String, database: String, optionalParameters: Map<String, String>): Result<PgConnection, PgConnectionFailure> {
    try {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect(host, port)
        val receiveChannel = socket.openReadChannel()
        val sendChannel = socket.openWriteChannel(autoFlush = true)
        val conn = PgConnection(
            host=host,
            port=port,
            user=user,
            password=password,
            database=database,
            optionalParameters=optionalParameters,
            socket=socket,
            receiveChannel=receiveChannel,
            sendChannel=sendChannel,
            selectorManager=selectorManager
        )

        return if (connect(conn)) {
            Ok(conn)
        } else {
            Err(PgConnectionFailure("Failed in startup message exchange with postgres server"))
        }

    } catch(e: Exception) {
        return Err(PgConnectionFailure(e.message ?: "Null exception string while creating connection"))
    }
}

internal fun i32ToByteArray(n: Int): ByteArray =
    (3 downTo 0).map {
        (n shr (it * Byte.SIZE_BITS)).toByte()
    }.toByteArray()

private suspend fun sendStartupMessage(pgConn: PgConnection) {
    val parameters = listOf("user", pgConn.user, "database", pgConn.database)
    val size = parameters.sumOf { it.length + 1 } + 9 // 4 for protocol version, 4 for size, 1 for extra null byte
    val startupMessage = i32ToByteArray(size) + 0x0 + 0x03 + 0x0 + 0x0 + parameters.flatMap { it.toAscii() + 0x0 } + 0x0
    pgConn.sendChannel.writeFully(startupMessage)
}

/**
 * TODO - can this throw any exceptions? I don't see any on GitHub
 */
private suspend fun readMessage(pgConn: PgConnection): PgWireMessage {
    val messageType = pgConn.receiveChannel.readByte().toInt().toChar()
    val messageSize = pgConn.receiveChannel.readInt()
    val message = pgConn.receiveChannel.readRemaining((messageSize-4).toLong())
    return PgWireMessage(messageType, message)
}

private suspend fun readAuthenticationResponse(pgConn: PgConnection): AuthenticationResponse {
    val message = readMessage(pgConn)

    if (message.messageType != MessageType.AUTHENTICATION.value) {
        println("Received unexpected message from server. Expected Authentication Response, got: ${message.messageType}")
        return AuthenticationFailure("Received unexpected message from server. Expected Authentication Response, got: ${message.messageType}")
    }

    // read integer to determine message status
    val authenticationStatus = message.messageBytes.readInt()
    when (authenticationStatus) {
        0 -> return AuthenticationOk
        3 -> return CleartextPasswordRequest
        5 -> {
            val salt = readString(message.messageBytes)
            return Md5PasswordRequest(salt)
        }
        10 -> {
            val mechanism = readString(message.messageBytes)
            return SaslAuthenticationRequest(mechanism)
        }
        11 -> {
            val saslData = readString(message.messageBytes)
            return SaslAuthenticationContinue(saslData)
        }
        12 -> return AuthenticationSASLFinal
        else -> return AuthenticationFailure("Unidentified authentication status value: $authenticationStatus")
    }
}

private suspend fun sendCleartextPasswordResponse(pgConn: PgConnection): AuthenticationResponse {
    val messageType = ByteArray(1)
    messageType[0] = 'p'.code.toByte()
    val sizeOf = pgConn.password.length + 1 + 4
    val cleartextPasswordMessage = messageType + i32ToByteArray(sizeOf) + pgConn.password.toAscii() + 0x0
    pgConn.sendChannel.writeFully(cleartextPasswordMessage)

    val authenticationOk = readAuthenticationResponse(pgConn)
    if (authenticationOk !is AuthenticationOk) {
        println("Unexpected message type in cleartext password authentication process. Expected AuthenticationOk. Got: $authenticationOk")
        return AuthenticationFailure("Unexpected message type in cleartext password authentication process. Expected AuthenticationOk. Got: $authenticationOk")
    }

    return authenticationOk
}

private suspend fun sendMd5PasswordResponse(pgConn: PgConnection, salt: String) {
    val messageType = ByteArray(1)
    messageType[0] = 'p'.code.toByte()
    val inner = MD5().digest("$pgConn.password$pgConn.user".toByteArray())
    val passwordHash = "md5".toByteArray() + MD5().digest(inner + salt.toByteArray())
    val sizeOf = pgConn.password.length + 4
    val cleartextPasswordMessage = messageType + i32ToByteArray(sizeOf) + passwordHash
    pgConn.sendChannel.writeFully(cleartextPasswordMessage)
}

private suspend fun performScramSha256Authentication(pgConn: PgConnection): AuthenticationResponse {
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
    pgConn.sendChannel.writeFully(saslInitialResponseMessage)
    println("Done sending scram-sha-256 initial response")

    val saslContinuationMessage = readAuthenticationResponse(pgConn)
    if (saslContinuationMessage !is SaslAuthenticationContinue) {
        println("Unexpected message type")
        return AuthenticationFailure("Unexpected message type in SCRAM-SHA-256 authentication process. Message type: $saslContinuationMessage")
    }

    println("sasl continuation data: ${saslContinuationMessage.saslData}")
    // TODO: here we need to check that the first part of the 'r' value is equal to the clientFirstData random string
    // s is the base64-encoded salt
    // i is the iteration count
    val serverFirstMessage = parseServerFirstMessage(saslContinuationMessage.saslData).getOrElse {
        return AuthenticationFailure(it.message ?: "Null error message from parseServerFirstMessage")
    }

    val clientFinalMessageText = getScramClientFinalMessage(pgConn.password, serverFirstMessage.r, serverFirstMessage.s, serverFirstMessage.i, clientFirstMessageBare, saslContinuationMessage.saslData)
    val clientFinalMessageSize = 4 + clientFinalMessageText.length
    val clientFinalMessageType = ByteArray(1)
    clientFinalMessageType[0] = 'p'.code.toByte()
    val finalMessage = clientFinalMessageType + i32ToByteArray(clientFinalMessageSize) + clientFinalMessageText.toAscii()
    pgConn.sendChannel.writeFully(finalMessage)
    println("Done sending client final message")

    val saslFinalResponse = readAuthenticationResponse(pgConn)
    if (saslFinalResponse !is AuthenticationSASLFinal) {
        println("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationSASLFinal. Got: $saslContinuationMessage")
        return AuthenticationFailure("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationSASLFinal. Got: $saslContinuationMessage")
    }

    val authenticationOk = readAuthenticationResponse(pgConn)
    if (authenticationOk !is AuthenticationOk) {
        println("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationOk. Got: $saslContinuationMessage")
        return AuthenticationFailure("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationOk. Got: $saslContinuationMessage")
    }

    return authenticationOk
}

private suspend fun readStartupResponse(pgConn: PgConnection): StartupResponse {
    // in this scenario, we should just read:
    // - AuthenticationOk, a list of ParameterStatus, a list of BackendKeyData, and ReadyForQuery
    // then return the two lists or an error
    val parameterStatusMap = mutableMapOf<String, String>()
    val backendKeyDataMap = mutableMapOf<String, Int>()

    var message = readMessage(pgConn)

    // setting an upper bound for sanity's sake
    var i = 0
    while (i++ < 200) {
        when(message.messageType) {
            MessageType.ERROR_RESPONSE.value -> {
                println("Got ErrorResponse message from server")
                return StartupFailure("Got ErrorResponse message from server")
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
                return StartupParameters(parameterStatusMap, backendKeyDataMap)
            }
        }
        message = readMessage(pgConn)
    }

    if (i == 200) {
        println("Exhausted loop iterations!")
    }
    return StartupFailure("oof")
}

// TODO: need to flesh this out for e.g. incorrect password
private suspend fun connect(pgConn: PgConnection): Boolean {
    sendStartupMessage(pgConn)
    val res = readAuthenticationResponse(pgConn)
    when (res) {
        is AuthenticationOk -> println("Authentication succeeded - no password required")
        is CleartextPasswordRequest -> {
            println("Password requested from server")
            val authResponse = sendCleartextPasswordResponse(pgConn)
            if (authResponse !is AuthenticationOk) {
                close(pgConn)
                return false
            }
        }
        is Md5PasswordRequest -> {
            println("MD5 password requested from server with salt = ${res.salt}")
            sendMd5PasswordResponse(pgConn, res.salt)
        }
        is SaslAuthenticationRequest -> {
            println("SASL authentication requested from server with mechanism - ${res.mechanism}")
            // TODO: mechanism is actually a list of mechanisms
            if (res.mechanism != "SCRAM-SHA-256") {
                println("Error: Unsupported mechanism received from server - ${res.mechanism}")
            }
            val authResponse = performScramSha256Authentication(pgConn)
            if (authResponse !is AuthenticationOk) {
                close(pgConn)
                return false
            }
        }
        else -> return false
    }

    return true
}

private suspend fun sendTerminationMessage(pgConn: PgConnection) {
    val messageType = ByteArray(1)
    messageType[0] = 'X'.code.toByte()
    val terminationMessage = messageType + i32ToByteArray(4)
    pgConn.sendChannel.writeFully(terminationMessage)
}

internal suspend fun close(pgConn: PgConnection) {
    sendTerminationMessage(pgConn)
    pgConn.socket.close()
    pgConn.selectorManager.close()
}

private fun parseErrorResponseMessage(messageBytes: ByteReadPacket): Map<String, String> {
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

internal fun getRandomString(length: Int) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

suspend fun readSimpleQueryResponse(pgConn: PgConnection): Result<List<SimpleQueryResponse>, SimpleQueryError> {
    var message: PgWireMessage
    // we want to populate a list of columns and list of rows
    var columns = mutableListOf<ColumnDescriptor>()
    var dataRows = mutableListOf<Map<String, String>>()
    var commandTag = ""
    var notices = mapOf<String,String>()
    var result = mutableListOf<SimpleQueryResponse>()

    repeat(10000) {
        message = readMessage(pgConn)

        when(message.messageType) {
            MessageType.ROW_DESCRIPTION.value -> {
                println("Parsing Row Description message...")
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
                    println("Parsed column descriptor: $c")
                    columns.add(c)
                }
            }
            MessageType.DATA_ROW.value -> {
                println("Parsing Row Data message...")
                val columnCount = message.messageBytes.readShort()
                val dataRow = mutableMapOf<String,String>()

                fields@ for (i in 0..<columnCount) {
                    val fieldLength = message.messageBytes.readInt()
                    if (fieldLength == -1) {
                        println("Field length -1. Continuing to next iteration...")
                        continue@fields
                    }
                    println("Reading value of length: $fieldLength")
                    val columnValue = readString(message.messageBytes, fieldLength)
                    println("Parsed column value: $columnValue")
                    dataRow[columns[i].name] = columnValue
                }
                dataRows.add(dataRow)
                println("Parsed data row: $dataRow")
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
                val err = parseErrorResponseMessage(message.messageBytes)
                println("Received error message from server: $err")
                result.add(SimpleQueryResponse(commandTag, columns, dataRows, err, notices))
                commandTag = ""
                columns = mutableListOf<ColumnDescriptor>()
                dataRows = mutableListOf<Map<String, String>>()
                notices = mutableMapOf<String,String>()
            }
            MessageType.NOTICE_RESPONSE.value -> {
                notices = parseErrorResponseMessage(message.messageBytes)
                println("Received warning message from server: $notices")
            }
            MessageType.READY_FOR_QUERY.value -> {
                val status = message.messageBytes.readByte().toInt().toChar()
                println("Received Ready For Query with status: $status")
                return Ok(result)
            }
        }
    }
    return Err(SimpleQueryError("Exhausted loop counter when reading simple query response..."))
}

suspend fun executeSimpleQuery(pgConn: PgConnection, sql: String) {
    // send string
    val messageType = ByteArray(1)
    messageType[0] = 'Q'.code.toByte()
    val queryMessage = messageType + i32ToByteArray(4 + sql.length + 1) + sql.toAscii() + 0x0
    pgConn.sendChannel.writeFully(queryMessage)
}