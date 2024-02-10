package kotlinbook

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import io.ktor.server.webjars.*
import kotlinbook.Fragments.listItem
import kotlinbook.Fragments.suburbSelect
import kotlinbook.db.DBSupport
import kotlinbook.web.WebResponse
import kotlinbook.web.WebResponseSupport
import kotlinx.html.*
import kotliquery.queryOf
import java.time.LocalTime
import javax.sql.DataSource

data class Town(val id: Long, val name: String) {
    companion object {
        fun fromRow(row: Map<String, Any?>): Town = Town(
            id = row["id"] as Long,
            name = row["name"] as String,
        )
    }
}

data class Suburb(val id: Long, val townId: Long, val name: String) {
    companion object {
        fun fromRow(row: Map<String, Any?>): Suburb = Suburb(
            id = row["id"] as Long,
            townId = row["town_id"] as Long,
            name = row["name"] as String,
        )
    }
}

fun Application.initHtmxRoutes(dataSource: DataSource) {
    install(Webjars)

    routing {
        get("/htmx", WebResponseSupport.webResponseDb(dataSource) { dbSess ->
            val towns = dbSess.list(queryOf("select id, name from town order by name"), DBSupport::mapFromRow)
                .map(Town.Companion::fromRow)
            val suburbs = dbSess.list(
                queryOf(
                    "select id, town_id, name from suburb where town_id = :town_id order by name",
                    mapOf("town_id" to towns.first().id)
                ), DBSupport::mapFromRow
            ).map(Suburb.Companion::fromRow)

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
                                attributes["name"] = "town_id"
                                attributes["hx-get"] = "/htmx/suburbs"
                                attributes["hx-target"] = "#suburb"
                                attributes["hx-select"] = "option"
                                towns.map {
                                    option {
                                        attributes["value"] = "${it.id}"
                                        +it.name
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
                        suburbSelect(suburbs)
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
        get("/htmx/suburbs", WebResponseSupport.webResponseDb(dataSource) { dbSess ->
            val townId = checkNotNull(call.parameters["town_id"]).toLong()
            val suburbs = dbSess.list(
                queryOf(
                    "select id, town_id, name from suburb where town_id = :town_id order by name",
                    mapOf("town_id" to townId)
                ), DBSupport::mapFromRow
            ).map(Suburb.Companion::fromRow)

            WebResponse.HtmlWebResponse(FragmentLayout().apply {
                fragment {
                    suburbSelect(suburbs)
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

    fun FlowContent.suburbSelect(suburbs: List<Suburb>) {
        select {
            id = "suburb"
            name = "suburb"

            suburbs.map {
                option {
                    attributes["value"] = "${it.id}"
                    +it.name
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
