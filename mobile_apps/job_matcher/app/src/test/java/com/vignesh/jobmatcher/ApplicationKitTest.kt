package com.vignesh.jobmatcher

import com.vignesh.jobmatcher.claude.ClaudeResponseParser
import com.vignesh.jobmatcher.claude.PromptBuilder
import com.vignesh.jobmatcher.data.ContactInfo
import com.vignesh.jobmatcher.export.CoverLetterDocument
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

class ApplicationKitTest {

    private val resume = File("src/main/assets/default_resume.txt").readText()
    private val job = Job(
        id = "WORKDAY:nvidia:JR2025036", companyId = "nvidia", companyName = "NVIDIA", title = "Senior ML Engineer, GenAI",
        location = "India, Bengaluru; India, Hyderabad",
        url = "https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite/job/India-Bengaluru/Senior-ML-Engineer_JR2025036",
        description = "Build LLM systems.", source = SourceType.WORKDAY
    )

    @Test
    fun contactInfo_parsedFromBundledResume() {
        val c = ContactInfo.fromResume(resume)
        assertEquals("Vigneshwara Chinnadurai", c.name)
        assertEquals("rockingstarvic@gmail.com", c.email)
        assertEquals("986-555-0901", c.phone)
        assertEquals("linkedin.com/in/vigneshwarac", c.linkedin)
        assertEquals("Vigneshwara", c.firstName)
    }

    @Test
    fun coverLetter_layoutAndDocx() {
        val doc = CoverLetterDocument(
            ContactInfo.fromResume(resume), job,
            "Dear Hiring Team at NVIDIA,\nI build R&D <agents> & pipelines.\nSincerely,\nVigneshwara Chinnadurai"
        )
        val text = doc.toText()
        assertTrue(text.startsWith("Vigneshwara Chinnadurai\nrockingstarvic@gmail.com"))
        assertTrue(text.contains("Re: Application for Senior ML Engineer, GenAI (Job ID JR2025036)"))
        assertTrue(text.contains("India, Bengaluru\n")) // first location only
        assertEquals("Cover_Letter_Vigneshwara_Chinnadurai_NVIDIA_Senior_ML_Engineer_GenAI.docx", doc.fileName(CoverLetterDocument.Format.DOCX))

        // A valid package: the three parts Word needs, with XML-escaped text.
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(doc.toDocx())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e -> entries[e.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml"), entries.keys)
        val xml = entries.getValue("word/document.xml")
        assertTrue(xml.contains("R&amp;D &lt;agents&gt; &amp; pipelines"))
        assertTrue(xml.contains("<w:b/>")) // name / subject in bold
    }

    @Test
    fun tailoringPrompt_carriesGapRulesAndSignature() {
        val prompt = PromptBuilder.tailoringPrompt(resume, job, ContactInfo.fromResume(resume))
        assertTrue(prompt.contains("realistic timeline"))
        assertTrue(prompt.contains("it will take some"))
        assertTrue(prompt.contains("Sincerely,\nVigneshwara Chinnadurai"))
        assertTrue(prompt.contains("Dear {Name},"))
        assertTrue(prompt.contains(job.url))
    }

    @Test
    fun tailoringReply_parsesAllSixSections() {
        val reply = """<match_summary>Fit</match_summary><resume_bullets>• a</resume_bullets>
            <cover_letter>Dear Hiring Team at NVIDIA,</cover_letter><gap_plan>Gap: CUDA</gap_plan>
            <connection_note>Dear {Name}, I applied for…</connection_note><referral_message>Dear {Name},</referral_message>"""
        val t = ClaudeResponseParser.parseTailoring(reply).getOrThrow()
        assertEquals("Dear {Name}, I applied for…", t.connectionNote)
        assertEquals("Gap: CUDA", t.gapPlan)
    }
}
