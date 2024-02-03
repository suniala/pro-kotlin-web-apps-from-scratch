package kotlinbook

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinbook.web.WebResponse
import kotlinbook.web.WebResponseSupport
import kotlinx.coroutines.delay

fun Application.initMockRoutes() {
    routing {
        get("/random_number", WebResponseSupport.webResponse {
            val num = (200L..2000L).random()
            delay(num)
            WebResponse.TextWebResponse(num.toString())
        })
        get("/ping", WebResponseSupport.webResponse {
            WebResponse.TextWebResponse("pong")
        })
        post("/reverse", WebResponseSupport.webResponse {
            WebResponse.TextWebResponse(call.receiveText().reversed())
        })
    }
}
