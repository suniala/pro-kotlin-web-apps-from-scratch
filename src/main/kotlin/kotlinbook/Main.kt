package kotlinbook

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

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

fun Application.createKtorApplication() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
    }
}
