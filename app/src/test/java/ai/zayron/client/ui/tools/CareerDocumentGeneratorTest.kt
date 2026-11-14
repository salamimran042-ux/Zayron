package ai.zayron.client.ui.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareerDocumentGeneratorTest {
    @Test
    fun resumeIncludesOnlyFactsEnteredByTheUser() {
        val result = CareerDocumentGenerator.buildResume(
            CareerProfile(fullName = "Ada Example", targetRole = "Junior Analyst", skills = "Excel, research", education = "BSc Economics")
        )
        assertTrue(result.contains("ADA EXAMPLE"))
        assertTrue(result.contains("Junior Analyst"))
        assertTrue(result.contains("Excel, research"))
        assertTrue(result.contains("BSc Economics"))
        assertFalse(result.contains("five years"))
        assertFalse(result.contains("award-winning"))
    }

    @Test
    fun emptyResumeDoesNotInventQualifications() {
        val result = CareerDocumentGenerator.buildResume(CareerProfile())
        assertTrue(result.isEmpty())
        assertFalse(result.contains("experience", ignoreCase = true))
    }

    @Test
    fun coverLetterUsesSuppliedRoleCompanyAndExperience() {
        val result = CareerDocumentGenerator.buildCoverLetter(
            CareerProfile(fullName = "Ada Example", targetRole = "Support Specialist", experience = "Handled customer enquiries for a student association"),
            company = "North Star Ltd"
        )
        assertTrue(result.contains("Support Specialist"))
        assertTrue(result.contains("North Star Ltd"))
        assertTrue(result.contains("Handled customer enquiries for a student association"))
        assertTrue(result.contains("Ada Example"))
    }

    @Test
    fun coverLetterWorksWithoutInventingMissingPersonalDetails() {
        val result = CareerDocumentGenerator.buildCoverLetter(CareerProfile(), "")
        assertTrue(result.contains("the advertised position"))
        assertTrue(result.contains("Dear Hiring Manager"))
        assertFalse(result.contains("at null"))
        assertFalse(result.contains("my degree"))
    }
}
