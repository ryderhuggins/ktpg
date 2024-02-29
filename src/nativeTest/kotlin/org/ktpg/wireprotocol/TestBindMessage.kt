package io.github.ryderhuggins.ktpg.wireprotocol

import io.ktor.util.*
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.BindMessage
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.ParameterFormat
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.ParameterValue
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.serialize
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test

class TestBindMessage {
    
    @Test
    @ExperimentalNativeApi
    fun tesetBindMessage() {
        val parameterFormats = List(2, { ParameterFormat.TEXT })
        val bindMessage = BindMessage(
            portal="",
            statement="",
            parameterFormats=parameterFormats,
            parameterValues=listOf(ParameterValue.Text("pg_class"), ParameterValue.Text("hello"))
        )
        val bytes = serialize(bindMessage)
        assert(bytes.encodeBase64() == "QgAAACUAAAACAAAAAAACAAAACHBnX2NsYXNzAAAABWhlbGxvAAA=")
    }
}