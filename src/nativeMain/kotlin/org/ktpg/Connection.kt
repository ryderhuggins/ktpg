import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

import com.github.michaelbull.result.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

import org.ktpg.*
import org.ktpg.wireprotocol.*

/**
 * TODO - for now i'm just putting exposed data structures here (i.e. supposed to be used by clients)
 */
typealias ErrorResponse = Map<String,String>
typealias ExecuteResponse = List<List<String>>

class PreparedStatementSuccess
class BindStatementSuccess

sealed interface PgConnectionResult

data class PgConnection internal constructor(
    val host: String,
    val port: Int,
    val user: String,
    internal val password: String,
    val database: String,
    val clientParameters: Map<String, String>,
    internal val socket: Socket,
    internal val receiveChannel: ByteReadChannel,
    internal val sendChannel: ByteWriteChannel,
    internal val selectorManager: SelectorManager
) : PgConnectionResult

data class PgConnectionStartupParameters internal constructor(
    val pgConn: PgConnection,
    val startupParameters: StartupParameters
)

data class PgConnectionFailure internal constructor(
    val errorString: String
) : PgConnectionResult

suspend fun getConnection(host: String, port: Int, user: String, password: String, database: String, clientParameters: Map<String, String>): Result<PgConnectionStartupParameters, PgConnectionFailure> {
    try {
        val finalParametersList = clientParameters.toMutableMap()
        finalParametersList.putAll(mapOf("user" to user, "database" to database))

        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect(host, port)
        val receiveChannel = socket.openReadChannel()
        val sendChannel = socket.openWriteChannel(autoFlush = true)
        val conn = PgConnection(
            host=host,
            port=port,
            user=user,
            password=password,
            database=database,
            clientParameters=finalParametersList,
            socket=socket,
            receiveChannel=receiveChannel,
            sendChannel=sendChannel,
            selectorManager=selectorManager
        )

        return when (val params = startupConnection(conn)) {
            is Ok -> Ok(PgConnectionStartupParameters(conn, params.value))
            is Err -> Err(PgConnectionFailure("Failed in startup message exchange with postgres server"))
        }

    } catch(e: Exception) {
        return Err(PgConnectionFailure(e.message ?: "Null exception string while creating connection"))
    }
}

suspend fun PgConnection.executeSimpleQuery(sql: String) {
    sendSimpleQueryMessage(this.sendChannel, sql)
}

suspend fun PgConnection.readSimpleQueryResponse(): Result<List<SimpleQueryResponse>, SimpleQueryError> {
    return readSimpleQueryResponseMessages(this.receiveChannel)
}

suspend fun PgConnection.prepareStatement(name: String? = null, sql: String, types: List<PgTypes>? = null) {
    val parseMessage = ParseMessage(
        name ?: "",
        sql,
        types ?: emptyList()
    )

    val parseMessageBytes = serialize(parseMessage)
    this.sendChannel.writeFully(parseMessageBytes)
}

suspend fun PgConnection.bind(statementName: String? = null, portalName: String? = null, parameterValues: List<ParameterValue>) {
    val parameterFormats = List(parameterValues.size, { ParameterFormat.TEXT })
    val bindMessage = BindMessage(portalName ?: "", statementName ?: "", parameterFormats, parameterValues)
    val bytes = serialize(bindMessage)
    this.sendChannel.writeFully(bytes)
}

suspend fun PgConnection.execute(portalName: String? = null): ExecuteResponse {
    val executeMessage = ExecuteMessage(portalName ?: "")
    val bytes = serialize(executeMessage)
    this.sendChannel.writeFully(bytes)
    this.sendSyncMessage()

    return this.readExecuteResponse()
}

suspend fun PgConnection.readExecuteResponse(): ExecuteResponse {
    var message: PgWireMessage
    val dataRows = mutableListOf<List<String>>()

    // just putting a bound on this for sanity's sake
    for (i in 0..999999) {
        message = readMessage(this.receiveChannel)
        when(message.messageType) {
            MessageType.ERROR_RESPONSE.value -> println("Got ErrorResponse message from server")
            MessageType.PARSE_COMPLETE.value -> println("Parse complete")
            MessageType.BIND_COMPLETE.value -> println("Bind complete")
            MessageType.READY_FOR_QUERY.value -> { println("Ready for query"); return dataRows; }
            MessageType.DATA_ROW.value -> {
                val columnCount = message.messageBytes.readShort()
                val dataRow = mutableListOf<String>()

                fields@ for (j in 0..<columnCount) {
                    val fieldLength = message.messageBytes.readInt()
                    if (fieldLength == -1) {
//                        println("Field length -1. adding empty string to result list")
                        dataRow.add("")
                        continue@fields
                    }
                    val columnValue = readString(message.messageBytes, fieldLength)
                    dataRow.add(columnValue)
                }
                dataRows.add(dataRow)
            }
        }
    }
    println("Exhausted loop count while reading execution response")
    return emptyList()
}

suspend fun PgConnection.sendSyncMessage() {
    this.sendChannel.writeFully(SyncMessage.serialize())
}

suspend fun PgConnection.closePreparedStatement(statementName: String) {
    val closeMessage = CloseMessage(
        CloseTarget.PreparedStatement,
        statementName
    )

    this.sendChannel.writeFully(closeMessage.serialize())
    readUntilZ() ?: println("Error closing statement: $statementName")
}

suspend fun PgConnection.closePortal(portalName: String) {
    val closeMessage = CloseMessage(
        CloseTarget.Portal,
        portalName
    )

    this.sendChannel.writeFully(closeMessage.serialize())
    readUntilZ() ?: println("Error closing portal: $portalName")
}

suspend fun PgConnection.readUntilZ(): Map<String,String>? {
    var message: PgWireMessage
    var err: Map<String, String>? = null

    for (i in 0..999999) {
        message = readMessage(this.receiveChannel)
        when(message.messageType) {
            MessageType.ERROR_RESPONSE.value -> err = parseErrorOrNoticeResponseMessage(message.messageBytes)
            MessageType.READY_FOR_QUERY.value -> break
            else -> { }
        }
    }
    return err
}

suspend fun PgConnection.sendFlushMessage() {
    val bytes = serialize(FlushMessage)
    this.sendChannel.writeFully(bytes)
}

/**
 * Planning preparedStatement interface
 * Need a sealed interface PgTypes { Int, Long, Text, Varchar, etc. }
 * if prepared statement is "UPDATE EMPLOYEES SET SALARY = ? WHERE ID = ?"
 * ps.setPgInt("$1", 10000).setPgText("$2", "501").build()
 * or there could be a buildPortal function that takes a prepared statement and a...
 *
 * prepareStatement(sqlString, p1TypeOid, p2TypeOid, p3TypeOid...)
 */

suspend fun close(pgConn: PgConnection) {
    sendTerminationMessage(pgConn.sendChannel)
    pgConn.socket.close()
    pgConn.selectorManager.close()
}