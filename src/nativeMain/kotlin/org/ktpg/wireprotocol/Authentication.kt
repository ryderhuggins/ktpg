package org.ktpg.wireprotocol

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.kotlincrypto.hash.md.MD5
import org.ktpg.*
import org.ktpg.getScramClientFinalMessage
import org.ktpg.i32ToByteArray
import org.ktpg.parseServerFirstMessage
import org.ktpg.readString

// TODO: need to flesh this out for e.g. incorrect password
internal suspend fun startupConnection(pgConn: PgConnection): Result<StartupParameters, StartupFailure> {
    sendStartupMessage(pgConn.sendChannel, pgConn.user, pgConn.database)
    val res = readAuthenticationResponse(pgConn.receiveChannel)
    when (res) {
        is AuthenticationOk -> println("Authentication succeeded - no password required")
        is CleartextPasswordRequest -> {
            println("Password requested from server")
            val authResponse = performCleartextPasswordAuthentication(pgConn)
            if (authResponse !is AuthenticationOk) {
                return Err(StartupFailure("$res"))
            }
        }
        is Md5PasswordRequest -> {
            println("MD5 password requested from server with salt = ${res.salt}")
            sendMd5PasswordResponse(pgConn, res.salt)
        }
        is SaslAuthenticationRequest -> {
            println("SASL authentication requested from server with mechanism - ${res.mechanism}")
            // TODO: mechanism is actually a list of mechanisms
            if (res.mechanism != "SCRAM-SHA-256") {
                println("Error: Unsupported mechanism received from server - ${res.mechanism}")
            }
            val authResponse = performScramSha256Authentication(pgConn)
            if (authResponse !is AuthenticationOk) {
                return Err(StartupFailure("$res"))
            }
        }
        else -> return Err(StartupFailure("$res"))
    }

    return readStartupResponse(pgConn.receiveChannel)
}

private suspend fun readAuthenticationResponse(receiveChannel: ByteReadChannel): AuthenticationResponse {
    val message = readMessage(receiveChannel)

    if (message.messageType != MessageType.AUTHENTICATION.value) {
        println("Received unexpected message from server. Expected Authentication Response, got: ${message.messageType}")
        return AuthenticationFailure("Received unexpected message from server. Expected Authentication Response, got: ${message.messageType}")
    }

    // read integer to determine message status
    val authenticationStatus = message.messageBytes.readInt()
    when (authenticationStatus) {
        0 -> return AuthenticationOk
        3 -> return CleartextPasswordRequest
        5 -> {
            val salt = readString(message.messageBytes)
            return Md5PasswordRequest(salt)
        }
        10 -> {
            val mechanism = readString(message.messageBytes)
            return SaslAuthenticationRequest(mechanism)
        }
        11 -> {
            val saslData = readString(message.messageBytes)
            return SaslAuthenticationContinue(saslData)
        }
        12 -> return AuthenticationSASLFinal
        else -> return AuthenticationFailure("Unidentified authentication status value: $authenticationStatus")
    }
}

private suspend fun performCleartextPasswordAuthentication(pgConn: PgConnection): AuthenticationResponse {
    val messageType = ByteArray(1)
    messageType[0] = 'p'.code.toByte()
    val sizeOf = pgConn.password.length + 1 + 4
    val cleartextPasswordMessage = messageType + i32ToByteArray(sizeOf) + pgConn.password.toAscii() + 0x0
    pgConn.sendChannel.writeFully(cleartextPasswordMessage)

    val authenticationOk = readAuthenticationResponse(pgConn.receiveChannel)
    if (authenticationOk !is AuthenticationOk) {
        println("Unexpected message type in cleartext password authentication process. Expected AuthenticationOk. Got: $authenticationOk")
        return AuthenticationFailure("Unexpected message type in cleartext password authentication process. Expected AuthenticationOk. Got: $authenticationOk")
    }

    return authenticationOk
}

private suspend fun sendMd5PasswordResponse(pgConn: PgConnection, salt: String) {
    val messageType = ByteArray(1)
    messageType[0] = 'p'.code.toByte()
    val inner = MD5().digest("$pgConn.password$pgConn.user".toByteArray())
    val passwordHash = "md5".toByteArray() + MD5().digest(inner + salt.toByteArray())
    val sizeOf = pgConn.password.length + 4
    val cleartextPasswordMessage = messageType + i32ToByteArray(sizeOf) + passwordHash
    pgConn.sendChannel.writeFully(cleartextPasswordMessage)
}

private suspend fun performScramSha256Authentication(pgConn: PgConnection): AuthenticationResponse {
    // send SASLInitialResponse message
    val messageType = ByteArray(1)
    messageType[0] = 'p'.code.toByte()
    val mechanism = "SCRAM-SHA-256"
    // hard-coded gs2 header for now
    val gs2Header = "n,,"
    val clientFirstMessageBare = "n=,r=" + getRandomString(24)
    val clientFirstData = gs2Header + clientFirstMessageBare
    println("sending client-first data: $clientFirstData")
    val length = 4 + mechanism.length + 1 + 4 +  clientFirstData.length
    val saslInitialResponseMessage = messageType + i32ToByteArray(length) + mechanism.toAscii() + 0x0 + i32ToByteArray(clientFirstData.length) + clientFirstData.toAscii()
    pgConn.sendChannel.writeFully(saslInitialResponseMessage)
    println("Done sending scram-sha-256 initial response")

    val saslContinuationMessage = readAuthenticationResponse(pgConn.receiveChannel)
    if (saslContinuationMessage !is SaslAuthenticationContinue) {
        println("Unexpected message type")
        return AuthenticationFailure("Unexpected message type in SCRAM-SHA-256 authentication process. Message type: $saslContinuationMessage")
    }

    println("sasl continuation data: ${saslContinuationMessage.saslData}")
    // TODO: here we need to check that the first part of the 'r' value is equal to the clientFirstData random string
    // s is the base64-encoded salt
    // i is the iteration count
    val serverFirstMessage = parseServerFirstMessage(saslContinuationMessage.saslData).getOrElse {
        return AuthenticationFailure(it.message ?: "Null error message from parseServerFirstMessage")
    }

    val clientFinalMessageText = getScramClientFinalMessage(pgConn.password, serverFirstMessage.r, serverFirstMessage.s, serverFirstMessage.i, clientFirstMessageBare, saslContinuationMessage.saslData)
    val clientFinalMessageSize = 4 + clientFinalMessageText.length
    val clientFinalMessageType = ByteArray(1)
    clientFinalMessageType[0] = 'p'.code.toByte()
    val finalMessage = clientFinalMessageType + i32ToByteArray(clientFinalMessageSize) + clientFinalMessageText.toAscii()
    pgConn.sendChannel.writeFully(finalMessage)
    println("Done sending client final message")

    val saslFinalResponse = readAuthenticationResponse(pgConn.receiveChannel)
    if (saslFinalResponse !is AuthenticationSASLFinal) {
        println("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationSASLFinal. Got: $saslContinuationMessage")
        return AuthenticationFailure("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationSASLFinal. Got: $saslContinuationMessage")
    }

    val authenticationOk = readAuthenticationResponse(pgConn.receiveChannel)
    if (authenticationOk !is AuthenticationOk) {
        println("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationOk. Got: $saslContinuationMessage")
        return AuthenticationFailure("Unexpected message type in SCRAM-SHA-256 authentication process. Expected AuthenticationOk. Got: $saslContinuationMessage")
    }

    return authenticationOk
}
