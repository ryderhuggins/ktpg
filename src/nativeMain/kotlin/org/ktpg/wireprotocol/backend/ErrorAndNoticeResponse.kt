package org.ktpg.wireprotocol.backend

import io.ktor.utils.io.core.*
import org.ktpg.wireprotocol.readString

internal fun parseErrorOrNoticeResponseMessage(messageBytes: ByteReadPacket): Map<String, String> {
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