package com.vignesh.jobmatcher.data

/**
 * Your name and contact details, read from the resume text so cover letters and outreach
 * messages are signed correctly without storing them separately.
 */
data class ContactInfo(
    val name: String,
    val email: String = "",
    val phone: String = "",
    val linkedin: String = "",
    val github: String = ""
) {
    /** "email | phone | linkedin" -- the contact line under your name. */
    val contactLine: String get() = listOf(email, phone, linkedin).filter { it.isNotBlank() }.joinToString(" | ")

    val firstName: String get() = name.substringBefore(' ')

    companion object {
        private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
        private val PHONE = Regex("""\+?\d[\d\s-]{8,14}\d""")
        private val LINKEDIN = Regex("""(?:https?://)?(?:[a-z]{2,3}\.)?linkedin\.com/in/[A-Za-z0-9_-]+/?""", RegexOption.IGNORE_CASE)
        private val GITHUB = Regex("""(?:https?://)?github\.com/[A-Za-z0-9_-]+/?""", RegexOption.IGNORE_CASE)

        fun fromResume(resume: String): ContactInfo {
            // The name is the first non-empty line that isn't itself contact details.
            val name = resume.lineSequence().map { it.trim() }
                .firstOrNull { it.isNotBlank() && !EMAIL.containsMatchIn(it) && !it.contains("linkedin", true) && it.length <= 60 }
                .orEmpty()
            val head = resume.take(1500) // contact details live in the header
            return ContactInfo(
                name = name,
                email = EMAIL.find(head)?.value.orEmpty(),
                phone = PHONE.find(head)?.value?.trim().orEmpty(),
                linkedin = LINKEDIN.find(head)?.value?.trimEnd('/').orEmpty(),
                github = GITHUB.find(head)?.value?.trimEnd('/').orEmpty()
            )
        }
    }
}
