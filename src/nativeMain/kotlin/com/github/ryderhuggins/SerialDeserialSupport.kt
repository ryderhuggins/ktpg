package com.github.ryderhuggins

import io.ktor.utils.io.core.*

internal fun i32ToByteArray(n: Int): ByteArray =
    (3 downTo 0).map {
        (n shr (it * Byte.SIZE_BITS)).toByte()
    }.toByteArray()

data class PgWireMessage(val messageType: Char, val messageBytes: ByteReadPacket)

data class StartupMessageResponse(val parameterStatus: Map<String, String>, val backendKeyData: Map<String, Int>)

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

/**
 * This will read a string until null termination or until end of packet data
 */
fun readString(packet: ByteReadPacket): String {
    var current: Int = packet.readByte().toInt()

    if (current.toInt() == 0) {
        return ""
    }

    var s = StringBuilder()
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

fun readString(packet: ByteReadPacket, limit: Int): String {
    var current: Int = 0

    var s = StringBuilder()
    var count = 0
    do {
        current = packet.readByte().toInt()
        s.append(current.toChar())
        count++
    } while(current != 0 && !packet.endOfInput && count < limit)

    return s.toString()
}

sealed class AuthenticationResponse {
    class AuthenticationOk : AuthenticationResponse()
    class CleartextPasswordRequest : AuthenticationResponse()
    class Md5PasswordRequest(val salt: String) : AuthenticationResponse()
    class SaslAuthenticationRequest(val mechanism: String) : AuthenticationResponse()
    class SaslAuthenticationContinue(val saslData: String) : AuthenticationResponse()
    class AuthenticationSASLFinal() : AuthenticationResponse()
    // TODO other authentication schemes
}

sealed class QueryResponse {
    data class CommandComplete(val commandTag: String) : QueryResponse()
    class CopyInResponse : QueryResponse()
    class CopyOutResponse : QueryResponse()
    class RowDescription : QueryResponse()
    class DataRow : QueryResponse()
    class EmptyQueryResponse : QueryResponse()
    class ErrorResponse : QueryResponse()
    class ReadyForQuery : QueryResponse()
    class NoticeResponse : QueryResponse()
}

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
        val dataRows: List<Map<String,String>>
    )

fun getRandomString(length: Int) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}