package kotlinbook

import io.ktor.util.*
import java.security.SecureRandom

object KeyGenerator {
    private fun getRandomBytesHex(length: Int) =
        ByteArray(length)
            .also { SecureRandom().nextBytes(it) }
            .let(::hex)

    @JvmStatic
    fun main(args: Array<String>) {
        println("cookieEncryptionKey: ${getRandomBytesHex(16)}")
        println("cookieSigningKey: ${getRandomBytesHex(32)}")
    }
}
