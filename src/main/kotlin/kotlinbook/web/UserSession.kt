package kotlinbook.web

import io.ktor.server.auth.*

/**
 * This will be sent to the browser (encrypted) so remember the limit of 4 kilobytes.
 */
data class UserSession(val userId: Long): Principal