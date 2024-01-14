package org.ktpg.wireprotocol

import org.ktpg.i32ToByteArray
import org.ktpg.toAscii

internal data class StartupMessage(val protocolVersion: Byte, val parameters: Map<String, String>)

internal fun serialize(startupMessage: StartupMessage): ByteArray {
    val size = startupMessage.parameters.asIterable().sumOf { it.key.length + 1 + it.value.length + 1 }  + 9
    println("Sending startup message with size: ")
    return i32ToByteArray(size) + 0x0 + startupMessage.protocolVersion + 0x0 + 0x0 + startupMessage.parameters.asIterable().flatMap { it.key.toAscii() + 0x0 + it.value.toAscii() + 0x0 } + 0x0
}

