package kotlinbook

import com.google.gson.Gson
import com.typesafe.config.ConfigFactory
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
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger("kotlinbook.Main")

fun main() {
    log.debug("Starting application...")

    createAppConfig(System.getenv("KOTLINBOOK_ENV") ?: "local").also { config ->
        log.info("Configuration loaded successfully:\n{}", config.formatForLogging())

        embeddedServer(Netty, port = config.httpPort) {
            createKtorApplication()
        }.start(wait = true)
    }
}

fun createAppConfig(env: String): WebappConfig =
    ConfigFactory
        .parseResources("app-${env}.conf")
        .withFallback(ConfigFactory.parseResources("app.conf"))
        .resolve()
        .let {
            WebappConfig(
                httpPort = it.getInt("httpPort"),
                dbUsername = it.getString("dbUsername"),
                dbPassword = it.getString("dbPassword"),
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

fun Application.createKtorApplication() {
    routing {
        get("/", webResponse {
            TextWebResponse("Hello, World!").header("x-asdf", Date().toString())
        })
        get("/param_test", webResponse {
            TextWebResponse("The param is: ${call.request.queryParameters["foo"]}")
        })
        get("/json_test_with_header", webResponse { JsonWebResponse(mapOf("foo" to "bar"))
            .header("x-test-header", "Just a test!")})
    }
}
