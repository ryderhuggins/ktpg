package io.github.ryderhuggins.ktpg.wireprotocol

import io.ktor.util.*
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.ParseMessage
import io.github.ryderhuggins.ktpg.wireprotocol.frontend.serialize
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test

class TestParseMessage {
    @Test
    @ExperimentalNativeApi
    fun testSerializeSimpleQuery() {
        val parseMessage = ParseMessage(
            "",
            "select table_name, table_type from information_schema.tables where table_name = $1 and table_type = $2",
            listOf(PgTypes.VARCHAR, PgTypes.VARCHAR)
        )
        println("Parsemessage: $parseMessage")
        val bytes = serialize(parseMessage)
        assert(bytes.encodeBase64() == "UAAAAHYAc2VsZWN0IHRhYmxlX25hbWUsIHRhYmxlX3R5cGUgZnJvbSBpbmZvcm1hdGlvbl9zY2hlbWEudGFibGVzIHdoZXJlIHRhYmxlX25hbWUgPSAkMSBhbmQgdGFibGVfdHlwZSA9ICQyAAACAAAEEwAABBM=")
    }

}