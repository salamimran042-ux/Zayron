package ai.zayron.client.ui.tools

internal data class DocumentSearchResult(
    val count: Int,
    val matchStarts: List<Int>
)

/**
 * Searches the locally extracted preview without uploading document content.
 * Counts non-overlapping matches and retains their offsets for result navigation.
 */
internal fun findDocumentMatches(text: String, query: String): DocumentSearchResult {
    val term = query.trim()
    if (term.isEmpty()) return DocumentSearchResult(0, emptyList())

    val starts = mutableListOf<Int>()
    var fromIndex = 0

    while (fromIndex < text.length) {
        val match = text.indexOf(term, fromIndex, ignoreCase = true)
        if (match < 0) break
        starts += match
        fromIndex = match + term.length.coerceAtLeast(1)
    }

    return DocumentSearchResult(starts.size, starts)
}

/** Returns a bounded, single-line context snippet for the selected search result. */
internal fun documentMatchSnippet(text: String, query: String, matchStart: Int): String {
    val term = query.trim()
    if (term.isEmpty() || matchStart < 0 || matchStart + term.length > text.length) return ""

    val start = (matchStart - 70).coerceAtLeast(0)
    val end = (matchStart + term.length + 90).coerceAtMost(text.length)
    val snippet = text.substring(start, end).replace('\\n', ' ')
    return (if (start > 0) "…" else "") +
        snippet +
        (if (end < text.length) "…" else "")
}
