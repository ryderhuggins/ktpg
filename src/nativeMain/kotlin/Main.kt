import com.github.ryderhuggins.AuthenticationResponse
import com.github.ryderhuggins.PgConnection
import kotlinx.coroutines.*

fun String.toAscii() = this.map { it.code.toByte() }

fun main() {
    runBlocking {
        // change username to ryderhuggins for no password, secure1 for cleartext password
        val pgConn = PgConnection("127.0.0.1", 5432, "secure1", "postgres", emptyMap())

        launch(Dispatchers.IO) {
            // normally we'd loop while true here to read and print
            pgConn.initialize()
            pgConn.sendStartupMessage()
            val res = pgConn.readAuthenticationResponse().getOrElse {
                println("Failed to read auth response with error: $it")
                pgConn.close()
            }
            when (res) {
                is AuthenticationResponse.AuthenticationOk -> println("Authentication succeeded - no password required")
                is AuthenticationResponse.CleartextPasswordRequest -> {
                    println("Password requested from server")
                    pgConn.sendCleartextPasswordResponse("password123")
                }
                is AuthenticationResponse.Md5PasswordRequest -> println("MD5 password requested from server with salt = ${res.salt} - TODO")
            }
            // this should return a status indicating success or request for password or md5-hashed password
            pgConn.readStartupResponse()
            pgConn.close()
        }

    }
}