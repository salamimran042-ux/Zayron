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
 * Parses only an explicitly configured HTTPS backend URL.
 * Kept as a pure helper so URL policy can be verified without a live service.
 */
internal fun resolveBackendBaseUrl(configuredUrl: String): HttpUrl? {
    val trimmed = configuredUrl.trim()
    if (trimmed.isEmpty()) return null
    val normalized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    return normalized.toHttpUrlOrNull()?.takeIf { it.isHttps }
}

/**
 * Resolves a relative path beneath the configured backend base path.
 * Absolute URLs, traversal segments, query strings and fragments are rejected.
 */
internal fun resolveApiUrl(baseUrl: HttpUrl?, relativePath: String): HttpUrl? {
    baseUrl ?: return null
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
        get() = resolveBackendBaseUrl(AiServiceConfig.API_BASE_URL)

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

    /** Resolves an internal relative API path beneath the configured HTTPS backend. */
    fun urlFor(relativePath: String): HttpUrl? =
        resolveApiUrl(backendBaseUrl, relativePath)

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
