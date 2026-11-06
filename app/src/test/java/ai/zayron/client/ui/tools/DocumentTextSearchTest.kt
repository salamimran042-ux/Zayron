package ai.zayron.client.ui.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTextSearchTest {

    @Test
    fun emptyOrWhitespaceQueryReturnsNoMatches() {
        assertEquals(DocumentSearchResult(0, ""), findDocumentMatches("Zayron AI", ""))
        assertEquals(DocumentSearchResult(0, ""), findDocumentMatches("Zayron AI", "   "))
    }

    @Test
    fun searchIsCaseInsensitive() {
        val result = findDocumentMatches("Zayron AI builds useful tools. zAyRoN AI keeps improving.", "ZAYRON ai")

        assertEquals(2, result.count)
        assertTrue(result.firstSnippet.contains("Zayron AI"))
    }

    @Test
    fun countsMultipleNonOverlappingMatches() {
        val result = findDocumentMatches("AI, AI, and AI", "AI")

        assertEquals(3, result.count)
        assertTrue(result.firstSnippet.startsWith("AI"))
    }

    @Test
    fun trimsQueryBeforeSearching() {
        val result = findDocumentMatches("Local document search", "  document  ")

        assertEquals(1, result.count)
        assertTrue(result.firstSnippet.contains("document"))
    }

    @Test
    fun addsEllipsesWhenFirstMatchHasSurroundingContext() {
        val text = "prefix ".repeat(15) + "needle" + " suffix".repeat(15)
        val result = findDocumentMatches(text, "needle")

        assertEquals(1, result.count)
        assertTrue(result.firstSnippet.startsWith("…"))
        assertTrue(result.firstSnippet.endsWith("…"))
        assertTrue(result.firstSnippet.contains("needle"))
    }

    @Test
    fun snippetReplacesLineBreaksWithSpaces() {
        val result = findDocumentMatches("first line\nneedle\nlast line", "needle")

        assertEquals(1, result.count)
        assertFalse(result.firstSnippet.contains('\n'))
        assertTrue(result.firstSnippet.contains("first line needle last line"))
    }
}
