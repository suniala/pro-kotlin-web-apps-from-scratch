package kotlinbook

import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinbook.db.DBSupport.dbSavePoint
import kotlinbook.db.DBSupport.mapFromRow
import kotlinbook.db.User
import kotlinbook.db.getUser
import kotlinbook.ui.AppLayout
import kotlinbook.util.TestDataGenerator
import kotlinbook.web.WebResponse
import kotlinbook.web.WebResponse.JsonWebResponse
import kotlinbook.web.WebResponse.TextWebResponse
import kotlinbook.web.WebResponseSupport.webResponse
import kotlinbook.web.WebResponseSupport.webResponseDb
import kotlinbook.web.WebResponseSupport.webResponseTx
import kotlinbook.web.authenticateUser
import kotlinx.coroutines.*
import kotlinx.html.*
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("kotlinbook.Main")

fun main() {
    log.debug("Starting application...")

    createAppConfig(System.getenv("KOTLINBOOK_ENV") ?: "local").also { config ->
        log.info("Configuration loaded successfully:\n{}", config.formatForLogging())
        log.debug("tee1")

        embeddedServer(Netty, port = 9876) {
            createFakeServiceKtorApplication()
        }.start(wait = false)

        embeddedServer(Netty, port = config.httpPort) {
            val dataSource = createAndMigrateDataSource(config)
            setUpKtorCookieSecurity(config, dataSource)
            createKtorApplication(config, dataSource)
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
                dbUrl = it.getString("dbUrl"),
                useFileSystemAssets = it.getBoolean("useFileSystemAssets"),
                useSecureCookie = it.getBoolean("useSecureCookie"),
                cookieEncryptionKey = it.getString("cookieEncryptionKey"),
                cookieSigningKey = it.getString("cookieSigningKey"),
            )
        }


fun Application.createFakeServiceKtorApplication() {
    routing {
        get("/random_number", webResponse {
            val num = (200L..2000L).random()
            delay(num)
            TextWebResponse(num.toString())
        })
        get("/ping", webResponse {
            TextWebResponse("pong")
        })
        post("/reverse", webResponse {
            TextWebResponse(call.receiveText().reversed())
        })
    }
}

suspend fun handleCoroutineTest(
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
                ::mapFromRow
            )
        }
    }
    TextWebResponse(
        """
            Random number: ${randomNumberRequest.await()}
            Reversed: ${reverseRequest.await()}
            Query: ${queryOperation.await()}
        """.trimIndent()
    )
}

/**
 * This will be sent to the browser (encrypted) so remember the limit of 4 kilobytes.
 */
data class UserSession(val userId: Long): Principal

/**
 * To separate our authentication setup from the rest of our routes, we’ll set up authentication and
 * authenticated routes here.
 */
fun Application.setUpKtorCookieSecurity (
    appConfig: WebappConfig,
    dataSource: DataSource
) {
    install(Sessions) {
        cookie<UserSession>("user-session") {
            transform(
                // Encrypt and sign the cookie
                SessionTransportTransformerEncrypt(
                    hex(appConfig.cookieEncryptionKey),
                    hex(appConfig.cookieSigningKey)
                )
            )
            cookie.maxAge = kotlin.time.Duration.parse("30d")
            cookie.httpOnly = true
            cookie.path = "/"
            cookie.secure = appConfig.useSecureCookie
            cookie.extensions["SameSite"] = "lax"
        }
    }

    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session ->
                session
            }
            challenge {
                call.respondRedirect("/login")
            }
        }
    }

    routing {
        get("/login", webResponse {
            WebResponse.HtmlWebResponse(AppLayout("Log in").apply {
                pageBody {
                    form(method = FormMethod.post, action = "/login") {
                        p {
                            label { +"E-mail" }
                            input(type = InputType.text, name = "username")
                        }
                        p {
                            label { +"Password" }
                            input(type = InputType.password, name = "password")
                        }
                        button(type = ButtonType.submit) { +"Log in" }
                    }
                }
            })
        })
        post("/login") {
            sessionOf(dataSource).use { dbSess ->
                val params = call.receiveParameters()
                val userId = authenticateUser(
                    dbSess,
                    requireNotNull(params["username"]),
                    requireNotNull(params["password"]),
                )
                if (userId == null) {
                    call.respondRedirect("/login")
                } else {
                    call.sessions.set(UserSession(userId = userId))
                    call.respondRedirect("/secret")
                }
            }
        }
        authenticate("auth-session") {
            get("/secret", webResponseDb(dataSource) { dbSess ->
                val userSession = checkNotNull(call.principal<UserSession>())
                val user = checkNotNull(getUser(dbSess, userSession.userId))
                WebResponse.HtmlWebResponse(
                    AppLayout("Welcome, ${user.email}").apply {
                        pageBody {
                            h1 {
                                +"Hello there, ${user.email}"
                            }
                            p { +"You're logged in." }
                            p {
                                a(href = "/logout") { +"Log out" }
                            }
                        }
                    }
                )
            })
        }
        authenticate("auth-session") {
            get("/logout") {
                call.sessions.clear<UserSession>()
                call.respondRedirect("/login")
            }
        }
    }
}

fun Application.createKtorApplication(appConfig: WebappConfig, dataSource: DataSource) {
    val log = LoggerFactory.getLogger("kotlinbook.Application")

    routing {
        static("/") {
            if (appConfig.useFileSystemAssets) {
                files("src/main/resources/public")
            } else {
                resources("public")
            }
        }
        get("/", webResponse {
            TextWebResponse("Hello, World!").header("x-asdf", Date().toString())
        })
        get("/test/coroutine", webResponseDb(dataSource) { dbSess ->
            handleCoroutineTest(dbSess)
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
        get("/test/html", webResponse {
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
