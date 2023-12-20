import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import kotlin.system.*


internal fun i32ToByteArray(n: Int): ByteArray =
    (3 downTo 0).map {
        (n shr (it * Byte.SIZE_BITS)).toByte()
    }.toByteArray()

fun String.toAscii() = this.map { it.code.toByte() }

@OptIn(ExperimentalStdlibApi::class)
suspend fun sendStartupMessage(sendChannel: ByteWriteChannel, user: String, database: String) {
    val parameters = listOf("user", user, "database", database)
    val size = parameters.sumOf { it.length + 1 } + 9 // 4 for protocol version, 4 for size, 1 for extra null byte
//    TODO: does the 0 value count as length?
    val startupMessage = i32ToByteArray(size) + 0x0 + 0x03 + 0x0 + 0x0 + parameters.flatMap { it.toAscii() + 0x0 } + 0x0
    println("startup message: ${startupMessage.toHexString()}")
    println("startup message length: $size")
    sendChannel.writeFully(startupMessage)
}

fun main() {
    runBlocking {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect("127.0.0.1", 5432)

        val receiveChannel = socket.openReadChannel()
        val sendChannel = socket.openWriteChannel(autoFlush = true)

        launch(Dispatchers.IO) {
            println("hihihi")
            socket.close()
            selectorManager.close()
        }

//        while (true) {
//            val myMessage = readln()
//            sendChannel.writeStringUtf8("$myMessage\n")
//        }
        // i think the first thing i need to figure out is how to "pack" and "unpack" primitives
        // in other words, how do i convert an int to a ByteArray
        // then, how to I put a character followed by an Int followed by a String?

        sendStartupMessage(sendChannel, "ryderhuggins", "postgres")

    }
}