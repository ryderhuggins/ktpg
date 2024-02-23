import com.github.michaelbull.result.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.ktpg.wireprotocol.ParameterValue
import org.ktpg.wireprotocol.PgTypes

fun main() {
    runBlocking {
        // change username to ryderhuggins for no password, secure1 for cleartext password, secure2 for SCRAM-SHA-256
        val (pgConn, _) = getConnection("127.0.0.1", 5432, "secure2", "password123", "demo", emptyMap()).getOrThrow {
            Throwable(it.errorString)
        }

        launch(Dispatchers.IO) {
            simpleQueryStuff(pgConn)
//            preparedStatementStuff(pgConn)
            close(pgConn)
        }

    }
}

private suspend fun preparedStatementStuff(pgConn: PgConnection) {
    val p2 = PreparedStatement(
        "named_statement",
        "select * from information_schema.tables where table_name = $1",
        null
    )
    pgConn.prepareStatement(p2)

    pgConn.bind(statementName = "named_statement", parameterValues = listOf(ParameterValue.Text("pg_class")))

    val res = pgConn.execute()
    println("result from p2 exeuction: $res")

    val p3 = PreparedStatement(
        "",
        "select table_name, table_type from information_schema.tables where table_name = $1 and table_type = $2",
        listOf(PgTypes.VARCHAR, PgTypes.VARCHAR)
    )
    pgConn.prepareStatement(p3)

    pgConn.bind(
        statementName = "",
        parameterValues = listOf(ParameterValue.Text("pg_class"), ParameterValue.Text("hello"))
    )

    val res2 = pgConn.execute()
}

suspend fun simpleQueryStuff(pgConn: PgConnection) {
    pgConn.executeSimpleQuery("INSERT INTO bookings (book_ref, book_date, total_amount)\nVALUES('222223', '2024-07-04 20:12:00-04', 12.12);")
    pgConn.readSimpleQueryResponse()
        .onFailure { println("** FAILED ** test with error: $it") }

    pgConn.executeSimpleQuery("DELETE FROM bookings where book_ref = '222223';")
    pgConn.readSimpleQueryResponse()
        .onFailure { println("** FAILED ** test with error: $it") }

    pgConn.executeSimpleQuery("select * from book_ref limit 100;")
    pgConn.readSimpleQueryResponse()
        .onFailure { println("** FAILED ** test with error: $it") }
//
//    pgConn.executeSimpleQuery("select fro links;")
//    pgConn.readSimpleQueryResponse()
//        .onFailure { bad -> println("** FAILED **: $bad") }
//        .onSuccess { good -> println("Simple query error response: $good") }
//
//    pgConn.executeSimpleQuery("")
//    pgConn.readSimpleQueryResponse()
//        .onFailure { println("** FAILED **: $it") }
//        .onSuccess { println("Simple query empty response: $it") }
//
//    val rando2 = getRandomString(40)
//    pgConn.executeSimpleQuery("INSERT INTO links (url, name)\n" +
//                               "VALUES('https://www.$rando2.com','$rando2 value');\n" +
//                               "SELECT 1/0;\n" +
//                               "INSERT INTO links (url, name)\n" +
//                               "VALUES('https://www.$rando2.com','$rando2 value');")
//    pgConn.readSimpleQueryResponse()
//        .onFailure { println("** FAILED **: $it") }
//        .onSuccess { println("Simple multi statement failure response: $it") }
//
//    val rando3 = getRandomString(40)
//    val rando4 = getRandomString(40)
//    pgConn.executeSimpleQuery("INSERT INTO links (url, name)\n" +
//                               "VALUES('https://www.$rando3.com','$rando3 value');\n" +
//                               "COMMIT;\n" +
//                               "INSERT INTO links (url, name)\n" +
//                               "VALUES('https://www.$rando4.com','$rando4 value');\n" +
//                               "SELECT 1/0;")
//    pgConn.readSimpleQueryResponse()
//        .onFailure { println("** FAILED **: $it") }
//        .onSuccess { println("Simple multi statement commit response: $it") }
//
//    val rando5 = getRandomString(40)
//    val rando6 = getRandomString(40)
//    pgConn.executeSimpleQuery("INSERT INTO links (url, name)\n" +
//                               "VALUES('https://www.$rando5.com','$rando5 value');\n" +
//                               "INSERT INTO links (url, name)\n" +
//                               "VALUES('https://www.$rando6.com','$rando6 value');")
//    pgConn.readSimpleQueryResponse()
//        .onFailure { println("** FAILED **: $it") }
//        .onSuccess { println("Simple multi statement success response: $it") }
//
//    pgConn.executeSimpleQuery("select * from links limit 1;\nselect * from links limit 1;")
//    pgConn.readSimpleQueryResponse()
//        .onFailure { println("** FAILED **: $it") }
//        .onSuccess { println("Simple query multiSelect response: $it") }
//
//    val rando7 = getRandomString(40)
//    val rando8 = getRandomString(40)
//    pgConn.executeSimpleQuery("SELECT 1/0;\nCOMMIT;\n" + "INSERT INTO links (url, name)\n" +
//                               "VALUES('https://www.$rando7.com','$rando7 value');\n" +
//                               "COMMIT;\n" +
//                               "INSERT INTO links (url, name)\n" +
//                               "VALUES('https://www.$rando8.com','$rando8 value');\n")
//    pgConn.readSimpleQueryResponse()
//        .onFailure { println("** FAILED **: $it") }
//        .onSuccess { println("Simple multi statement fail then query response: $it") }
}