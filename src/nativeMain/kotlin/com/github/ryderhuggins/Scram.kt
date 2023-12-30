package com.github.ryderhuggins

import io.ktor.util.*
import io.ktor.utils.io.core.*
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import kotlin.experimental.xor

fun xorByteArrays(b1: ByteArray, b2: ByteArray): ByteArray {
    val res = ByteArray(b1.size)
    for (i in b1.indices) {
        res[i] = b1[i] xor b2[i]
    }
    return res
}

fun getSaltedPassword(normalizedPassword: ByteArray, salt: ByteArray, i: Int): ByteArray {
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

fun getClientKey(saltedPassword: ByteArray): ByteArray {
    return HmacSHA256(saltedPassword).doFinal("Client Key".toByteArray())
}

fun getStoredKey(clientKey: ByteArray): ByteArray {
    return SHA256().digest(clientKey)
}

fun getAuthMessage(clientFirstMessage: String, serverFirstMessage: String, clientFinalMessageWithoutProof: String): ByteArray {
    val x = "$clientFirstMessage,$serverFirstMessage,$clientFinalMessageWithoutProof"
    println("auth message: $x")
    return x.toByteArray()
}

fun getClientSignature(storedKey: ByteArray, authMessage: ByteArray): ByteArray {
    return HmacSHA256(storedKey).doFinal(authMessage)
}

fun getClientProof(clientKey: ByteArray, clientSignature: ByteArray): ByteArray {
    return xorByteArrays(clientKey, clientSignature)
}

// TODO: unit test this
@OptIn(ExperimentalStdlibApi::class)
fun getScramClientFinalMessage(password: String, r: String, s: String, i: Int, clientFirstMessageBare: String, serverFirstMessage: String): String {
    val hmac = HmacSHA256("12345".toByteArray())
    val res = hmac.doFinal("sample message".toByteArray())
    println("hmac res: ${res.toHexString()}")

    // test
//    val r="fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j"
//    val s="QSXCR+Q6sek8bf92".decodeBase64Bytes()
//    val i=4096

    val saltedPassword = getSaltedPassword(password.toByteArray(), s.decodeBase64Bytes(), i)
    println("salted password: ${saltedPassword.toHexString()}")

    val clientKey = getClientKey(saltedPassword)
    println("client key: ${clientKey.toHexString()}")

    val storedKey = getStoredKey(clientKey)
    println("stored key: ${storedKey.toHexString()}")

//    val clientFirstMessageBare = "n=user,r=fyko+d2lbbFgONRv9qkxdawL"
//    val serverFirstMessage = "r=fyko+d2lbbFgONRv9qkxdawL3rfcNHYJY1ZVvWVs7j,s=QSXCR+Q6sek8bf92,i=4096"
    val clientFinalMessageWithoutProof = "c=biws,r=$r"

    val authMessage = getAuthMessage(clientFirstMessageBare, serverFirstMessage, clientFinalMessageWithoutProof)
    println("auth message: ${authMessage.toHexString()}")

    val clientSignature = getClientSignature(storedKey, authMessage)
    println("client signature: ${clientSignature.toHexString()}")

    val clientProof = getClientProof(clientKey, clientSignature)
    println("client proof: ${clientProof.toHexString()}")
    println("client proof: ${clientProof.encodeBase64()}")

    // TODO: what type should this return?
    return clientFinalMessageWithoutProof + ",p=" + clientProof.encodeBase64()
}

data class ScramServerFirstMessage(val r: String, val s: String, val i: Int)

data class ClientFinalMessage(val clientFinalMessageBare: String, val clientProof: String)

// TODO unit test this
fun parseServerFirstMessage(serverFirstMessageText: String): Result<ScramServerFirstMessage> {
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