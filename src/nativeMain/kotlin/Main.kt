import com.github.ryderhuggins.AuthenticationResponse
import com.github.ryderhuggins.PgConnection
import com.github.ryderhuggins.getRandomString
import kotlinx.coroutines.*

fun String.toAscii() = this.map { it.code.toByte() }

//fun normalize(s: String): ByteArray = TODO()


fun main() {
    runBlocking {
        // change username to ryderhuggins for no password, secure1 for cleartext password, secure2 for SCRAM-SHA-256
        val pgConn = PgConnection("127.0.0.1", 5432, "secure2", "password123", "postgres", emptyMap())

        launch(Dispatchers.IO) {
            // normally we'd loop while true here to read and print
            pgConn.connect()

            val rando = getRandomString(10)
            pgConn.executeSimpleQuery("INSERT INTO links (url, name)\nVALUES('https://www.$rando.com','$rando value');")
            val insertRes = pgConn.readSimpleQueryResponse().getOrElse {
                println("Failed: $it")
            }
            println("Simple query response: $insertRes")

            pgConn.executeSimpleQuery("select * from links;")
            val res = pgConn.readSimpleQueryResponse().getOrElse {
                println("Failed: $it")
            }
            println("Simple query response: $res")

            pgConn.close()
        }

    }
}