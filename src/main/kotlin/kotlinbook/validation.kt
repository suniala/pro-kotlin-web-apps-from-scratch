package kotlinbook

import arrow.core.Either
import arrow.core.left
import arrow.core.right

data class ValidationError(val error: String) {}
data class MyUser(val email: String, val password: String)

fun validateEmail(email: Any?): Either<ValidationError, String> {
    if (email !is String) {
        return ValidationError("E-mail must be set").left()
    }
    if (!email.contains("@")) {
        return ValidationError("Invalid e-mail").left()
    }
    return email.right()
}

fun validatePassword(password: Any?): Either<ValidationError, String> {
    if (password !is String) {
        return ValidationError("Password must be set").left()
    }
    if (password == "1234") {
        return ValidationError("Insecure password").left()
    }
    return password.right()
}
