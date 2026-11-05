package ai.zayron.client.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseFrameDecoderTest {
    @Test
    fun decodesEventSplitAcrossArbitraryChunks() {
        val decoder = SseFrameDecoder()
        assertTrue(decoder.append("event: message.delta\ndata: {\"delta\":").isEmpty())
        assertEquals(
            listOf(ChatStreamEvent.Delta("Hello")),
            decoder.append("\"Hello\"}\n\n")
        )
    }

    @Test
    fun joinsMultipleDataLinesIntoOnePayload() {
        val decoder = SseFrameDecoder()
        val events = decoder.append(
            "event: message.delta\ndata: {\"delta\":\ndata: \"Hello\"}\n\n"
        )
        assertEquals(listOf(ChatStreamEvent.Delta("Hello")), events)
    }

    @Test
    fun acceptsCrLfAndCrLineEndings() {
        val decoder = SseFrameDecoder()
        val events = decoder.append(
            "event: message.delta\r\ndata: {\"delta\":\"A\"}\r\r" +
                "event: message.delta\rdata: {\"delta\":\"B\"}\r\r"
        )
        assertEquals(
            listOf(ChatStreamEvent.Delta("A"), ChatStreamEvent.Delta("B")),
            events
        )
    }

    @Test
    fun ignoresCommentsAndUnknownFields() {
        val decoder = SseFrameDecoder()
        val events = decoder.append(
            ": heartbeat\nretry: 5000\nid: 12\nevent: message.delta\n" +
                "data: {\"delta\":\"Still here\"}\n\n"
        )
        assertEquals(listOf(ChatStreamEvent.Delta("Still here")), events)
    }

    @Test
    fun decodesSeveralFramesFromOneChunk() {
        val decoder = SseFrameDecoder()
        val events = decoder.append(
            "event: message.delta\ndata: {\"delta\":\"One\"}\n\n" +
                "event: message.completed\ndata: {\"messageId\":\"m1\"}\n\n"
        )
        assertEquals(
            listOf(ChatStreamEvent.Delta("One"), ChatStreamEvent.Completed("m1")),
            events
        )
    }

    @Test
    fun flushesFinalFrameAtEndOfInputWithoutTrailingBlankLine() {
        val decoder = SseFrameDecoder()
        assertTrue(decoder.append("event: message.delta\ndata: {\"delta\":\"Final\"}").isEmpty())
        assertEquals(
            listOf(ChatStreamEvent.Delta("Final")),
            decoder.append("", endOfInput = true)
        )
    }

    @Test
    fun ignoresMalformedAndDataLessFrames() {
        val decoder = SseFrameDecoder()
        val events = decoder.append(
            "event: message.delta\n\n" +
                "event: message.delta\ndata: not-json\n\n"
        )
        assertTrue(events.isEmpty())
    }
}
