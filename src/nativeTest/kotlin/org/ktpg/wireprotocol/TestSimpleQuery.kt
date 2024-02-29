package io.github.ryderhuggins.ktpg.wireprotocol

import io.ktor.util.*
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.SimpleQuery
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.serialize
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test


class TestSimpleQuery {
    
    @Test
    @ExperimentalNativeApi
    fun testSerializeSimpleQuery() {
        val simpleQuery: SimpleQuery = "select * from links;"
        val bytes = serialize(simpleQuery)
        assert(bytes.encodeBase64() == "UQAAABlzZWxlY3QgKiBmcm9tIGxpbmtzOwA=")
    }
}