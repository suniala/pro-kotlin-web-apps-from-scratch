package kotlinbook.db

import kotlinbook.db.DBSupport.mapFromRow
import kotlinbook.web.bcryptHasher
import kotliquery.Session
import kotliquery.queryOf

data class User(
    val id: Long,
    val email: String,
    val tosAccepted: Boolean,
    val name: String?,
) {
    companion object {
        fun fromRow(row: Map<String, Any?>) = User(
            id = row["id"] as Long,
            email = row["email"] as String,
            name = row["name"] as? String,
            tosAccepted = row["tos_accepted"] as Boolean,
        )
    }
}

fun createUser(
    dbSession: Session,
    email: String,
    name: String,
    passwordText: String,
    tosAccepted: Boolean = false,
): Long {
    val userId = dbSession.updateAndReturnGeneratedKey(
        queryOf(
            """
              INSERT INTO user_t
              (email, name, tos_accepted, password_hash)
              VALUES (:email, :name, :tosAccepted, :passwordHash)
              """,
            mapOf(
                "email" to email,
                "name" to name,
                "tosAccepted" to tosAccepted,
                "passwordHash" to bcryptHasher.hash(
                    10,
                    passwordText.toByteArray(Charsets.UTF_8)
                )
            )
        )
    )
    return checkNotNull(userId)
}

fun getUser(dbSess: Session, id: Long): User? {
    return dbSess
        .single(
            queryOf("SELECT * FROM user_t WHERE id = ?", id),
            ::mapFromRow
        )
        ?.let(User.Companion::fromRow)
}

fun listUsers(dbSession: Session) =
    dbSession
        .list(queryOf("SELECT * FROM user_t"), ::mapFromRow)
        .map(User.Companion::fromRow)
