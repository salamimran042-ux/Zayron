package ai.zayron.client.data.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentPolicyTest {
    @Test
    fun allowsOnlySupportedDocumentAndImageTypes() {
        assertTrue(ChatAttachmentPolicy.isAllowed("application/pdf"))
        assertTrue(ChatAttachmentPolicy.isAllowed("image/jpeg"))
        assertTrue(ChatAttachmentPolicy.isAllowed("text/plain"))
        assertFalse(ChatAttachmentPolicy.isAllowed("application/octet-stream"))
        assertFalse(ChatAttachmentPolicy.isAllowed("application/x-msdownload"))
        assertFalse(ChatAttachmentPolicy.isAllowed(null))
    }

    @Test
    fun enforcesTenMebibyteLimitAndRejectsEmptyFiles() {
        assertTrue(ChatAttachmentPolicy.isAllowedSize(1))
        assertTrue(ChatAttachmentPolicy.isAllowedSize(10L * 1024 * 1024))
        assertFalse(ChatAttachmentPolicy.isAllowedSize(0))
        assertFalse(ChatAttachmentPolicy.isAllowedSize(10L * 1024 * 1024 + 1))
        assertFalse(ChatAttachmentPolicy.isAllowedSize(-1))
    }
}
