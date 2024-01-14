package org.ktpg

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

data class ColumnDescriptor(val name: String,
                            val tableOid: Int,
                            val columnId: Short,
                            val dataTypeOid: Int,
                            val dataTypeSize: Short,
                            val typeModifier: Int,
                            val formatCode: Short)

data class SimpleQueryResponse(
    val commandTag: String,
    val columns: List<ColumnDescriptor>,
    val dataRows: List<Map<String,String>>,
    val error: Map<String,String>,
    val notice: Map<String, String>
)

data class SimpleQueryError(val errorString: String)

data class PgWireMessage(val messageType: Char, val messageBytes: ByteReadPacket)

sealed interface AuthenticationResponse
data object AuthenticationOk : AuthenticationResponse
data object CleartextPasswordRequest : AuthenticationResponse
data class Md5PasswordRequest(val salt: String) : AuthenticationResponse
data class SaslAuthenticationRequest(val mechanism: String) : AuthenticationResponse
data class SaslAuthenticationContinue(val saslData: String) : AuthenticationResponse
data object AuthenticationSASLFinal : AuthenticationResponse
data class AuthenticationFailure(val errorString: String) : AuthenticationResponse
// TODO other authentication schemes

data class StartupParameters(val parameterStatusMap: Map<String,String>, val backendKeyDataMap: Map<String,Int>)
data class StartupFailure(val errorString: String)

enum class MessageType(val value: Char) {
    BACKEND_KEY_DATA('K'),
    ERROR_RESPONSE('E'),
    AUTHENTICATION('R'),
    PARAMETER_STATUS('S'),
    READY_FOR_QUERY('Z'),
    COMMAND_COMPLETE('C'),
    COPY_IN_RESPONSE('G'),
    COPY_OUT_RESPONSE('H'),
    ROW_DESCRIPTION('T'),
    DATA_ROW('D'),
    EMPTY_QUERY_RESPONSE('I'),
    NOTICE_RESPONSE('N')
}