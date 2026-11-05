package ai.zayron.client.data.network

/**
 * Common result type for requests to the Zayron backend.
 *
 * This keeps transport failures separate from HTTP responses so each feature
 * can present an appropriate message without treating failures as AI output.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(
        val data: T,
        val statusCode: Int
    ) : NetworkResult<T>()

    data class HttpError(
        val statusCode: Int,
        val responseBody: String
    ) : NetworkResult<Nothing>()

    data class StreamError(
        val code: String?
    ) : NetworkResult<Nothing>()

    data object AuthenticationRequired : NetworkResult<Nothing>()

    data object NotConfigured : NetworkResult<Nothing>()

    data object RejectedRequest : NetworkResult<Nothing>()

    data object ConnectionError : NetworkResult<Nothing>()
}
