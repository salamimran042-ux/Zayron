package ai.zayron.client.data.auth

import ai.zayron.client.data.network.NetworkResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendAuthRepositoryTest {
    private class FakeStore(var tokens: SessionTokens? = null) : SecureSessionStore {
        override fun save(tokens: SessionTokens) { this.tokens = tokens }
        override fun read(): SessionTokens? = tokens
        override fun clear() { tokens = null }
    }

    private class FakeApi : AuthApi {
        val posts = mutableListOf<Triple<String, JSONObject, String?>>()
        val gets = mutableListOf<Pair<String, String>>()
        var postResult: NetworkResult<String> = NetworkResult.Success(sessionJson(), 200)
        var getResult: NetworkResult<String> = NetworkResult.Success("""{"id":"user-123","email":"user@example.com"}""", 200)

        override suspend fun post(path: String, body: JSONObject, accessToken: String?): NetworkResult<String> {
            posts += Triple(path, body, accessToken)
            return postResult
        }
        override suspend fun get(path: String, accessToken: String): NetworkResult<String> {
            gets += path to accessToken
            return getResult
        }
    }

    @Test fun signInPersistsTokensAndReturnsOnlyProfile() = runBlocking {
        val store = FakeStore()
        val api = FakeApi()
        val password = "secret-password".toCharArray()
        val result = BackendAuthRepository(store, api) { 100L }
            .signIn(SignInCredentials(" user@example.com ", password))
        assertTrue(result is AuthResult.Authenticated)
        assertEquals("user@example.com", (result as AuthResult.Authenticated).session.email)
        assertEquals("access-token", store.tokens?.accessToken)
        assertEquals("rotated-refresh-token", store.tokens?.refreshToken)
        assertEquals("secret-password", api.posts.single().second.getString("password"))
        assertTrue(password.all { it == '\u0000' })
    }

    @Test fun expiredSessionRefreshesBeforeProfileRestore() = runBlocking {
        val store = FakeStore(SessionTokens("expired-access", "old-refresh", 99L,
            "user@example.com", "Zayron User"))
        val api = FakeApi()
        val result = BackendAuthRepository(store, api) { 100L }.restoreSession()
        assertEquals("user-123", result?.userId)
        assertEquals("rotated-refresh-token", store.tokens?.refreshToken)
        assertEquals("api/v1/auth/refresh", api.posts.single().first)
        assertEquals("api/v1/me", api.gets.single().first)
        assertEquals("access-token", api.gets.single().second)
    }

    @Test fun revokedRefreshSessionClearsEncryptedSession() = runBlocking {
        val store = FakeStore(SessionTokens("expired-access", "revoked-refresh", 99L))
        val api = FakeApi().apply { postResult = NetworkResult.HttpError(401, """{"detail":{"code":"SESSION_EXPIRED"}}""") }
        val result = BackendAuthRepository(store, api) { 100L }.restoreSession()
        assertNull(result)
        assertNull(store.tokens)
    }

    @Test fun signOutClearsLocalSessionEvenWhenNetworkFails() = runBlocking {
        val store = FakeStore(SessionTokens("access-token", "refresh-token", 200L))
        val api = FakeApi().apply { postResult = NetworkResult.ConnectionError }
        BackendAuthRepository(store, api).signOut()
        assertNull(store.tokens)
        assertEquals("api/v1/auth/logout", api.posts.single().first)
        assertEquals("access-token", api.posts.single().third)
    }

    @Test fun missingBackendDoesNotPersistCredentials() = runBlocking {
        val store = FakeStore()
        val api = FakeApi().apply { postResult = NetworkResult.NotConfigured }
        val result = BackendAuthRepository(store, api)
            .signIn(SignInCredentials("user@example.com", "password".toCharArray()))
        assertEquals(AuthFailureCode.SERVICE_UNAVAILABLE, (result as AuthResult.Failed).reason)
        assertNull(store.tokens)
    }

    companion object {
        private fun sessionJson() = """{
            "access_token":"access-token",
            "refresh_token":"rotated-refresh-token",
            "expires_at":2000000000,
            "user":{"id":"user-123","email":"user@example.com","display_name":"Zayron User"}
        }"""
    }
}
