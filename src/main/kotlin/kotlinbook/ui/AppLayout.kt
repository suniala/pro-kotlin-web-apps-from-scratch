package kotlinbook.ui

import io.ktor.server.html.*
import kotlinx.html.BODY
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.styleLink
import kotlinx.html.title

class AppLayout(
    val pageTitle: String? = null
): Template<HTML> {
    val pageBody = Placeholder<BODY>()
    override fun HTML.apply() {
        val pageTitlePrefix = if (pageTitle == null) {
            ""
        } else {
            "${pageTitle} - "
        }
        head {
            title {
                +"${pageTitlePrefix}KotlinBook"
            }
            styleLink("/app.css")
        }
        body {
            insert(pageBody)
        }
    }
}
