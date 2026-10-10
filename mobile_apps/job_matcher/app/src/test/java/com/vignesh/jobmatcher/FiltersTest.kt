package com.vignesh.jobmatcher

import com.vignesh.jobmatcher.model.ApplicationFilters
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobOrigin
import com.vignesh.jobmatcher.model.JobStatus
import com.vignesh.jobmatcher.model.MatchFilters
import com.vignesh.jobmatcher.model.SourceType
import com.vignesh.jobmatcher.model.toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {
    private fun job(id: String, status: JobStatus, origin: JobOrigin = JobOrigin.AUTOMATIC, score: Int? = 85) = Job(
        id = id, companyId = "c", companyName = "C", title = id, location = "Bengaluru", url = "https://x/$id",
        description = "d", source = SourceType.WORKDAY, origin = origin, status = status, claudeScore = score
    )

    private val jobs = listOf(
        job("new-auto", JobStatus.NEW),
        job("short-manual", JobStatus.SHORTLISTED, JobOrigin.MANUAL),
        job("applied-auto", JobStatus.APPLIED),
        job("rejected", JobStatus.REJECTED),
        job("dismissed", JobStatus.DISMISSED),
        job("low-new", JobStatus.NEW, score = 50),
        job("manual-unscored", JobStatus.NEW, JobOrigin.MANUAL, score = null)
    )

    @Test
    fun matchFilters_multiSelectAndDismissedHiddenByDefault() {
        val none = UiState(jobs = jobs, matchFilters = MatchFilters())
        val (above, below) = none.filteredMatches()
        assertFalse(above.any { it.id == "dismissed" }) // hidden unless asked for
        assertEquals(listOf("low-new"), below.map { it.id })

        val two = UiState(jobs = jobs, matchFilters = MatchFilters(statuses = setOf(JobStatus.NEW, JobStatus.APPLIED)))
        assertEquals(setOf("new-auto", "applied-auto"), two.filteredMatches().first.map { it.id }.toSet())

        val dismissed = UiState(jobs = jobs, matchFilters = MatchFilters(statuses = setOf(JobStatus.DISMISSED)))
        assertEquals(listOf("dismissed"), dismissed.filteredMatches().first.map { it.id })

        val autoShortlisted = UiState(jobs = jobs, matchFilters = MatchFilters(setOf(JobOrigin.AUTOMATIC), setOf(JobStatus.SHORTLISTED)))
        assertTrue(autoShortlisted.filteredMatches().first.isEmpty()) // the shortlisted one is manual
    }

    @Test
    fun manualOnly_includesUnscored() {
        val f = MatchFilters(origins = setOf(JobOrigin.MANUAL))
        assertTrue(f.manualOnly)
        assertEquals(setOf("short-manual", "manual-unscored"), UiState(jobs = jobs, matchFilters = f).filteredManual().map { it.id }.toSet())
        assertFalse(MatchFilters(origins = setOf(JobOrigin.MANUAL, JobOrigin.AUTOMATIC)).manualOnly)
    }

    @Test
    fun applicationFilters_defaultActive_andNotInterestedTrackable() {
        val active = UiState(jobs = jobs).filteredApplications()
        assertEquals(setOf("short-manual", "applied-auto"), active.map { it.id }.toSet())

        val closed = UiState(jobs = jobs, applicationFilters = ApplicationFilters(setOf(JobStatus.REJECTED, JobStatus.DISMISSED)))
        assertEquals(setOf("rejected", "dismissed"), closed.filteredApplications().map { it.id }.toSet())
    }

    @Test
    fun toggle_addsAndRemoves() {
        val s = setOf(JobStatus.NEW).toggle(JobStatus.APPLIED)
        assertEquals(setOf(JobStatus.NEW, JobStatus.APPLIED), s)
        assertEquals(setOf(JobStatus.APPLIED), s.toggle(JobStatus.NEW))
    }

    @Test
    fun filters_surviveDataReload() {
        // reload() rebuilds UiState from storage and must carry the session's filters over.
        val before = UiState(matchFilters = MatchFilters(statuses = setOf(JobStatus.NEW)), applicationFilters = ApplicationFilters(setOf(JobStatus.DISMISSED)))
        val reloaded = UiState(jobs = jobs).copy(matchFilters = before.matchFilters, applicationFilters = before.applicationFilters)
        assertEquals(before.matchFilters, reloaded.matchFilters)
        assertEquals(before.applicationFilters, reloaded.applicationFilters)
    }
}
