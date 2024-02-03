package kotlinbook

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import io.ktor.server.webjars.*
import kotlinbook.web.WebResponse
import kotlinbook.web.WebResponseSupport
import kotlinx.html.BODY
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.title

fun Application.initHtmxRoutes() {
    install(Webjars)

    routing {
        get("/htmx", WebResponseSupport.webResponse {
            WebResponse.HtmlWebResponse(HtmxLayout("HTMX").apply {
                pageBody {
                    h1 {
                        +"HTMX Demo"
                    }
                    button {
                        attributes["hx-get"] = "/htmx/click-me"
                        attributes["hx-swap"] = "outerHTML"
                        +"Click me!"
                    }
                }
            })
        })
        get("/htmx/click-me", WebResponseSupport.webResponse {
            WebResponse.HtmlWebResponse(FragmentLayout().apply {
                fragment {
                    p { +"I have been clicked." }
                }
            })
        })
    }
}

private class HtmxLayout(
    val pageTitle: String? = null
) : Template<HTML> {
    val pageBody = Placeholder<BODY>()

    override fun HTML.apply() {
        val pageTitlePrefix = if (pageTitle == null) {
            ""
        } else {
            "$pageTitle - "
        }
        head {
            title {
                +"${pageTitlePrefix}KotlinBook"
            }
            script(src = htmx("dist/htmx.min.js")) {}
            script(src = htmx("dist/ext/json-enc.js")) {}
            script(src = htmx("dist/ext/sse.js")) {}
        }
        body {
            insert(pageBody)
        }
    }

    companion object {
        private val htmx = { e: String -> "webjars/htmx.org/1.9.4/$e" }
    }
}

private class FragmentLayout() : Template<HTML> {
    val fragment = Placeholder<BODY>()
    override fun HTML.apply() {
        body {
            insert(fragment)
        }
    }
}
