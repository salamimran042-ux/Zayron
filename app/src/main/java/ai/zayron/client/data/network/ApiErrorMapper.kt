package ai.zayron.client.data.network

import org.json.JSONObject

/**
 * Converts transport outcomes into safe, user-facing messages.
 * Server response bodies are parsed only for documented machine-readable codes.
 */
object ApiErrorMapper {

    fun userMessage(result: NetworkResult<*>): String? = when (result) {
        is NetworkResult.Success<*> -> null
        NetworkResult.AuthenticationRequired ->
            "Please sign in to use Zayron AI services."
        is NetworkResult.StreamError ->
            "Zayron could not complete the response. Please try again."
        NetworkResult.NotConfigured ->
            "Zayron services are not connected yet. Please try again later."
        NetworkResult.RejectedRequest ->
            "This request could not be sent securely. Please try again."
        NetworkResult.ConnectionError ->
            "We could not reach Zayron. Check your internet connection and try again."
        is NetworkResult.HttpError -> messageFor(
            statusCode = result.statusCode,
            errorCode = readErrorCode(result.responseBody)
        )
    }

    private fun readErrorCode(responseBody: String): String? {
        if (responseBody.isBlank()) return null
        return try {
            JSONObject(responseBody)
                .optString("code")
                .trim()
                .uppercase()
                .takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun messageFor(statusCode: Int, errorCode: String?): String {
        val documentedMessage = when (errorCode) {
            "AUTH_REQUIRED", "INVALID_CREDENTIALS", "TOKEN_EXPIRED" ->
                "Please sign in again to continue."
            "INSUFFICIENT_CREDITS" ->
                "You do not have enough credits for this request."
            "FILE_TOO_LARGE" ->
                "This file is too large. Choose a smaller file and try again."
            "UNSUPPORTED_MEDIA" ->
                "This file type is not supported."
            "RATE_LIMITED" ->
                "You are making requests too quickly. Please wait a moment and try again."
            "SERVICE_UNAVAILABLE" ->
                "This Zayron service is temporarily unavailable. Please try again later."
            else -> null
        }

        if (documentedMessage != null) return documentedMessage

        return when (statusCode) {
            400, 422 -> "We could not process that request. Check the details and try again."
            401, 403 -> "You are not authorized to perform this action. Please sign in again."
            404 -> "The requested Zayron service could not be found."
            408, 504 -> "The request took too long. Please try again."
            429 -> "You are making requests too quickly. Please wait and try again."
            in 500..599 -> "A server error occurred. Your request was not completed; please try again."
            else -> "The request could not be completed. Please try again."
        }
    }
}
