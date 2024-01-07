import com.github.michaelbull.result.*
import org.ktpg.getRandomString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

//fun normalize(s: String): ByteArray = TODO()


fun main() {
    runBlocking {
        // change username to ryderhuggins for no password, secure1 for cleartext password, secure2 for SCRAM-SHA-256
        val pgConn = getConnection("127.0.0.1", 5432, "secure2", "password123", "postgres", emptyMap()).getOrThrow {
            Throwable(it.errorString)
        }
        println("Connection received")

        launch(Dispatchers.IO) {
            val rando = getRandomString(40)
            executeSimpleQuery(pgConn, "INSERT INTO links (url, name)\nVALUES('https://www.$rando.com','$rando value');")
            readSimpleQueryResponse(pgConn)
                .onFailure { println("Failed to run first query: $it") }
                .onSuccess { println("Simple query response: $it") }

            executeSimpleQuery(pgConn, "select * from links;")
            readSimpleQueryResponse(pgConn)
                .onFailure { println("Failed: $it") }
                .onSuccess { println("Simple query response: $it") }

            executeSimpleQuery(pgConn, "select fro links;")
            readSimpleQueryResponse(pgConn)
                .onFailure { bad -> println("Failed: $bad") }
                .onSuccess { good -> println("Simple query error response: $good") }

            executeSimpleQuery(pgConn, "")
            readSimpleQueryResponse(pgConn)
                .onFailure { println("Failed: $it") }
                .onSuccess { println("Simple query empty response: $it") }

            val rando2 = getRandomString(40)
            executeSimpleQuery(pgConn, "INSERT INTO links (url, name)\n" +
                                      "VALUES('https://www.$rando2.com','$rando2 value');\n" +
                                      "SELECT 1/0;\n" +
                                      "INSERT INTO links (url, name)\n" +
                                      "VALUES('https://www.$rando2.com','$rando2 value');")
            readSimpleQueryResponse(pgConn)
                .onFailure { println("Failed: $it") }
                .onSuccess { println("Simple multi statement failure response: $it") }

            val rando3 = getRandomString(40)
            val rando4 = getRandomString(40)
            executeSimpleQuery(pgConn, "INSERT INTO links (url, name)\n" +
                                      "VALUES('https://www.$rando3.com','$rando3 value');\n" +
                                      "COMMIT;\n" +
                                      "INSERT INTO links (url, name)\n" +
                                      "VALUES('https://www.$rando4.com','$rando4 value');\n" +
                                      "SELECT 1/0;")
            readSimpleQueryResponse(pgConn)
                .onFailure { println("Failed: $it") }
                .onSuccess { println("Simple multi statement commit response: $it") }

            val rando5 = getRandomString(40)
            val rando6 = getRandomString(40)
            executeSimpleQuery(pgConn, "INSERT INTO links (url, name)\n" +
                                      "VALUES('https://www.$rando5.com','$rando5 value');\n" +
                                      "INSERT INTO links (url, name)\n" +
                                      "VALUES('https://www.$rando6.com','$rando6 value');")
            readSimpleQueryResponse(pgConn)
                .onFailure { println("Failed: $it") }
                .onSuccess { println("Simple multi statement success response: $it") }

            executeSimpleQuery(pgConn, "select * from links limit 1;\nselect * from links limit 1;")
            readSimpleQueryResponse(pgConn)
                .onFailure { println("Failed: $it") }
                .onSuccess { println("Simple query multiSelect response: $it") }

            val rando7 = getRandomString(40)
            val rando8 = getRandomString(40)
            executeSimpleQuery(pgConn, "SELECT 1/0;\nCOMMIT;\n" + "INSERT INTO links (url, name)\n" +
                                      "VALUES('https://www.$rando7.com','$rando7 value');\n" +
                                      "COMMIT;\n" +
                                      "INSERT INTO links (url, name)\n" +
                                      "VALUES('https://www.$rando8.com','$rando8 value');\n")
            readSimpleQueryResponse(pgConn)
                .onFailure { println("Failed: $it") }
                .onSuccess { println("Simple multi statement fail then query response: $it") }

            close(pgConn)
        }

    }
}