package ai.zayron.client.ui.tools

data class CareerProfile(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val location: String = "",
    val targetRole: String = "",
    val summary: String = "",
    val skills: String = "",
    val experience: String = "",
    val education: String = ""
)

/** Builds editable, offline drafts without inventing qualifications or work history. */
object CareerDocumentGenerator {
    fun buildResume(profile: CareerProfile): String = buildString {
        val name = clean(profile.fullName)
        val role = clean(profile.targetRole)
        val contact = listOf(profile.email, profile.phone, profile.location)
            .map(::clean).filter(String::isNotEmpty).joinToString(" | ")
        if (name.isNotEmpty()) appendLine(name.uppercase())
        if (role.isNotEmpty()) appendLine(role)
        if (contact.isNotEmpty()) appendLine(contact)
        if (isNotEmpty()) appendLine()
        section("PROFESSIONAL SUMMARY", profile.summary)
        section("KEY SKILLS", profile.skills)
        section("WORK EXPERIENCE", profile.experience)
        section("EDUCATION", profile.education)
    }.trim()

    fun buildCoverLetter(profile: CareerProfile, company: String, jobDescription: String = ""): String = buildString {
        val name = clean(profile.fullName)
        val contact = listOf(profile.email, profile.phone, profile.location)
            .map(::clean).filter(String::isNotEmpty).joinToString(" | ")
        val role = clean(profile.targetRole).ifEmpty { "the advertised position" }
        val organisation = clean(company)
        if (name.isNotEmpty()) appendLine(name)
        if (contact.isNotEmpty()) appendLine(contact)
        if (name.isNotEmpty() || contact.isNotEmpty()) appendLine()
        appendLine("Dear Hiring Manager,")
        appendLine()
        append("I am writing to apply for ")
        append(role)
        if (organisation.isNotEmpty()) append(" at $organisation")
        appendLine(".")
        appendLine()
        clean(profile.summary).takeIf(String::isNotEmpty)?.let { appendLine(it); appendLine() }
        clean(profile.skills).takeIf(String::isNotEmpty)?.let { appendLine("Relevant skills: $it."); appendLine() }
        clean(profile.experience).takeIf(String::isNotEmpty)?.let { appendLine("Relevant experience and achievements: $it."); appendLine() }
        clean(profile.education).takeIf(String::isNotEmpty)?.let { appendLine("Education and qualifications: $it."); appendLine() }
        clean(jobDescription).takeIf(String::isNotEmpty)?.let {
            appendLine("I understand that the role involves: $it")
            appendLine()
        }
        appendLine("Thank you for considering my application. I would welcome the opportunity to discuss how my background and skills may contribute to your team.")
        appendLine()
        appendLine("Yours sincerely,")
        if (name.isNotEmpty()) appendLine(name)
    }.trim()

    private fun StringBuilder.section(title: String, value: String) {
        val content = clean(value)
        if (content.isNotEmpty()) {
            if (isNotEmpty()) appendLine()
            appendLine(title)
            appendLine(content)
        }
    }

    private fun clean(value: String): String = value.trim()
}
