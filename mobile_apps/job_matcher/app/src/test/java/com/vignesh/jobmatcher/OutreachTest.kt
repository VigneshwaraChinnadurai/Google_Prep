package com.vignesh.jobmatcher

import com.vignesh.jobmatcher.claude.ClaudeResponseParser
import com.vignesh.jobmatcher.claude.PromptBuilder
import com.vignesh.jobmatcher.data.JsonCodec
import com.vignesh.jobmatcher.model.Contact
import com.vignesh.jobmatcher.model.ContactType
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.SourceType
import com.vignesh.jobmatcher.ui.personalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutreachTest {
    private val job = Job(
        id = "EIGHTFOLD:microsoft:200054398", companyId = "microsoft", companyName = "Microsoft", title = "Principal Applied Scientist",
        location = "India, Karnataka, Bangalore", url = "https://apply.careers.microsoft.com/careers/job/1970393556991771",
        description = "d", source = SourceType.EIGHTFOLD
    )

    @Test
    fun contactsPrompt_demandsRealProfilesOnly() {
        val p = PromptBuilder.findContactsPrompt(job, listOf("bengaluru", "hyderabad"))
        assertTrue(p.contains("Never guess, construct or shorten a URL"))
        assertTrue(p.contains("No personal email addresses or phone numbers"))
        assertTrue(p.contains(job.url))
    }

    @Test
    fun contactsReply_keepsOnlyLinkedInProfiles() {
        val reply = """<contacts>[
            {"name":"Priya Sharma","title":"Senior Technical Recruiter","type":"recruiter","profile_url":"https://www.linkedin.com/in/priya-sharma-ta/","why":"Recruits for AI in Bengaluru"},
            {"name":"Rahul Mehta","title":"Principal Applied Scientist","type":"employee","profile_url":"in.linkedin.com/in/rahulmehta","why":"Same org"},
            {"name":"No Url","title":"Recruiter","type":"recruiter","profile_url":"","why":"x"},
            {"name":"Company Page","title":"","type":"recruiter","profile_url":"https://www.linkedin.com/company/microsoft","why":"x"}
        ]</contacts>"""
        val contacts = ClaudeResponseParser.parseContacts(reply).getOrThrow()
        assertEquals(listOf("Priya Sharma", "Rahul Mehta"), contacts.map { it.name })
        assertEquals("https://www.linkedin.com/in/priya-sharma-ta", contacts[0].profileUrl)
        assertEquals("https://in.linkedin.com/in/rahulmehta", contacts[1].profileUrl)
        assertEquals(ContactType.EMPLOYEE, contacts[1].type)
    }

    @Test
    fun personalize_usesFirstName_andSurvivesStorage() {
        val c = Contact(name = "Dr. Anita Rao", profileUrl = "https://www.linkedin.com/in/anita", type = ContactType.HIRING_MANAGER, contactedAt = 5L)
        assertEquals("Dear Anita,", personalize("Dear {Name},", c))
        assertEquals("Dear Hiring Team,", personalize("Dear {Name},", null))

        val back = JsonCodec.jobFromJson(JsonCodec.jobToJson(job.copy(contacts = listOf(c))))
        assertEquals(listOf(c), back.contacts)
    }
}
