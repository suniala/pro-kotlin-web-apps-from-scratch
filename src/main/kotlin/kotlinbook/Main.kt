package kotlinbook

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("kotlinbook.Main")

fun main() {
    log.debug("Starting application...")

    embeddedServer(Netty, port = 4207) {
        createKtorApplication()
    }.start(wait = true)
}
fun Application.createKtorApplication() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
    }
}
