package kotlinbook

sealed class WebResponse {
    abstract val statusCode: Int
    abstract val headers: Map<String, List<String>>

    abstract fun copyResponse(statusCode: Int, headers: Map<String, List<String>>): WebResponse

    fun headers(): Map<String, List<String>> =
        headers
            .map { it.key.lowercase() to it.value }
            .fold(mapOf()) { res, (k, v) ->
                res.plus(Pair(k, res.getOrDefault(k, listOf()).plus(v)))
            }

    fun header(headerName: String, headerValue: String) = header(headerName, listOf(headerValue))

    fun header(headerName: String, headerValue: List<String>) =
        copyResponse(
            statusCode,
            headers
                .plus(
                    Pair(
                        headerName,
                        headers.getOrDefault(headerName, listOf()).plus(headerValue)
                    )
                )
        )

    data class TextWebResponse(
        val body: String,
        override val statusCode: Int = 200,
        override val headers: Map<String, List<String>> = mapOf()
    ) : WebResponse() {
        override fun copyResponse(statusCode: Int, headers: Map<String, List<String>>): WebResponse =
            copy(body = body, statusCode = statusCode, headers = headers)
    }

    data class JsonWebResponse(
        val body: Any?,
        override val statusCode: Int = 200,
        override val headers: Map<String, List<String>> = mapOf()
    ) : WebResponse() {
        override fun copyResponse(statusCode: Int, headers: Map<String, List<String>>): WebResponse =
            copy(body = body, statusCode = statusCode, headers = headers)
    }
}
