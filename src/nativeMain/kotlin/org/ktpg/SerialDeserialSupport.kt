package org.ktpg

import io.ktor.utils.io.core.*

internal fun i32ToByteArray(n: Int): ByteArray =
    (3 downTo 0).map {
        (n shr (it * Byte.SIZE_BITS)).toByte()
    }.toByteArray()


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
        val dataRows: List<Map<String,String>>,
        val error: Map<String,String>,
        val notice: Map<String, String>
    )

data class SimpleQueryError(val errorString: String)

fun getRandomString(length: Int) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}