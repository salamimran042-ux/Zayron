package ai.zayron.client.data.network

import ai.zayron.client.AiServiceConfig
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client for the future Zayron backend.
 *
 * This class deliberately contains no provider credentials, token values, or
 * logging interceptor. Requests must only target an explicitly configured
 * HTTPS backend. An empty or invalid base URL keeps networking unavailable.
 */
object ApiClient {
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 60L
    private const val CALL_TIMEOUT_SECONDS = 90L

    private val backendBaseUrl: HttpUrl?
        get() {
            val configuredUrl = AiServiceConfig.API_BASE_URL.trim()
            if (configuredUrl.isEmpty()) return null

            val normalizedUrl = if (configuredUrl.endsWith("/")) {
                configuredUrl
            } else {
                "$configuredUrl/"
            }

            return normalizedUrl.toHttpUrlOrNull()?.takeIf { it.isHttps }
        }

    val isConfigured: Boolean
        get() = backendBaseUrl != null

    /**
     * A single reusable client with bounded timeouts.
     * Authentication will be added only after the backend session contract exists.
     */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Resolves an internal relative API path. Absolute URLs are rejected so
     * callers cannot accidentally send authenticated future requests elsewhere.
     */
    fun urlFor(relativePath: String): HttpUrl? {
        val baseUrl = backendBaseUrl ?: return null
        val path = relativePath.trim().trimStart('/')

        if (path.isEmpty() || "://" in path || path.startsWith("//")) {
            return null
        }

        return baseUrl.newBuilder()
            .addPathSegments(path)
            .build()
    }
}
