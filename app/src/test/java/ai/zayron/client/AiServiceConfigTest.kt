package ai.zayron.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiServiceConfigTest {

    @Test
    fun backendIsNotReportedAsConfiguredBeforeDeployment() {
        assertTrue(AiServiceConfig.API_BASE_URL.isBlank())
        assertFalse(AiServiceConfig.isConfigured)
    }
}
