package org.ktpg.wireprotocol

import io.ktor.utils.io.core.*
import org.ktpg.i32ToByteArray
import org.ktpg.toByteArray

enum class ParameterFormat(val format: Short) {
    TEXT(0),
    BINARY(1)
}

sealed interface ParameterValue {
    data class Text(val text: String) : ParameterValue
    data class Integer(val integer: Int) : ParameterValue
    data class SmallInt(val smallInt: Short) : ParameterValue
    data class VarChar(val varChar: String) : ParameterValue
    data class TimestampZ(val timestampZ: String) : ParameterValue
    data class Numeric(val numeric: Double) : ParameterValue
}

// TODO - THIS ONLY SERIALIZES TO TEXT
internal fun serializeParameterValue(parameterValue: ParameterValue): ByteArray {
    return when (parameterValue) {
        is ParameterValue.Text -> i32ToByteArray(parameterValue.text.length) + parameterValue.text.toByteArray()
        is ParameterValue.Integer -> i32ToByteArray(parameterValue.integer.toString().length) + parameterValue.integer.toString().toByteArray()
        is ParameterValue.TimestampZ -> i32ToByteArray(parameterValue.timestampZ.length) + parameterValue.timestampZ.toByteArray()
        is ParameterValue.Numeric -> i32ToByteArray(parameterValue.numeric.toString().length) + parameterValue.numeric.toString().toByteArray() // TODO this will probably fail with really large numbers
        else -> byteArrayOf()
    }
}

internal data class BindMessage(
    val portal: String,
    val statement: String,
    val parameterFormats: List<ParameterFormat>,
    val parameterValues: List<ParameterValue>
)

internal fun serialize(bindMessage: BindMessage): ByteArray {
    val messageType = ByteArray(1)
    messageType[0] = 'B'.code.toByte()

    val serializedPortalName = if(bindMessage.portal.isEmpty()) {
        byteArrayOf(0x0) // TODO: should this be two 0x0's or one? 
    } else {
        bindMessage.portal.toByteArray() + 0x0
    }
    
    val serializedStatementName = if(bindMessage.statement.isEmpty()) {
        byteArrayOf(0x0) // TODO: should this be two 0x0's or one? 
    } else {
        bindMessage.statement.toByteArray() + 0x0
    }
    
    // serialize parameter formats
    val serializedParameterFormats = if (bindMessage.parameterFormats.isEmpty()) {
        byteArrayOf()
    } else {
        bindMessage.parameterFormats
            .map { it.format.toByteArray() }
            .reduce { acc, bytes -> acc + bytes }
    }
    
    // serialize parameter values
    val serializedParameterValues = if (bindMessage.parameterValues.isEmpty()) {
        byteArrayOf()
    } else {
        bindMessage.parameterValues
            .map { serializeParameterValue(it) }
            .reduce { acc, bytes -> acc + bytes }
    }
    
    val size = 4 + serializedPortalName.size + serializedStatementName.size + 2 + serializedParameterFormats.size + 2 + serializedParameterValues.size + 2
    // TODO - the + 2 at the end corresponds to the 0x0 at the end of this function
    return messageType +
           i32ToByteArray(size) +
           serializedPortalName +
           serializedStatementName +
           bindMessage.parameterFormats.size.toShort().toByteArray() +
           serializedParameterFormats +
           bindMessage.parameterValues.size.toShort().toByteArray() +
           serializedParameterValues +
           0x0.toShort().toByteArray() // TODO - this is the result column format code -> this one is for TEXT by default
}