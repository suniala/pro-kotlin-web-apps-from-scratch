package kotlinbook.db

import kotlinbook.test.testTx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserTest {
    @Test
    fun testHelloWorld() {
        assertEquals(1, 1)
    }

    @Test
    fun testCreateUser() {
        testTx { dbSess ->
            val userAId = createUser(
                dbSess,
                email = "augustlilleaas@me.com",
                name = "August Lilleaas",
                passwordText = "1234"
            )
            val userBId = createUser(
                dbSess,
                email = "august@augustl.com",
                name = "August Lilleaas",
                passwordText = "1234"
            )
            assertNotEquals(userAId, userBId)
        }
    }

    @Test
    fun testCreateAnotherUser() {
        testTx { dbSess ->
            createUser(
                dbSess,
                email = "augustlilleaas@me.com",
                name = "August Lilleaas",
                passwordText = "1234"
            )
            // ... write some assertions here ...
        }
    }

    @Test
    fun testListUsers() {
        testTx { dbSess ->
            val usersBefore = listUsers(dbSess)
            val userAId = createUser(
                dbSess,
                email = "augustlilleaas@me.com",
                name = "August Lilleaas",
                passwordText = "1234"
            )
            val userBId = createUser(
                dbSess,
                email = "august@augustl.com",
                name = "August Lilleaas",
                passwordText = "1234"
            )
            val users = listUsers(dbSess)

            // Do a relative assert because db already had some users due to migrations
            assertEquals(2, users.size - usersBefore.size)
            assertNotNull(users.find { it.id == userAId })
            assertNotNull(users.find { it.id == userBId })
        }
    }

    @Test
    fun testGetUser() {
        testTx { dbSess ->
            val userId = createUser(
                dbSess,
                email = "augustlilleaas@me.com",
                name = "August Lilleaas",
                passwordText = "1234",
                tosAccepted = true
            )
            assertNull(getUser(dbSess, -9000))
            val user = getUser(dbSess, userId)
            assertNotNull(user)
            assertEquals(user.email, "augustlilleaas@me.com")
        }
    }
}
