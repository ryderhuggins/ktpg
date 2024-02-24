package org.ktpg

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