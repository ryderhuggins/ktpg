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

            val rando = getRandomString(40)
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

            pgConn.executeSimpleQuery("select fro links;")
            val errorRes = pgConn.readSimpleQueryResponse().getOrElse {
                println("Failed: $it")
            }
            println("Simple query error response: $errorRes")

            pgConn.executeSimpleQuery("")
            val emptyRes = pgConn.readSimpleQueryResponse().getOrElse {
                println("Failed: $it")
            }
            println("Simple query empty response: $emptyRes")

            val rando2 = getRandomString(40)
            pgConn.executeSimpleQuery("INSERT INTO links (url, name)\n" +
                    "VALUES('https://www.$rando2.com','$rando2 value');\n" +
                    "SELECT 1/0;\n" +
                    "INSERT INTO links (url, name)\n" +
                    "VALUES('https://www.$rando2.com','$rando2 value');")
            val multiStatementFail = pgConn.readSimpleQueryResponse().getOrElse {
                println("Failed: $it")
            }
            println("Simple multi statement failure response: $multiStatementFail")

            val rando3 = getRandomString(40)
            val rando4 = getRandomString(40)
            pgConn.executeSimpleQuery("INSERT INTO links (url, name)\n" +
                    "VALUES('https://www.$rando3.com','$rando3 value');\n" +
                    "COMMIT;\n" +
                    "INSERT INTO links (url, name)\n" +
                    "VALUES('https://www.$rando4.com','$rando4 value');\n" +
                    "SELECT 1/0;")
            val multiStatementCommit = pgConn.readSimpleQueryResponse().getOrElse {
                println("Failed: $it")
            }
            println("Simple multi statement commit response: $multiStatementCommit")

            val rando5 = getRandomString(40)
            val rando6 = getRandomString(40)
            pgConn.executeSimpleQuery("INSERT INTO links (url, name)\n" +
                    "VALUES('https://www.$rando5.com','$rando5 value');\n" +
                    "INSERT INTO links (url, name)\n" +
                    "VALUES('https://www.$rando6.com','$rando6 value');")
            val mutliStatementSuccess = pgConn.readSimpleQueryResponse().getOrElse {
                println("Failed: $it")
            }
            println("Simple multi statement success response: $mutliStatementSuccess")

            pgConn.executeSimpleQuery("select * from links limit 1;\nselect * from links limit 1;")
            val multiSelect = pgConn.readSimpleQueryResponse().getOrElse {
                println("Failed: $it")
            }
            println("Simple query multiSelect response: $multiSelect")

            val rando7 = getRandomString(40)
            val rando8 = getRandomString(40)
            pgConn.executeSimpleQuery("SELECT 1/0;\nCOMMIT;\n" + "INSERT INTO links (url, name)\n" +
                    "VALUES('https://www.$rando7.com','$rando7 value');\n" +
                    "COMMIT;\n" +
                    "INSERT INTO links (url, name)\n" +
                    "VALUES('https://www.$rando8.com','$rando8 value');\n")
            val failThenQuery = pgConn.readSimpleQueryResponse().getOrElse {
                println("Failed: $it")
            }
            println("Simple multi statement fail then query response: $failThenQuery")

            pgConn.close()
        }

    }
}