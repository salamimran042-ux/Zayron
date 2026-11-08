package ai.zayron.client

/**
 * Backend configuration only. No provider API keys belong in the Android client.
 *
 * Keep this empty until the FastAPI Zayron backend is deployed to a verified
 * HTTPS origin. A Supabase Edge Functions URL is not interchangeable with the
 * FastAPI service base URL and must not make the client appear connected.
 */
object AiServiceConfig {
    const val API_BASE_URL: String = ""

    val isConfigured: Boolean
        get() = API_BASE_URL.isNotBlank() && API_BASE_URL.startsWith("https://")
}
