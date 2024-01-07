import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

import com.github.michaelbull.result.*

import org.ktpg.*

// TODO: use some kind of result type for this
suspend fun getConnection(host: String, port: Int, user: String, password: String, database: String, optionalParameters: Map<String, String>): Result<PgConnectionStartupParameters, PgConnectionFailure> {
    try {
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
            optionalParameters=optionalParameters,
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
    sendSimpleQueryMessage(pgConn, sql)
}

suspend fun readSimpleQueryResponse(pgConn: PgConnection): Result<List<SimpleQueryResponse>, SimpleQueryError> {
    return readSimpleQueryResponseMessages(pgConn)
}

suspend fun close(pgConn: PgConnection) {
    sendTerminationMessage(pgConn)
    pgConn.socket.close()
    pgConn.selectorManager.close()
}