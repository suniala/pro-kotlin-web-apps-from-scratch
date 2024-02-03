package kotlinbook

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinbook.db.getUser
import kotlinbook.ui.AppLayout
import kotlinbook.web.UserSession
import kotlinbook.web.WebResponse
import kotlinbook.web.WebResponseSupport
import kotlinbook.web.authenticateUser
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotliquery.sessionOf
import javax.sql.DataSource

/**
 * To separate our authentication setup from the rest of our routes, we’ll set up authentication and
 * authenticated routes here.
 */
fun Application.initSessionRoutes (
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
        get("/login", WebResponseSupport.webResponse {
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
            get("/secret", WebResponseSupport.webResponseDb(dataSource) { dbSess ->
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