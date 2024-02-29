package io.github.ryderhuggins.ktpg.wireprotocol

import io.ktor.utils.io.core.*

fun String.toAscii() = this.map { it.code.toByte() }

internal fun i32ToByteArray(n: Int): ByteArray =
    (3 downTo 0).map {
        (n shr (it * Byte.SIZE_BITS)).toByte()
    }.toByteArray()

internal fun Short.toByteArray(): ByteArray =  byteArrayOf(((this.toInt() and 0xFF00) shr (8)).toByte(), (this.toInt() and 0x00FF).toByte())

fun getRandomString(length: Int) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
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