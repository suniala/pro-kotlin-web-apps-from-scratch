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
import kotliquery.queryOf
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource
import kotliquery.sessionOf

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

fun Application.createKtorApplication(dataSource: DataSource) {
    routing {
        get("/", webResponse {
            TextWebResponse("Hello, World!").header("x-asdf", Date().toString())
        })
        get("/param_test", webResponse {
            TextWebResponse("The param is: ${call.request.queryParameters["foo"]}")
        })
        get("/json_test_with_header", webResponse {
            JsonWebResponse(mapOf("foo" to "bar"))
            .header("x-test-header", "Just a test!")})
        get("/db_test", webResponseDb(dataSource) { dbSess ->
            JsonWebResponse(
                dbSess.single(queryOf("SELECT 1"), ::mapFromRow)
            )
        })
    }
}
