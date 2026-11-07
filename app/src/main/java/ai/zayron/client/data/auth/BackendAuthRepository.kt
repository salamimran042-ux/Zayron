package ai.zayron.client.data.auth

import ai.zayron.client.data.network.ApiClient
import ai.zayron.client.data.network.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Testable HTTP boundary for the account/session API. */
interface AuthApi {
    suspend fun post(path: String, body: JSONObject, accessToken: String? = null): NetworkResult<String>
    suspend fun get(path: String, accessToken: String): NetworkResult<String>
}

internal class OkHttpAuthApi : AuthApi {
    override suspend fun post(path: String, body: JSONObject, accessToken: String?): NetworkResult<String> {
        val url = ApiClient.urlFor(path) ?: return NetworkResult.NotConfigured
        val builder = Request.Builder().url(url).header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (!accessToken.isNullOrBlank()) builder.header("Authorization", "Bearer $accessToken")
        return ApiClient.execute(builder.build())
    }

    override suspend fun get(path: String, accessToken: String): NetworkResult<String> {
        val url = ApiClient.urlFor(path) ?: return NetworkResult.NotConfigured
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken").get().build()
        return ApiClient.execute(request)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Backend-backed account lifecycle. All token persistence stays inside SecureSessionStore.
 * A mutex prevents concurrent sign-in, refresh and sign-out operations from racing.
 */
class BackendAuthRepository(
    private val sessionStore: SecureSessionStore,
    private val api: AuthApi = OkHttpAuthApi(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) : AuthRepository {
    private val sessionMutex = Mutex()

    override suspend fun signIn(credentials: SignInCredentials): AuthResult =
        sessionMutex.withLock {
            try {
                val body = JSONObject()
                    .put("email", credentials.email.trim())
                    .put("password", String(credentials.password))
                when (val result = api.post(LOGIN_PATH, body)) {
                    is NetworkResult.Success -> {
                        val parsed = parseSessionTokens(result.data)
                            ?: return@withLock AuthResult.Failed(AuthFailureCode.UNKNOWN)
                        withContext(Dispatchers.IO) { sessionStore.save(parsed.first) }
                        AuthResult.Authenticated(parsed.second)
                    }
                    else -> AuthResult.Failed(mapFailure(result))
                }
            } catch (_: Exception) {
                AuthResult.Failed(AuthFailureCode.UNKNOWN)
            } finally {
                credentials.password.fill('\u0000')
            }
        }

    override suspend fun restoreSession(): AuthSession? = sessionMutex.withLock {
        var tokens = withContext(Dispatchers.IO) { sessionStore.read() } ?: return@withLock null
        if (tokens.accessExpiresAtEpochSeconds <= nowEpochSeconds() + EXPIRY_SKEW_SECONDS) {
            tokens = refreshLocked(tokens) ?: return@withLock null
        }

        var result = api.get(ME_PATH, tokens.accessToken)
        if (result is NetworkResult.HttpError && result.statusCode == 401) {
            tokens = refreshLocked(tokens) ?: return@withLock null
            result = api.get(ME_PATH, tokens.accessToken)
        }

        when (result) {
            is NetworkResult.Success -> parseProfile(result.data, tokens)
            is NetworkResult.HttpError -> {
                if (result.statusCode == 401) clearSession()
                null
            }
            else -> null
        }
    }

    override suspend fun signOut(): Unit = sessionMutex.withLock {
        val tokens = withContext(Dispatchers.IO) { sessionStore.read() }
        try {
            if (tokens != null) api.post(LOGOUT_PATH, JSONObject(), tokens.accessToken)
        } finally {
            clearSession()
        }
        Unit
    }

    private suspend fun refreshLocked(current: SessionTokens): SessionTokens? {
        val result = api.post(REFRESH_PATH, JSONObject().put("refresh_token", current.refreshToken))
        if (result is NetworkResult.Success) {
            val parsed = parseSessionTokens(result.data) ?: run {
                clearSession()
                return null
            }
            withContext(Dispatchers.IO) { sessionStore.save(parsed.first) }
            return parsed.first
        }
        if (result is NetworkResult.HttpError && result.statusCode in 400..403) clearSession()
        return null
    }

    private fun parseSessionTokens(raw: String): Pair<SessionTokens, AuthSession>? = try {
        val root = JSONObject(raw)
        val user = root.getJSONObject("user")
        val userId = user.getString("id")
        val email = user.getString("email")
        val displayName = user.optString("display_name").takeIf { it.isNotBlank() }
        val tokens = SessionTokens(
            accessToken = root.getString("access_token"),
            refreshToken = root.getString("refresh_token"),
            accessExpiresAtEpochSeconds = root.getLong("expires_at"),
            email = email,
            displayName = displayName
        )
        tokens to AuthSession(userId, email, displayName, tokens.accessExpiresAtEpochSeconds)
    } catch (_: Exception) {
        null
    }

    private fun parseProfile(raw: String, tokens: SessionTokens): AuthSession? = try {
        val profile = JSONObject(raw)
        val id = profile.getString("id")
        val email = profile.optString("email").takeIf { it.isNotBlank() } ?: tokens.email ?: return null
        val name = profile.optString("display_name").takeIf { it.isNotBlank() } ?: tokens.displayName
        AuthSession(id, email, name, tokens.accessExpiresAtEpochSeconds)
    } catch (_: Exception) {
        null
    }

    private suspend fun clearSession() = withContext(Dispatchers.IO) { sessionStore.clear() }

    private fun mapFailure(result: NetworkResult<*>): AuthFailureCode = when (result) {
        NetworkResult.ConnectionError -> AuthFailureCode.NETWORK_UNAVAILABLE
        NetworkResult.NotConfigured, NetworkResult.RejectedRequest ->
            AuthFailureCode.SERVICE_UNAVAILABLE
        NetworkResult.AuthenticationRequired -> AuthFailureCode.SESSION_EXPIRED
        is NetworkResult.HttpError -> when (result.statusCode) {
            401 -> AuthFailureCode.INVALID_CREDENTIALS
            429, in 500..599 -> AuthFailureCode.SERVICE_UNAVAILABLE
            else -> AuthFailureCode.UNKNOWN
        }
        is NetworkResult.StreamError -> AuthFailureCode.UNKNOWN
        is NetworkResult.Success<*> -> AuthFailureCode.UNKNOWN
    }

    private companion object {
        const val LOGIN_PATH = "api/v1/auth/login"
        const val REFRESH_PATH = "api/v1/auth/refresh"
        const val LOGOUT_PATH = "api/v1/auth/logout"
        const val ME_PATH = "api/v1/me"
        const val EXPIRY_SKEW_SECONDS = 30L
    }
}
