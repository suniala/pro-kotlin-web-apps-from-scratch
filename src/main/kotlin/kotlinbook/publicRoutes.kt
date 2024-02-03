package kotlinbook

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import kotlinbook.db.DBSupport
import kotlinbook.db.User
import kotlinbook.ui.AppLayout
import kotlinbook.util.TestDataGenerator
import kotlinbook.web.WebResponse
import kotlinbook.web.WebResponseSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.html.h1
import kotliquery.Session
import kotliquery.queryOf
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource

fun Application.initPublicRoutes(appConfig: WebappConfig, dataSource: DataSource) {
    val log = LoggerFactory.getLogger("kotlinbook.Application")

    routing {
        static("/") {
            if (appConfig.useFileSystemAssets) {
                files("src/main/resources/public")
            } else {
                resources("public")
            }
        }
        get("/", WebResponseSupport.webResponse {
            WebResponse.TextWebResponse("Hello, World!").header("x-asdf", Date().toString())
        })
        get("/test/coroutine", WebResponseSupport.webResponseDb(dataSource) { dbSess ->
            handleCoroutineTest(dbSess)
        })
        get("/test/param", WebResponseSupport.webResponse {
            WebResponse.TextWebResponse("The param is: ${call.request.queryParameters["foo"]}")
        })
        get("/test/json", WebResponseSupport.webResponse {
            WebResponse.JsonWebResponse(mapOf("foo" to "bar"))
                .header("x-test-header", "Just a test!")
        })
        get("/test/users_raw", WebResponseSupport.webResponseDb(dataSource) { dbSess ->
            WebResponse.JsonWebResponse(
                dbSess.list(queryOf("SELECT name, email FROM user_t"), DBSupport::mapFromRow)
            )
        })
        get("/test/users_dto", WebResponseSupport.webResponseDb(dataSource) { dbSess ->
            WebResponse.JsonWebResponse(
                dbSess.list(queryOf("SELECT * FROM user_t"), DBSupport::mapFromRow).map(User.Companion::fromRow)
            )
        })
        get("/test/failing_tx", WebResponseSupport.webResponseTx(dataSource) { dbSess ->
            val testInsertEmail = "bwd@example.com"
            val testUserQuery =
                queryOf("SELECT count(*) FROM user_t WHERE email = :email", mapOf("email" to testInsertEmail))

            log.debug(
                "Number of {} users in db in beginning of transaction: {}", testInsertEmail,
                dbSess.single(testUserQuery, DBSupport::mapFromRow)
            )

            dbSess.update(
                queryOf(
                    """
                        INSERT INTO user_t (email, name, password_hash, tos_accepted)
                        VALUES ('$testInsertEmail', 'Bwd Zvii', 'rereer', false);
                    """.trimIndent()
                )
            )
            log.debug(
                "Number of {} users in db after insert: {}", testInsertEmail,
                dbSess.single(testUserQuery, DBSupport::mapFromRow)
            )
            val insertedUser = dbSess.single(
                queryOf("SELECT name from user_t WHERE email = :email", mapOf("email" to testInsertEmail)),
                DBSupport::mapFromRow
            )
            log.debug("Got {} from db before rollback", insertedUser)

            log.debug("Will now cause a rollback")
            dbSess.single(queryOf("SELECT 1 FROM nonexistanttable"), DBSupport::mapFromRow)

            WebResponse.TextWebResponse("This text should not be returned")
        })
        get("/test/partially_committed_tx", WebResponseSupport.webResponseTx(dataSource) { dbSess ->
            fun insertUser(name: String) = queryOf(
                """
                        INSERT INTO user_t (email, name, password_hash, tos_accepted)
                        VALUES (:email, :name, 'rereer', false);
                    """.trimIndent(),
                mapOf("email" to "$name@example.com", "name" to name)
            )

            val firstTransactionUser = DBSupport.dbSavePoint(dbSess) {
                val name = TestDataGenerator.username()
                dbSess.update(insertUser(name))
                name
            }

            try {
                DBSupport.dbSavePoint(dbSess) {
                    val name = TestDataGenerator.username()
                    dbSess.update(insertUser(name))
                    throw RuntimeException("some error after $name was inserted")
                }
            } catch (e: Throwable) {
                log.debug("""Caught error "{}" from dbSavePoint""", e.message)
            }

            val firstTransactionUserIdAfterSecondTransactionRollback = dbSess.single(
                queryOf("SELECT id FROM user_t WHERE name = :name", mapOf("name" to firstTransactionUser)),
                DBSupport::mapFromRow
            )

            WebResponse.JsonWebResponse(firstTransactionUserIdAfterSecondTransactionRollback)
        })
        get("/test/html", WebResponseSupport.webResponse {
            WebResponse.HtmlWebResponse(AppLayout("Hello, world!").apply {
                pageBody {
                    h1 {
                        +"Hello, readers!"
                    }
                }
            })
        })
    }
}

private suspend fun handleCoroutineTest(
    dbSess: Session,
) = coroutineScope {
    val client = HttpClient(CIO)
    val randomNumberRequest = async {
        client.get("http://localhost:9876/random_number")
            .bodyAsText()
    }
    val reverseRequest = async {
        client.post("http://localhost:9876/reverse") {
            setBody(randomNumberRequest.await())
        }.bodyAsText()
    }
    val queryOperation = async {
        val pingPong = client.get("http://localhost:9876/ping")
            .bodyAsText()
        // Execute blocking call in a separate thread pool, designated for long-running blocking I/O operations such as database queries.
        // Note that this is just an example and not needed in this particular case.
        withContext(Dispatchers.IO) {
            dbSess.single(
                queryOf(
                    "SELECT count(*) c from user_t WHERE email != ?",
                    pingPong
                ),
                DBSupport::mapFromRow
            )
        }
    }
    WebResponse.TextWebResponse(
        """
            Random number: ${randomNumberRequest.await()}
            Reversed: ${reverseRequest.await()}
            Query: ${queryOperation.await()}
        """.trimIndent()
    )
}