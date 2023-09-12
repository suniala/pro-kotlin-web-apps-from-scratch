package kotlinbook.web

import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import javax.sql.DataSource

object WebResponseSupport {
    class KtorJsonWebResponse(val body: Any?, override val status: HttpStatusCode = HttpStatusCode.OK) :
        OutgoingContent.ByteArrayContent() {
        override val contentType: ContentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)

        override fun bytes() = Gson().toJson(body).toByteArray(Charsets.UTF_8)
    }

    fun webResponse(handler: suspend PipelineContext<Unit, ApplicationCall>.() -> WebResponse): PipelineInterceptor<Unit, ApplicationCall> {
        return {
            val resp = this.handler()
            val statusCode = HttpStatusCode.fromValue(resp.statusCode)

            for ((name, values) in resp.headers()) {
                for (value in values) {
                    call.response.header(name, value)
                }
            }

            when (resp) {
                is WebResponse.TextWebResponse -> {
                    call.respondText(text = resp.body, status = statusCode)
                }

                is WebResponse.JsonWebResponse -> {
                    call.respond(KtorJsonWebResponse(body = resp.body, status = statusCode))
                }
            }
        }
    }

    fun webResponseDb(
        dataSource: DataSource,
        handler: suspend PipelineContext<Unit, ApplicationCall>.(
            dbSess: Session,
        ) -> WebResponse,
    ) = webResponse {
        sessionOf(
            dataSource,
            returnGeneratedKey = true
        ).use { dbSess ->
            handler(dbSess)
        }
    }

    fun webResponseTx(
        dataSource: DataSource,
        handler: suspend PipelineContext<Unit, ApplicationCall>.(
            dbSess: TransactionalSession,
        ) -> WebResponse,
    ) = webResponseDb(dataSource) { dbSess ->
        dbSess.transaction { txSess ->
            handler(txSess)
        }
    }
}
