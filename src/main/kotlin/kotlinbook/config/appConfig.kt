package kotlinbook.config

import com.typesafe.config.ConfigFactory
import kotlinbook.WebappConfig

fun createAppConfig(env: String): WebappConfig =
    ConfigFactory.parseResources("app-${env}.conf")
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