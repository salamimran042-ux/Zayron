package ai.zayron.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiServiceConfigTest {

    @Test
    fun deployedStagingBackendIsConfiguredOverHttps() {
        assertEquals(
            "https://zayron-ai-api-staging.onrender.com",
            AiServiceConfig.API_BASE_URL
        )
        assertTrue(AiServiceConfig.isConfigured)
        assertTrue(AiServiceConfig.API_BASE_URL.startsWith("https://"))
    }
}
