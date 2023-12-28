import com.github.ryderhuggins.AuthenticationResponse
import com.github.ryderhuggins.PgConnection
import kotlinx.coroutines.*

fun String.toAscii() = this.map { it.code.toByte() }

//fun normalize(s: String): ByteArray = TODO()


fun main() {
    runBlocking {
        // change username to ryderhuggins for no password, secure1 for cleartext password, secure2 for SCRAM-SHA-256
        val pgConn = PgConnection("127.0.0.1", 5432, "secure2", "postgres", emptyMap())

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
                is AuthenticationResponse.Md5PasswordRequest -> {
                    println("MD5 password requested from server with salt = ${res.salt} - TODO")
                    pgConn.sendMd5PasswordResponse("password123", res.salt)
                }
                is AuthenticationResponse.SaslAuthenticationRequest -> {
                    println("SASL authentication requested from server with mechanism - ${res.mechanism}")
                    // TODO: mechanism is actually a list of mechanisms
                    if (res.mechanism != "SCRAM-SHA-256") {
                        println("Error: Unsupported mechanism received from server - ${res.mechanism}")
                    }
                    pgConn.performSha256Authentication("password123")
                }
            }

            pgConn.readStartupResponse()

            // TODO: send a simple query!

            pgConn.close()
        }

    }
}