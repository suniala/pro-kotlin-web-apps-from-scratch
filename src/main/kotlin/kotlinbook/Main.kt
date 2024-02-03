package kotlinbook

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinbook.config.createAndMigrateDataSource
import kotlinbook.config.createAppConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("kotlinbook.Main")

fun main() {
    log.debug("Starting application...")

    createAppConfig(System.getenv("KOTLINBOOK_ENV") ?: "local").also { config ->
        log.info("Configuration loaded successfully:\n{}", config.formatForLogging())
        log.debug("tee1")

        embeddedServer(Netty, port = 9876) {
            initMockRoutes()
        }.start(wait = false)

        embeddedServer(Netty, port = config.httpPort) {
            val dataSource = createAndMigrateDataSource(config)
            initSessionRoutes(config, dataSource)
            initPublicRoutes(config, dataSource)
        }.start(wait = true)
    }
}
