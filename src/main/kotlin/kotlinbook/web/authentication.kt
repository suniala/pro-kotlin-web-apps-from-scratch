package kotlinbook.web

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinbook.db.DBSupport.mapFromRow
import kotliquery.Session
import kotliquery.queryOf

val bcryptHasher: BCrypt.Hasher = BCrypt.withDefaults()
val bcryptVerifier: BCrypt.Verifyer = BCrypt.verifyer()

fun authenticateUser(
    dbSession: Session,
    email: String,
    passwordText: String,
): Long? {
    return dbSession.single(
        queryOf("SELECT * FROM user_t WHERE email = ?", email),
        ::mapFromRow
    )?.let {
        val pwHash = it["password_hash"] as ByteArray
        if (bcryptVerifier.verify(
                passwordText.toByteArray(Charsets.UTF_8),
                pwHash
            ).verified
        ) {
            return it["id"] as Long
        } else {
            return null
        }
    }
}
