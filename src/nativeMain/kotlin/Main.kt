import com.github.ryderhuggins.AuthenticationResponse
import com.github.ryderhuggins.PgConnection
import com.github.ryderhuggins.i32ToByteArray
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import kotlin.experimental.xor

fun String.toAscii() = this.map { it.code.toByte() }

//fun normalize(s: String): ByteArray = TODO()

fun xorByteArrays(b1: ByteArray, b2: ByteArray): ByteArray {
    val res = ByteArray(b1.size)
    for (i in b1.indices) {
        res[i] = b1[i] xor b2[i]
    }
    return res
}

fun getSaltedPassword(normalizedPassword: ByteArray, salt: ByteArray, i: Int): ByteArray {
    val ui = mutableListOf<ByteArray>()
    var hmac = HmacSHA256(normalizedPassword)
    var prev = hmac.doFinal(salt + i32ToByteArray(1))
    ui.add(prev)
    var curr = prev
    for (iter in 1..<i) {
        curr = hmac.doFinal(prev)
        ui.add(curr)
        prev = curr
    }

    // now we need to XOR each of those to produce the salted password
    var res = xorByteArrays(ui[0], ui[1])
    for (idx in 2..<ui.size) {
        res = xorByteArrays(res, ui[idx])
    }
    return res
}

fun getClientKey(saltedPassword: ByteArray): ByteArray {
    return HmacSHA256(saltedPassword).doFinal("Client Key".toByteArray())
}

fun getStoredKey(clientKey: ByteArray): ByteArray {
    return SHA256().digest(clientKey)
}

fun getAuthMessage(clientFirstMessage: String, serverFirstMessage: String, clientFinalMessageWithoutProof: String): ByteArray {
    val x = clientFirstMessage + "," + serverFirstMessage + "," + clientFinalMessageWithoutProof
    println("auth message: $x")
    return x.toByteArray()
}

fun getClientSignature(storedKey: ByteArray, authMessage: ByteArray): ByteArray {
    return HmacSHA256(storedKey).doFinal(authMessage)
}

fun getClientProof(clientKey: ByteArray, clientSignature: ByteArray): ByteArray {
    return xorByteArrays(clientKey, clientSignature)
}


@OptIn(ExperimentalStdlibApi::class)
fun main() {

    var hmac = HmacSHA256("12345".toByteArray())
    val res = hmac.doFinal("sample message".toByteArray())
    println("hmac res: ${res.toHexString()}")

    // test
    val r="fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j"
    val s="QSXCR+Q6sek8bf92".decodeBase64Bytes()
    val i=4096

    val saltedPassword = getSaltedPassword("pencil".toByteArray(), s, i)
    println("salted password: ${saltedPassword.toHexString()}")

    val clientKey = getClientKey(saltedPassword)
    println("client key: ${clientKey.toHexString()}")

    val storedKey = getStoredKey(clientKey)
    println("stored key: ${storedKey.toHexString()}")

    val clientFirstMessage = "n=user,r=fyko+d2lbbFgONRv9qkxdawL"
    val serverFirstMessage = "r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j,s=QSXCR+Q6sek8bf92,i=4096"
    val clientFinalMessageWithoutProof = "c=biws,r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j"

    val authMessage = getAuthMessage(clientFirstMessage, serverFirstMessage, clientFinalMessageWithoutProof)
    println("auth message: ${authMessage.toHexString()}")

    val clientSignature = getClientSignature(storedKey, authMessage)
    println("client signature: ${clientSignature.toHexString()}")

    val clientProof = getClientProof(clientKey, clientSignature)
    println("client proof: ${clientProof.toHexString()}")
    println("client proof: ${clientProof.encodeBase64()}")

    if (true) {
        return
    }
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

//            val res2 = pgConn.readAuthenticationResponse().getOrElse {
//                println("Failed to read SASL continue: $it")
//                pgConn.close()
//            }
            // this should return a status indicating success or request for password or md5-hashed password
            pgConn.readStartupResponse()
            pgConn.close()
        }

    }
}