import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

import com.github.michaelbull.result.*
import io.ktor.utils.io.*

import org.ktpg.*
import org.ktpg.wireprotocol.*

/**
 * TODO - for now i'm just putting exposed data structures here (i.e. supposed to be used by clients)
 */
data class PreparedStatement(
    val name: String? = null,
    val sql: String,
    val types: List<PgTypes>? = null
)

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

suspend fun executeSimpleQuery(pgConn: PgConnection, sql: String) {
    sendSimpleQueryMessage(pgConn.sendChannel, sql)
}

suspend fun readSimpleQueryResponse(pgConn: PgConnection): Result<List<SimpleQueryResponse>, SimpleQueryError> {
    return readSimpleQueryResponseMessages(pgConn.receiveChannel)
}

suspend fun prepareStatement(pgConn: PgConnection, preparedStatement: PreparedStatement) {
    val parseMessage = ParseMessage(
        preparedStatement.name ?: "",
        preparedStatement.sql,
        preparedStatement.types ?: emptyList()
    )
    println("parseMessage: $parseMessage")

    val parseMessageBytes = serialize(parseMessage)
    println("writing ${parseMessageBytes.size} bytes")
    pgConn.sendChannel.writeFully(parseMessageBytes)
}

suspend fun sendSyncMessage(pgConn: PgConnection) {
    val bytes = serialize(SyncMessage)
    pgConn.sendChannel.writeFully(bytes)
}

suspend fun sendFlushMessage(pgConn: PgConnection) {
    val bytes = serialize(FlushMessage)
    pgConn.sendChannel.writeFully(bytes)
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