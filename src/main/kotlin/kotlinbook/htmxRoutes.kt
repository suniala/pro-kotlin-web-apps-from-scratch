package kotlinbook

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import io.ktor.server.webjars.*
import kotlinbook.Fragments.listItem
import kotlinbook.Fragments.suburbSelect
import kotlinbook.web.WebResponse
import kotlinbook.web.WebResponseSupport
import kotlinx.html.*
import java.time.LocalTime

val models = mapOf(
    "Tampere" to listOf("Tammela", "Teisko", "Tohloppi"),
    "Helsinki" to listOf("Hakaniemi", "Hietaniemi", "Hermanni")
)

fun Application.initHtmxRoutes() {
    install(Webjars)

    routing {
        get("/htmx", WebResponseSupport.webResponse {
            val town = models.entries.first().key

            WebResponse.HtmlWebResponse(HtmxLayout("HTMX").apply {
                pageBody {
                    h1 {
                        +"HTMX Demo"
                    }
                    div {
                        button {
                            attributes["hx-get"] = "/htmx/click-me"
                            attributes["hx-swap"] = "outerHTML"
                            +"Click me!"
                        }
                    }
                    div {
                        button {
                            attributes["hx-get"] = "/htmx/get-error"
                            +"Generate an error"
                        }
                    }
                    div {
                        div {
                            label {
                                attributes["for"] = "town"
                                +"Town: "
                            }
                            select {
                                id = "town"
                                attributes["name"] = "town"
                                attributes["hx-get"] = "/htmx/suburbs"
                                attributes["hx-target"] = "#suburb"
                                attributes["hx-select"] = "option"
                                models.map {
                                    option {
                                        attributes["value"] = it.key
                                        +it.key
                                    }
                                }
                            }
                        }
                    }
                    div {
                        label {
                            attributes["for"] = "suburb"
                            +"Suburb: "
                        }
                        suburbSelect(town)
                    }
                    ol {
                        (1..100).map {
                            listItem(it)
                        }
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
        get("/htmx/get-error", WebResponseSupport.webResponse {
            WebResponse.TextWebResponse("error", statusCode = 500)
        })
        get("/htmx/list-item/{id}", WebResponseSupport.webResponse {
            val listItemId = checkNotNull(call.parameters["id"]).toInt()
            WebResponse.HtmlWebResponse(FragmentLayout().apply {
                fragment {
                    ol {
                        listItem(listItemId)
                    }
                }
            })
        })
        get("/htmx/suburbs", WebResponseSupport.webResponse {
            val town = checkNotNull(call.parameters["town"])
            WebResponse.HtmlWebResponse(FragmentLayout().apply {
                fragment {
                    suburbSelect(town)
                }
            })
        })
    }
}

object Fragments {
    fun OL.listItem(listItemId: Int) {
        li {
            id = "listItem$listItemId"
            attributes["hx-get"] = "/htmx/list-item/$listItemId"
            attributes["hx-swap"] = "outerHTML"
            attributes["hx-select"] = "li"
            +"List item $listItemId updated at ${LocalTime.now()}"
        }
    }

    fun FlowContent.suburbSelect(town: String) {
        select {
            id = "suburb"
            name = "suburb"

            checkNotNull(models[town]).map {
                option {
                    attributes["value"] = it
                    +it
                }
            }
        }
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

            script {
                unsafe {
                    +"""
                    htmx.on("htmx:responseError", function (evt) {
                        alert('Error: ' + evt.detail.xhr.status);
                    });
                    """.trimIndent()
                }
            }
        }
    }

    companion object {
        private val htmx = { e: String -> "webjars/htmx.org/1.9.4/$e" }
    }
}

private class FragmentLayout : Template<HTML> {
    val fragment = Placeholder<BODY>()
    override fun HTML.apply() {
        body {
            insert(fragment)
        }
    }
}
