package kotlinbook.db

import kotlinbook.db.DBSupport.mapFromRow
import kotlinbook.test.testTx
import kotlinbook.web.authenticateUser
import kotliquery.queryOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PasswordTest {
    @Test
    fun testVerifyUserPassword() = testTx { dbSess ->
        val userId = createUser(
            dbSess,
            email = "a@b.com",
            name = "August Lilleaas",
            passwordText = "1234",
            tosAccepted = true
        )
        assertEquals(
            userId,
            authenticateUser(dbSess, "a@b.com", "1234")
        )
        assertEquals(
            null,
            authenticateUser(dbSess, "a@b.com", "incorrect")
        )
        assertEquals(
            null,
            authenticateUser(dbSess, "does@not.exist", "1234")
        )
    }

    @Test
    fun testUserPasswordSalting() = testTx { dbSess ->
        val userAId = createUser(
            dbSess,
            email = "a@b.com",
            name = "A",
            passwordText = "1234",
            tosAccepted = true
        )
        val userBId = createUser(
            dbSess,
            email = "x@b.com",
            name = "X",
            passwordText = "1234",
            tosAccepted = true
        )
        val userAHash = checkNotNull(
            dbSess.single(
                queryOf("SELECT * FROM user_t WHERE id = ?", userAId),
                ::mapFromRow
            )
        )["password_hash"] as ByteArray
        val userBHash = checkNotNull(
            dbSess.single(
                queryOf("SELECT * FROM user_t WHERE id = ?", userBId),
                ::mapFromRow
            )
        )["password_hash"] as ByteArray
        assertFalse(userAHash.contentEquals(userBHash))
    }
}
