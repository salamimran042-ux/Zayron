package ai.zayron.client.data.auth

import ai.zayron.client.data.chat.ChatAccessTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bridges the encrypted session store to authenticated chat transport without exposing tokens to UI. */
class SecureChatAccessTokenProvider(
    private val sessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) : ChatAccessTokenProvider {
    override suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        sessionStore.read()
            ?.takeIf { it.accessExpiresAtEpochSeconds > nowEpochSeconds() }
            ?.accessToken
    }
}
