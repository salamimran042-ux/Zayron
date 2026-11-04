package ai.zayron.client

/**
 * Backend configuration only. No provider API keys belong in the Android client.
 * Set this to the HTTPS URL of the Zayron backend when that service is deployed.
 */
object AiServiceConfig {
    const val API_BASE_URL: String = ""

    val isConfigured: Boolean
        get() = API_BASE_URL.startsWith("https://")
}
