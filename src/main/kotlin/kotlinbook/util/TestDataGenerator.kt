package kotlinbook.util

import java.util.Random
import kotlin.random.asKotlinRandom

object TestDataGenerator {
    private val rnd = Random().asKotlinRandom()
    fun username() = "Name${rnd.nextLong(100, 1000)}"
}
