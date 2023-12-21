package com.github.ryderhuggins

import io.ktor.utils.io.core.*

internal fun i32ToByteArray(n: Int): ByteArray =
    (3 downTo 0).map {
        (n shr (it * Byte.SIZE_BITS)).toByte()
    }.toByteArray()

data class PgWireMessage(val messageType: Char, val message: ByteReadPacket)

data class StartupMessageResponse(val parameterStatus: Map<String, String>, val backendKeyData: Map<String, Int>)

enum class MessageType(val value: Char) {
    BACKEND_KEY_DATA('K'),
    ERROR_RESPONSE('E'),
    AUTHENTICATION('R'),
    PARAMETER_STATUS('S'),
    READY_FOR_QUERY('Z')
}

fun readString(packet: ByteReadPacket): String {
    var current: Int = packet.readByte().toInt()

    if (current.toInt() == 0) {
        return ""
    }

    var s = StringBuilder()
    while(current != 0) {
        s.append(current.toChar())
        current = packet.readByte().toInt()
    }

    return s.toString()
}