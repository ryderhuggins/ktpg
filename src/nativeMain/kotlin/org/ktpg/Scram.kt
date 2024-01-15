package org.ktpg

import io.ktor.util.*
import io.ktor.utils.io.core.*
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import kotlin.experimental.xor

import org.ktpg.wireprotocol.ClientFinalMessage

internal fun xorByteArrays(b1: ByteArray, b2: ByteArray): ByteArray {
    val res = ByteArray(b1.size)
    for (i in b1.indices) {
        res[i] = b1[i] xor b2[i]
    }
    return res
}

internal fun getSaltedPassword(normalizedPassword: ByteArray, salt: ByteArray, i: Int): ByteArray {
    val ui = mutableListOf<ByteArray>()
    val hmac = HmacSHA256(normalizedPassword)
    var prev = hmac.doFinal(salt + i32ToByteArray(1))
    ui.add(prev)
    var curr: ByteArray
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

internal fun getClientKey(saltedPassword: ByteArray): ByteArray {
    return HmacSHA256(saltedPassword).doFinal("Client Key".toByteArray())
}

internal fun getStoredKey(clientKey: ByteArray): ByteArray {
    return SHA256().digest(clientKey)
}

internal fun getAuthMessage(clientFirstMessage: String, serverFirstMessage: String, clientFinalMessageWithoutProof: String): ByteArray {
    val x = "$clientFirstMessage,$serverFirstMessage,$clientFinalMessageWithoutProof"
    return x.toByteArray()
}

internal fun getClientSignature(storedKey: ByteArray, authMessage: ByteArray): ByteArray {
    return HmacSHA256(storedKey).doFinal(authMessage)
}

internal fun getClientProof(clientKey: ByteArray, clientSignature: ByteArray): ByteArray {
    return xorByteArrays(clientKey, clientSignature)
}

// TODO: unit test this
internal fun getScramClientFinalMessage(password: String, r: String, s: String, i: Int, clientFirstMessageBare: String, serverFirstMessage: String): ClientFinalMessage {
    val saltedPassword = getSaltedPassword(password.toByteArray(), s.decodeBase64Bytes(), i)

    val clientKey = getClientKey(saltedPassword)

    val storedKey = getStoredKey(clientKey)

    val clientFinalMessageWithoutProof = "c=biws,r=$r"

    val authMessage = getAuthMessage(clientFirstMessageBare, serverFirstMessage, clientFinalMessageWithoutProof)

    val clientSignature = getClientSignature(storedKey, authMessage)

    val clientProof = getClientProof(clientKey, clientSignature)

    return ClientFinalMessage(clientFinalMessageWithoutProof, clientProof.encodeBase64())
}

internal data class ScramServerFirstMessage(val r: String, val s: String, val i: Int)

// TODO unit test this
internal fun parseServerFirstMessage(serverFirstMessageText: String): Result<ScramServerFirstMessage> {
    var r = ""
    var s = ""
    var i = 0
    for (str in serverFirstMessageText.split(",")) {
        when (val c = str[0]) {
            'r' -> { r = str.split("=")[1] }
            's' -> { s = str.split("=")[1] }
            'i' -> { i = str.split("=")[1].toInt() }
            else -> println("Unidentified value when parsing SCRAM server-first-message: $c")
        }
    }

    if (r == "" || s == "" || i == 0) {
        return Result.failure(Throwable("Failed to parse server-first-message"))
    }
    return Result.success(ScramServerFirstMessage(r, s, i))
}