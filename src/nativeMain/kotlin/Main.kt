import com.github.ryderhuggins.PgConnection
import kotlinx.coroutines.*

fun String.toAscii() = this.map { it.code.toByte() }

fun main() {
    runBlocking {
        val pgConn = PgConnection("127.0.0.1", 5432, "ryderhuggins", "postgres", emptyMap())

        launch(Dispatchers.IO) {
            // normally we'd loop while true here to read and print
            pgConn.initialize()
            pgConn.sendStartupMessageNoAuthentication()
            pgConn.readStartupResponseNoAuthentication()
            pgConn.close();
        }

    }
}