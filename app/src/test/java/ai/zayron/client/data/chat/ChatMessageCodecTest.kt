package ai.zayron.client.data.chat

import ai.zayron.client.ui.chat.ChatMessage
import ai.zayron.client.ui.chat.ChatAttachmentReference
import ai.zayron.client.ui.chat.ChatRole
import ai.zayron.client.ui.chat.DeliveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageCodecTest {

    @Test
    fun roundTripPreservesMessageRoleAndDeliveryState() {
        val messages = listOf(
            ChatMessage("user-1", "Hello Zayron", ChatRole.USER, DeliveryState.LOCAL_ONLY),
            ChatMessage("assistant-1", "Hello! How can I help?", ChatRole.ASSISTANT, DeliveryState.SENT)
        )

        assertEquals(messages, ChatMessageCodec.decode(ChatMessageCodec.encode(messages)))
    }

    @Test
    fun roundTripPreservesPrivateAttachmentReferences() {
        val attachment = ChatAttachmentReference(
            "attachment-1", "brief.pdf", "application/pdf", 2048L,
            "owner-folder/private-object.pdf"
        )
        val message = ChatMessage(
            "user-with-file", "Please review this.", ChatRole.USER,
            DeliveryState.SENT, listOf(attachment)
        )
        assertEquals(listOf(message), ChatMessageCodec.decode(ChatMessageCodec.encode(listOf(message))))
    }

    @Test
    fun legacyMessagesWithoutDeliveryStateRemainReadable() {
        val decoded = ChatMessageCodec.decode(
            """[{"id":"old-1","text":"Earlier message","role":"USER"}]"""
        )

        assertEquals(1, decoded.size)
        assertEquals(ChatRole.USER, decoded.single().role)
        assertEquals(DeliveryState.LOCAL_ONLY, decoded.single().deliveryState)
    }

    @Test
    fun unknownRoleAndDeliveryStateFallBackSafely() {
        val decoded = ChatMessageCodec.decode(
            """[{"id":"future-1","text":"Compatible message","role":"NEW_ROLE","deliveryState":"NEW_STATE"}]"""
        )

        assertEquals(ChatRole.USER, decoded.single().role)
        assertEquals(DeliveryState.LOCAL_ONLY, decoded.single().deliveryState)
    }

    @Test
    fun malformedHistoryReturnsEmptyList() {
        assertTrue(ChatMessageCodec.decode("not-json").isEmpty())
    }

    @Test
    fun blankMessagesAreSkippedWithoutLosingValidMessages() {
        val decoded = ChatMessageCodec.decode(
            """[{"id":"blank","text":"  ","role":"USER"},{"id":"valid","text":"Keep me","role":"USER"}]"""
        )

        assertEquals(1, decoded.size)
        assertEquals("Keep me", decoded.single().text)
    }
}
