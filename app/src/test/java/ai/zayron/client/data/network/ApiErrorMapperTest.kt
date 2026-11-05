package ai.zayron.client.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorMapperTest {

    @Test
    fun successHasNoErrorMessage() {
        val result = NetworkResult.Success(data = "ok", statusCode = 200)

        assertNull(ApiErrorMapper.userMessage(result))
    }

    @Test
    fun notConfiguredUsesSafeMessage() {
        assertEquals(
            "Zayron services are not connected yet. Please try again later.",
            ApiErrorMapper.userMessage(NetworkResult.NotConfigured)
        )
    }

    @Test
    fun documentedCreditErrorMapsToApprovedMessage() {
        val result = NetworkResult.HttpError(
            statusCode = 402,
            responseBody = """{"code":"INSUFFICIENT_CREDITS","detail":"internal billing detail"}"""
        )

        assertEquals(
            "You do not have enough credits for this request.",
            ApiErrorMapper.userMessage(result)
        )
    }

    @Test
    fun documentedFileErrorMapsEvenWhenStatusIsGeneric() {
        val result = NetworkResult.HttpError(
            statusCode = 500,
            responseBody = """{"code":"FILE_TOO_LARGE"}"""
        )

        assertEquals(
            "This file is too large. Choose a smaller file and try again.",
            ApiErrorMapper.userMessage(result)
        )
    }

    @Test
    fun malformedServerBodyFallsBackWithoutExposingBody() {
        val privateDetail = "database-password-do-not-display"
        val result = NetworkResult.HttpError(
            statusCode = 500,
            responseBody = "not-json $privateDetail"
        )

        val message = ApiErrorMapper.userMessage(result)

        assertEquals(
            "A server error occurred. Your request was not completed; please try again.",
            message
        )
        assertEquals(false, message?.contains(privateDetail) ?: false)
    }

    @Test
    fun unknownServerCodeDoesNotExposeRawResponse() {
        val privateDetail = "stack-trace-secret"
        val result = NetworkResult.HttpError(
            statusCode = 400,
            responseBody = """{"code":"UNKNOWN_PRIVATE_CODE","debug":"$privateDetail"}"""
        )

        val message = ApiErrorMapper.userMessage(result)

        assertEquals(
            "We could not process that request. Check the details and try again.",
            message
        )
        assertEquals(false, message?.contains(privateDetail) ?: false)
    }

    @Test
    fun rateLimitStatusUsesRetryMessage() {
        val result = NetworkResult.HttpError(
            statusCode = 429,
            responseBody = ""
        )

        assertEquals(
            "You are making requests too quickly. Please wait and try again.",
            ApiErrorMapper.userMessage(result)
        )
    }
}
