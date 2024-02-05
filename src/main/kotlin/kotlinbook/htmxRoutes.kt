package kotlinbook

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import io.ktor.server.webjars.*
import kotlinbook.web.WebResponse
import kotlinbook.web.WebResponseSupport
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.LocalTime

data class ListItem(val id: Int, val time: LocalTime)

fun Application.initHtmxRoutes() {
    install(Webjars)
    install(Thymeleaf) {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            characterEncoding = "utf-8"
        })
    }

    routing {
        get("/htmx", WebResponseSupport.webResponse {
            WebResponse.ThymeleafWebResponse(
                "index", mapOf(
                    "listItems" to (1..100).map { listItemId ->
                        ListItem(listItemId, LocalTime.now())
                    })
            )
        })
        get("/htmx/click-me", WebResponseSupport.webResponse {
            WebResponse.ThymeleafWebResponse("click-me")
        })
        get("/htmx/get-error", WebResponseSupport.webResponse {
            WebResponse.TextWebResponse("error", statusCode = 500)
        })
        get("/htmx/list-item/{id}", WebResponseSupport.webResponse {
            val id = checkNotNull(call.parameters["id"]).toInt()
            WebResponse.ThymeleafWebResponse(
                "list-item",
                mapOf(
                    "item" to ListItem(id, LocalTime.now())
                )
            )
        })
    }
}
