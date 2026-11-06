package ai.zayron.client.ui.tools

internal data class DocumentSearchResult(
    val count: Int,
    val firstSnippet: String
)

/**
 * Searches the locally extracted preview without uploading document content.
 * Counts non-overlapping matches and returns context around the first match.
 */
internal fun findDocumentMatches(text: String, query: String): DocumentSearchResult {
    val term = query.trim()
    if (term.isEmpty()) return DocumentSearchResult(0, "")

    var fromIndex = 0
    var count = 0
    var firstSnippet = ""

    while (fromIndex < text.length) {
        val match = text.indexOf(term, fromIndex, ignoreCase = true)
        if (match < 0) break

        count++
        if (count == 1) {
            val start = (match - 70).coerceAtLeast(0)
            val end = (match + term.length + 90).coerceAtMost(text.length)
            val snippet = text.substring(start, end).replace('\n', ' ')
            firstSnippet = (if (start > 0) "…" else "") +
                snippet +
                (if (end < text.length) "…" else "")
        }
        fromIndex = match + term.length.coerceAtLeast(1)
    }

    return DocumentSearchResult(count, firstSnippet)
}
