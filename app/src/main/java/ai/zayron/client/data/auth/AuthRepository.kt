package ai.zayron.client.data.auth

/**
 * Non-secret account information returned after the backend authenticates a user.
 *
 * Access and refresh tokens deliberately do not appear in this model or in UI state.
 */
data class AuthSession(
    val userId: String,
    val email: String,
    val displayName: String?,
    val expiresAtEpochSeconds: Long?
)

/** Stable failure categories; never place raw server response text or credentials here. */
enum class AuthFailureCode {
    INVALID_CREDENTIALS,
    ACCOUNT_DISABLED,
    EMAIL_NOT_VERIFIED,
    SESSION_EXPIRED,
    NETWORK_UNAVAILABLE,
    SERVICE_UNAVAILABLE,
    UNKNOWN
}

/** Result of an authentication operation. */
sealed class AuthResult {
    data class Authenticated(val session: AuthSession) : AuthResult()
    data class Failed(val reason: AuthFailureCode) : AuthResult()
}

sealed class SignUpResult {
    data class Registered(val confirmationRequired: Boolean, val session: AuthSession? = null) : SignUpResult()
    data class Failed(val reason: AuthFailureCode) : SignUpResult()
}

/**
 * Credentials are supplied only for the duration of a sign-in attempt.
 * Implementations must never persist or log the password.
 */
data class SignInCredentials(
    val email: String,
    val password: CharArray
)

/**
 * Contract for backend-backed authentication.
 *
 * BackendAuthRepository implements login, secure session persistence, session
 * restoration, serialized refresh and logout. The service remains unavailable
 * until a real HTTPS backend and Supabase project are configured.
 */
interface AuthRepository {
    suspend fun signIn(credentials: SignInCredentials): AuthResult

    /** Restores a valid session from an approved secure session store, if present. */
    suspend fun restoreSession(): AuthSession?

    /** Revokes the server session and clears local secure session material. */
    suspend fun signOut()
}
