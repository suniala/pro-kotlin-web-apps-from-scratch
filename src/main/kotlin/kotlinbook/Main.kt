package kotlinbook

import com.google.gson.Gson
import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinbook.WebResponse.JsonWebResponse
import kotlinbook.WebResponse.TextWebResponse
import kotliquery.Row
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.lang.RuntimeException
import java.util.Date
import java.util.Random
import javax.sql.DataSource
import kotlin.random.asKotlinRandom

private val log = LoggerFactory.getLogger("kotlinbook.Main")

fun main() {
    log.debug("Starting application...")

    createAppConfig(System.getenv("KOTLINBOOK_ENV") ?: "local").also { config ->
        log.info("Configuration loaded successfully:\n{}", config.formatForLogging())
        log.debug("tee1")

        embeddedServer(Netty, port = config.httpPort) {
            val dataSource = createAndMigrateDataSource(config)
            createKtorApplication(dataSource)
        }.start(wait = true)
    }
}

fun createDataSource(config: WebappConfig) =
    HikariDataSource().apply {
        jdbcUrl = config.dbUrl
        username = config.dbUser
        password = config.dbPassword
    }

fun migrateDataSource(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("db/migration")
        .table("flyway_schema_history")
        .load()
        .migrate()
}

fun createAndMigrateDataSource(config: WebappConfig) =
    createDataSource(config).also(::migrateDataSource)

fun mapFromRow(row: Row): Map<String, Any?> {
    return row.underlying.metaData
        .let { (1..it.columnCount).map(it::getColumnName) }
        .map { it to row.anyOrNull(it) }
        .toMap()
}

fun createAppConfig(env: String): WebappConfig =
    ConfigFactory
        .parseResources("app-${env}.conf")
        .withFallback(ConfigFactory.parseResources("app.conf"))
        .resolve()
        .let {
            WebappConfig(
                httpPort = it.getInt("httpPort"),
                dbUser = it.getString("dbUser"),
                dbPassword = it.getString("dbPassword"),
                dbUrl = it.getString("dbUrl")
            )
        }


class KtorJsonWebResponse(val body: Any?, override val status: HttpStatusCode = HttpStatusCode.OK) :
    OutgoingContent.ByteArrayContent() {
    override val contentType: ContentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)

    override fun bytes() = Gson().toJson(body).toByteArray(Charsets.UTF_8)
}

fun webResponse(handler: suspend PipelineContext<Unit, ApplicationCall>.() -> WebResponse): PipelineInterceptor<Unit, ApplicationCall> {
    return {
        val resp = this.handler()
        val statusCode = HttpStatusCode.fromValue(resp.statusCode)

        for ((name, values) in resp.headers()) {
            for (value in values) {
                call.response.header(name, value)
            }
        }

        when (resp) {
            is TextWebResponse -> {
                call.respondText(text = resp.body, status = statusCode)
            }

            is JsonWebResponse -> {
                call.respond(KtorJsonWebResponse(body = resp.body, status = statusCode))
            }
        }
    }
}

fun webResponseDb(
    dataSource: DataSource,
    handler: suspend PipelineContext<Unit, ApplicationCall>.(
        dbSess: Session,
    ) -> WebResponse,
) = webResponse {
    sessionOf(
        dataSource,
        returnGeneratedKey = true
    ).use { dbSess ->
        handler(dbSess)
    }
}

fun webResponseTx(
    dataSource: DataSource,
    handler: suspend PipelineContext<Unit, ApplicationCall>.(
        dbSess: TransactionalSession,
    ) -> WebResponse,
) = webResponseDb(dataSource) { dbSess ->
    dbSess.transaction { txSess ->
        handler(txSess)
    }
}

fun <A> dbSavePoint(dbSess: Session, body: () -> A): A {
    val sp = dbSess.connection.underlying.setSavepoint()
    return try {
        body().also {
            dbSess.connection.underlying.releaseSavepoint(sp)
        }
    } catch (e: Throwable) {
        log.warn("Got exception, will rollback")
        dbSess.connection.underlying.rollback(sp)
        throw e
    }
}

data class User(
    val id: Long,
    val email: String,
    val tosAccepted: Boolean,
    val name: String?,
) {
    companion object {
        fun fromRow(row: Map<String, Any?>) = User(
            id = row["id"] as Long,
            email = row["email"] as String,
            name = row["name"] as? String,
            tosAccepted = row["tos_accepted"] as Boolean,
        )
    }
}

fun createUser(
    dbSession: Session,
    email: String,
    name: String,
    passwordText: String,
    tosAccepted: Boolean = false,
): Long {
    val userId = dbSession.updateAndReturnGeneratedKey(
        queryOf(
            """
              INSERT INTO user_t
              (email, name, tos_accepted, password_hash)
              VALUES (:email, :name, :tosAccepted, :passwordHash)
              """,
            mapOf(
                "email" to email,
                "name" to name,
                "tosAccepted" to tosAccepted,
                "passwordHash" to passwordText
                    .toByteArray(Charsets.UTF_8)
            )
        )
    )
    return checkNotNull(userId)
}

fun getUser(dbSess: Session, id: Long): User? {
    return dbSess
        .single(
            queryOf("SELECT * FROM user_t WHERE id = ?", id),
            ::mapFromRow
        )
        ?.let(User::fromRow)
}

fun listUsers(dbSession: Session) =
    dbSession
        .list(queryOf("SELECT * FROM user_t"), ::mapFromRow)
        .map(User::fromRow)

object TestDataGenerator {
    private val rnd = Random().asKotlinRandom()
    fun username() = "Name${rnd.nextLong(100, 1000)}"
}

fun Application.createKtorApplication(dataSource: DataSource) {
    val log = LoggerFactory.getLogger("kotlinbook.Application")

    routing {
        get("/", webResponse {
            TextWebResponse("Hello, World!").header("x-asdf", Date().toString())
        })
        get("/test/param", webResponse {
            TextWebResponse("The param is: ${call.request.queryParameters["foo"]}")
        })
        get("/test/json", webResponse {
            JsonWebResponse(mapOf("foo" to "bar"))
                .header("x-test-header", "Just a test!")
        })
        get("/test/users_raw", webResponseDb(dataSource) { dbSess ->
            JsonWebResponse(
                dbSess.list(queryOf("SELECT name, email FROM user_t"), ::mapFromRow)
            )
        })
        get("/test/users_dto", webResponseDb(dataSource) { dbSess ->
            JsonWebResponse(
                dbSess.list(queryOf("SELECT * FROM user_t"), ::mapFromRow).map(User::fromRow)
            )
        })
        get("/test/failing_tx", webResponseTx(dataSource) { dbSess ->
            val testInsertEmail = "bwd@example.com"
            val testUserQuery =
                queryOf("SELECT count(*) FROM user_t WHERE email = :email", mapOf("email" to testInsertEmail))

            log.debug(
                "Number of {} users in db in beginning of transaction: {}", testInsertEmail,
                dbSess.single(testUserQuery, ::mapFromRow)
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
                dbSess.single(testUserQuery, ::mapFromRow)
            )
            val insertedUser = dbSess.single(
                queryOf("SELECT name from user_t WHERE email = :email", mapOf("email" to testInsertEmail)),
                ::mapFromRow
            )
            log.debug("Got {} from db before rollback", insertedUser)

            log.debug("Will now cause a rollback")
            dbSess.single(queryOf("SELECT 1 FROM nonexistanttable"), ::mapFromRow)

            TextWebResponse("This text should not be returned")
        })
        get("/test/partially_committed_tx", webResponseTx(dataSource) { dbSess ->
            fun insertUser(name: String) = queryOf(
                """
                        INSERT INTO user_t (email, name, password_hash, tos_accepted)
                        VALUES (:email, :name, 'rereer', false);
                    """.trimIndent(),
                mapOf("email" to "$name@example.com", "name" to name)
            )

            val firstTransactionUser = dbSavePoint(dbSess) {
                val name = TestDataGenerator.username()
                dbSess.update(insertUser(name))
                name
            }

            try {
                dbSavePoint(dbSess) {
                    val name = TestDataGenerator.username()
                    dbSess.update(insertUser(name))
                    throw RuntimeException("some error after $name was inserted")
                }
            } catch (e: Throwable) {
                log.debug("""Caught error "{}" from dbSavePoint""", e.message)
            }

            val firstTransactionUserIdAfterSecondTransactionRollback = dbSess.single(
                queryOf("SELECT id FROM user_t WHERE name = :name", mapOf("name" to firstTransactionUser)),
                ::mapFromRow
            )

            JsonWebResponse(firstTransactionUserIdAfterSecondTransactionRollback)
        })
    }
}
