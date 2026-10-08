package com.vignesh.jobmatcher

import com.vignesh.jobmatcher.data.JsonCodec
import com.vignesh.jobmatcher.model.EventType
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobEvent
import com.vignesh.jobmatcher.model.JobStatus
import com.vignesh.jobmatcher.model.SourceType
import com.vignesh.jobmatcher.model.StatusChange
import com.vignesh.jobmatcher.model.Tailoring
import com.vignesh.jobmatcher.ui.nextAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shortlist-first statuses, dated steps and the "next action" shown in Applications. */
class ApplicationTrackingTest {

    private val day = 24L * 3600 * 1000
    private val now = 1_800_000_000_000L
    private val job = Job(
        id = "WORKDAY:nvidia:JR1", companyId = "nvidia", companyName = "NVIDIA", title = "ML Engineer",
        location = "Bengaluru", url = "https://x", description = "d", source = SourceType.WORKDAY
    )

    @Test
    fun savedFromOlderVersions_becomesShortlisted() {
        assertEquals(JobStatus.SHORTLISTED, JobStatus.parse("SAVED"))
        assertEquals(JobStatus.NEW, JobStatus.parse(null))
        assertTrue(JobStatus.entries.all { it.meaning.isNotBlank() })
    }

    @Test
    fun eventsAndHistory_roundTrip() {
        val j = job.copy(
            status = JobStatus.APPLIED, shortlistedAt = now - 3 * day, appliedAt = now,
            events = listOf(JobEvent("e1", EventType.INTERVIEW, "Round 1", now + day, notes = "Meet link", calendarEventId = 42L)),
            history = listOf(StatusChange(JobStatus.SHORTLISTED, now - 3 * day), StatusChange(JobStatus.APPLIED, now))
        )
        val back = JsonCodec.jobFromJson(JsonCodec.jobToJson(j))
        assertEquals(j.events, back.events)
        assertEquals(j.history, back.history)
        assertEquals(j.shortlistedAt, back.shortlistedAt)

        // Jobs stored before dates existed load with none.
        val old = JsonCodec.jobToJson(job).apply { remove("events"); remove("history"); put("status", "SAVED") }
        val loaded = JsonCodec.jobFromJson(old)
        assertEquals(emptyList<JobEvent>(), loaded.events)
        assertEquals(JobStatus.SHORTLISTED, loaded.status)
    }

    @Test
    fun nextAction_followsTheApplicationStage() {
        assertTrue(nextAction(job.copy(status = JobStatus.SHORTLISTED), now)!!.contains("prepare the application kit"))
        assertTrue(nextAction(job.copy(status = JobStatus.SHORTLISTED, tailoring = Tailoring(coverLetter = "x")), now)!!.contains("apply"))

        val followUp = JobEvent("f", EventType.FOLLOW_UP, "Follow up", now + 2 * day)
        assertTrue(nextAction(job.copy(status = JobStatus.APPLIED, events = listOf(followUp)), now)!!.startsWith("Next: follow up on"))
        assertEquals("Waiting to hear back", nextAction(job.copy(status = JobStatus.APPLIED), now))

        val past = JobEvent("p", EventType.INTERVIEW, "Round 1", now - day)
        assertEquals("Next: add your next interview date", nextAction(job.copy(status = JobStatus.INTERVIEWING, events = listOf(past)), now))
        assertNull(nextAction(job.copy(status = JobStatus.REJECTED), now))
    }
}
