package ai.zayron.client.data.network

import ai.zayron.client.AiServiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client for the future Zayron backend.
 *
 * No provider credentials, token values, or body-logging interceptor belong here.
 * Requests are allowed only to the explicitly configured HTTPS backend.
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

            val normalizedUrl = if (configuredUrl.endsWith("/")) configuredUrl else "$configuredUrl/"
            return normalizedUrl.toHttpUrlOrNull()?.takeIf { it.isHttps }
        }

    val isConfigured: Boolean
        get() = backendBaseUrl != null

    /** One reusable client with bounded timeouts and no sensitive request logging. */
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
     * Resolves an internal relative API path. Absolute URLs, traversal segments,
     * query strings and fragments are rejected; query parameters should be added
     * explicitly by the endpoint implementation.
     */
    fun urlFor(relativePath: String): HttpUrl? {
        val baseUrl = backendBaseUrl ?: return null
        val path = relativePath.trim().trimStart('/')

        if (path.isEmpty() || "://" in path || path.startsWith("//") ||
            '?' in path || '#' in path || '\\' in path ||
            path.split('/').any { it == "." || it == ".." }
        ) {
            return null
        }

        return baseUrl.newBuilder()
            .addPathSegments(path)
            .build()
    }

    /**
     * Executes a request off the main thread and returns a typed result.
     * The request URL must remain under the configured HTTPS backend path.
     * Authentication is intentionally not added until the backend session
     * contract and secure token storage are implemented.
     */
    suspend fun execute(request: Request): NetworkResult<String> = withContext(Dispatchers.IO) {
        val baseUrl = backendBaseUrl
            ?: return@withContext NetworkResult.NotConfigured

        val target = request.url
        val isAllowedTarget = target.isHttps &&
            target.host == baseUrl.host &&
            target.port == baseUrl.port &&
            target.encodedPath.startsWith(baseUrl.encodedPath)

        if (!isAllowedTarget) {
            return@withContext NetworkResult.RejectedRequest
        }

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    NetworkResult.Success(responseText, response.code)
                } else {
                    NetworkResult.HttpError(response.code, responseText)
                }
            }
        } catch (_: IOException) {
            NetworkResult.ConnectionError
        }
    }
}
