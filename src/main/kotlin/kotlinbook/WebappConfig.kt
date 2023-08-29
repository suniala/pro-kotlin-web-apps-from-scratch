package kotlinbook

import kotlin.reflect.full.declaredMemberProperties

data class WebappConfig(
    val httpPort: Int,
    val dbUser: String,
    val dbPassword: String,
    val dbUrl: String,
) {
    fun formatForLogging(): String = WebappConfig::class.declaredMemberProperties
        .sortedBy { it.name }
        .joinToString(separator = "\n") {
            if (secretsRegex.containsMatchIn(it.name)) {
                "${it.name} = ${it.get(this).toString().take(2)}*****"
            } else {
                "${it.name} = ${it.get(this)}"
            }
        }

    private companion object {
        val secretsRegex = "password|secret|key".toRegex(RegexOption.IGNORE_CASE)
    }
}
