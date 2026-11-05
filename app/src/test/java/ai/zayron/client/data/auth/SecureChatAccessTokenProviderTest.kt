package ai.zayron.client.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SecureChatAccessTokenProviderTest {
    private class FakeSessionStore(var tokens: SessionTokens? = null) : SecureSessionStore {
        override fun save(tokens: SessionTokens) {
            this.tokens = tokens
        }

        override fun read(): SessionTokens? = tokens

        override fun clear() {
            tokens = null
        }
    }

    @Test
    fun returnsAccessTokenWhenExpiryIsInFuture() = runBlocking {
        val store = FakeSessionStore(SessionTokens("access-secret", "refresh-secret", 101L))
        val provider = SecureChatAccessTokenProvider(store) { 100L }

        assertEquals("access-secret", provider.getAccessToken())
    }

    @Test
    fun refusesAccessTokenAtExactExpiryBoundary() = runBlocking {
        val store = FakeSessionStore(SessionTokens("access-secret", "refresh-secret", 100L))
        val provider = SecureChatAccessTokenProvider(store) { 100L }

        assertNull(provider.getAccessToken())
    }

    @Test
    fun refusesExpiredAccessToken() = runBlocking {
        val store = FakeSessionStore(SessionTokens("access-secret", "refresh-secret", 99L))
        val provider = SecureChatAccessTokenProvider(store) { 100L }

        assertNull(provider.getAccessToken())
    }

    @Test
    fun returnsNullWhenNoSessionExists() = runBlocking {
        val provider = SecureChatAccessTokenProvider(FakeSessionStore()) { 100L }

        assertNull(provider.getAccessToken())
    }

    @Test
    fun sessionTokenStringRepresentationDoesNotContainSecrets() {
        val session = SessionTokens("access-secret", "refresh-secret", 101L)

        assertFalse(session.toString().contains("access-secret"))
        assertFalse(session.toString().contains("refresh-secret"))
    }
}
