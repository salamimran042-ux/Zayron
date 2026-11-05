package ai.zayron.client.data.auth

import ai.zayron.client.data.chat.ChatAccessTokenProvider

/** Bridges the encrypted session store to authenticated chat transport without exposing tokens to UI. */
class SecureChatAccessTokenProvider(
    private val sessionStore: SecureSessionStore
) : ChatAccessTokenProvider {
    override suspend fun getAccessToken(): String? =
        sessionStore.read()
            ?.takeIf { it.accessExpiresAtEpochSeconds > System.currentTimeMillis() / 1000L }
            ?.accessToken
}
