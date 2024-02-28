package org.ktpg.wireprotocol.frontend

import org.ktpg.wireprotocol.i32ToByteArray
import org.ktpg.wireprotocol.toAscii

internal data class StartupMessage(val protocolVersion: Byte, val parameters: Map<String, String>)

internal fun StartupMessage.serialize(): ByteArray {
    val size = this.parameters.asIterable().sumOf { it.key.length + 1 + it.value.length + 1 }  + 9
    return i32ToByteArray(size) + 0x0 + this.protocolVersion + 0x0 + 0x0 + this.parameters.asIterable().flatMap { it.key.toAscii() + 0x0 + it.value.toAscii() + 0x0 } + 0x0
}

