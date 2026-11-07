package ai.zayron.client.data.chat

/**
 * Incrementally decodes Server-Sent Events from arbitrary network chunks.
 *
 * Handles an optional UTF-8 BOM, LF, CRLF and CR line endings, multi-line data
 * fields, comments, multiple frames per chunk and a final unterminated frame
 * at end-of-stream. Call [append] with endOfInput=true when the response body
 * reaches EOF.
 */
class SseFrameDecoder {
    private val pendingLine = StringBuilder()
    private val dataLines = mutableListOf<String>()
    private var eventName: String? = null
    private var hasData = false
    private var skipNextLf = false
    private var atStreamStart = true

    fun append(chunk: String, endOfInput: Boolean = false): List<ChatStreamEvent> {
        val events = mutableListOf<ChatStreamEvent>()

        chunk.forEach { character ->
            // The SSE format permits one UTF-8 BOM at the beginning of a stream.
            // Ignore it even when it arrives in a chunk by itself.
            if (atStreamStart) {
                atStreamStart = false
                if (character == '\uFEFF') return@forEach
            }

            if (skipNextLf) {
                skipNextLf = false
                if (character == '\n') return@forEach
            }

            when (character) {
                '\r' -> {
                    processLine(events)
                    skipNextLf = true
                }
                '\n' -> processLine(events)
                else -> pendingLine.append(character)
            }
        }

        if (endOfInput) {
            if (pendingLine.isNotEmpty()) processLine(events)
            dispatchFrame(events)
            skipNextLf = false
        }

        return events
    }

    private fun processLine(events: MutableList<ChatStreamEvent>) {
        val line = pendingLine.toString()
        pendingLine.setLength(0)

        if (line.isEmpty()) {
            dispatchFrame(events)
            return
        }

        // A colon at the beginning marks a comment / heartbeat.
        if (line.startsWith(':')) return

        val separator = line.indexOf(':')
        val field = if (separator < 0) line else line.substring(0, separator)
        var value = if (separator < 0) "" else line.substring(separator + 1)
        if (value.startsWith(' ')) value = value.substring(1)

        when (field) {
            "data" -> {
                hasData = true
                dataLines.add(value)
            }
            "event" -> eventName = value
            // id and retry are intentionally ignored by this stateless decoder.
        }
    }

    private fun dispatchFrame(events: MutableList<ChatStreamEvent>) {
        if (hasData) {
            ChatStreamEventParser.parse(eventName, dataLines.joinToString("\n"))?.let(events::add)
        }
        dataLines.clear()
        eventName = null
        hasData = false
    }
}
