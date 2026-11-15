package ai.zayron.client

/**
 * Public HTTPS origin for the Zayron staging API.
 *
 * This URL is not a credential. Provider keys, Supabase secret keys, database
 * passwords and worker tokens must remain in server-side secret storage.
 * Keep the Android client pointed at the FastAPI backend, not directly at
 * privileged database endpoints or AI provider APIs.
 */
object AiServiceConfig {
    const val API_BASE_URL: String = "https://zayron-ai-api-staging.onrender.com"

    val isConfigured: Boolean
        get() = API_BASE_URL.startsWith("https://")
}
