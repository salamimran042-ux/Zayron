package ai.zayron.client.ui.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTextSearchTest {

    @Test
    fun emptyOrWhitespaceQueryReturnsNoMatches() {
        assertEquals(DocumentSearchResult(0, emptyList()), findDocumentMatches("Zayron AI", ""))
        assertEquals(DocumentSearchResult(0, emptyList()), findDocumentMatches("Zayron AI", "   "))
    }

    @Test
    fun searchIsCaseInsensitive() {
        val text = "Zayron AI builds useful tools. zAyRoN AI keeps improving."
        val result = findDocumentMatches(text, "ZAYRON ai")

        assertEquals(2, result.count)
        assertTrue(documentMatchSnippet(text, "ZAYRON ai", result.matchStarts[0]).contains("Zayron AI"))
    }

    @Test
    fun countsMultipleNonOverlappingMatches() {
        val result = findDocumentMatches("AI, AI, and AI", "AI")

        assertEquals(3, result.count)
        assertEquals(listOf(0, 4, 12), result.matchStarts)
    }

    @Test
    fun trimsQueryBeforeSearching() {
        val result = findDocumentMatches("Local document search", "  document  ")

        assertEquals(1, result.count)
        assertTrue(result.matchStarts.isNotEmpty())
    }

    @Test
    fun addsEllipsesWhenFirstMatchHasSurroundingContext() {
        val text = "prefix ".repeat(15) + "needle" + " suffix".repeat(15)
        val result = findDocumentMatches(text, "needle")
        val snippet = documentMatchSnippet(text, "needle", result.matchStarts[0])

        assertEquals(1, result.count)
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
        assertTrue(snippet.contains("needle"))
    }

    @Test
    fun snippetReplacesLineBreaksWithSpaces() {
        val text = "first line\nneedle\nlast line"
        val result = findDocumentMatches(text, "needle")
        val snippet = documentMatchSnippet(text, "needle", result.matchStarts[0])

        assertEquals(1, result.count)
        assertFalse(snippet.contains('\n'))
        assertTrue(snippet.contains("first line needle last line"))
    }

    @Test
    fun storesEveryMatchOffsetForPreviousAndNextNavigation() {
        val text = "one hit, then another hit, then a final hit"
        val result = findDocumentMatches(text, "hit")

        assertEquals(3, result.count)
        assertEquals(3, result.matchStarts.size)
        assertTrue(documentMatchSnippet(text, "hit", result.matchStarts[1]).contains("another hit"))
        assertTrue(documentMatchSnippet(text, "hit", result.matchStarts[2]).contains("final hit"))
    }

    @Test
    fun invalidMatchOffsetReturnsEmptySnippet() {
        assertEquals("", documentMatchSnippet("sample text", "sample", -1))
        assertEquals("", documentMatchSnippet("sample text", "sample", 99))
    }
}
