package ai.zayron.client.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApiUrlPolicyTest {

    @Test
    fun blankOrNonHttpsBackendIsNotAccepted() {
        assertNull(resolveBackendBaseUrl(""))
        assertNull(resolveBackendBaseUrl("   "))
        assertNull(resolveBackendBaseUrl("http://api.example.com"))
    }

    @Test
    fun httpsBackendBaseUrlIsNormalized() {
        assertEquals(
            "https://api.example.com/zayron/",
            resolveBackendBaseUrl(" https://api.example.com/zayron ")?.toString()
        )
    }

    @Test
    fun relativePathResolvesInsideConfiguredBasePath() {
        val base = "https://api.example.com/zayron/".toHttpUrl()

        assertEquals(
            "https://api.example.com/zayron/api/v1/chat/stream",
            resolveApiUrl(base, "/api/v1/chat/stream")?.toString()
        )
    }

    @Test
    fun unsafeOrAmbiguousPathsAreRejected() {
        val base = "https://api.example.com/zayron/".toHttpUrl()
        val rejected = listOf(
            "",
            "   ",
            "../admin",
            "api/../admin",
            "./admin",
            "https://attacker.example/path",
            "//attacker.example/path",
            "///attacker.example/path",
            "%2e%2e/admin",
            "api/%2E%2E/admin",
            "api/chat?debug=true",
            "api/chat#fragment",
            "api\\\\chat"
        )

        rejected.forEach { path ->
            assertNull("Expected rejection for path: $path", resolveApiUrl(base, path))
        }
    }

    @Test
    fun missingBackendCannotResolveApiPath() {
        assertNull(resolveApiUrl(null, "api/v1/chat/stream"))
    }

    @Test
    fun validNestedPathProducesHttpsUrl() {
        val result = resolveApiUrl(
            "https://api.example.com/v2/".toHttpUrl(),
            "files/upload"
        )
        assertNotNull(result)
        assertEquals(true, result!!.isHttps)
        assertEquals("api.example.com", result.host)
        assertEquals("/v2/files/upload", result.encodedPath)
    }
}
