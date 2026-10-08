package com.vignesh.jobmatcher

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.vignesh.jobmatcher.data.AppStorage
import com.vignesh.jobmatcher.data.DefaultData
import com.vignesh.jobmatcher.data.JobRepository
import com.vignesh.jobmatcher.model.AppSettings
import com.vignesh.jobmatcher.model.CandidateProfile
import com.vignesh.jobmatcher.model.Company
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobStatus
import com.vignesh.jobmatcher.model.SourceType
import com.vignesh.jobmatcher.work.DailyFetchWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val jobs: List<Job> = emptyList(),
    val companies: List<Company> = emptyList(),
    val profile: CandidateProfile = CandidateProfile(),
    val settings: AppSettings = AppSettings(),
    val resume: String = "",
    val pendingCount: Int = 0,
    val busy: Boolean = false,
    val progress: String = "",
    val message: String? = null,
    /** One-shot request to open a job's detail screen (e.g. right after adding it from a link). */
    val openJobRequest: String? = null
) {
    private val priorityById: Map<String, Int> get() = companies.associate { it.id to it.priority }

    fun isTopChoice(job: Job): Boolean = priorityById[job.companyId] == 1

    /** Top-choice companies (Google) first, then by Claude score. */
    val matches: List<Job>
        get() {
            val priority = priorityById
            return jobs.filter { it.isScored && it.claudeScore!! >= settings.matchThreshold && it.status != JobStatus.DISMISSED }
                .sortedWith(
                    compareBy<Job> { priority[it.companyId] ?: 2 }
                        .thenByDescending { it.claudeScore }
                        .thenByDescending { it.firstSeenAt }
                )
        }

    val nextSearchBatch: List<Company>
        get() = JobRepository.nextSearchBatch(
            companies.filter { it.enabled && it.source == SourceType.CLAUDE_SEARCH },
            settings.searchBatchSize,
            System.currentTimeMillis()
        )

    val belowThreshold: List<Job>
        get() = jobs.filter { it.isScored && it.claudeScore!! < settings.matchThreshold && it.status != JobStatus.DISMISSED }
            .sortedByDescending { it.claudeScore }

    val shortlist: List<Job>
        get() = jobs.filter { !it.isScored && !it.closed && it.status != JobStatus.DISMISSED && it.localScore >= settings.prefilterThreshold }
            .sortedByDescending { it.localScore }

    val tracked: List<Job> get() = jobs.filter { it.status in JobStatus.TRACKED }

    /** Shortlisted -> offer: the applications you're actively working (vs. settings.shortlistLimit). */
    val activeCount: Int get() = jobs.count { it.status in JobStatus.ACTIVE }

    /** Every job you added from a link, scored or not, best score first. */
    val manualJobs: List<Job>
        get() = jobs.filter { it.isManual && it.status != JobStatus.DISMISSED }
            .sortedWith(compareByDescending<Job> { it.claudeScore ?: -1 }.thenByDescending { it.firstSeenAt })
}

class JobViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val repo = JobRepository(context)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) {
                UiState(
                    jobs = AppStorage.loadJobs(context),
                    companies = AppStorage.loadCompanies(context),
                    profile = AppStorage.loadProfile(context),
                    settings = AppStorage.loadSettings(context),
                    resume = AppStorage.loadResume(context),
                    pendingCount = repo.pendingScoring().size
                )
            }
            _state.update { s.copy(busy = it.busy, progress = it.progress, message = it.message, openJobRequest = it.openJobRequest) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun consumeOpenJobRequest() = _state.update { it.copy(openJobRequest = null) }

    private fun say(msg: String) = _state.update { it.copy(message = msg) }

    private fun runBusy(block: suspend () -> String) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, progress = "") }
            val msg = runCatching { withContext(Dispatchers.IO) { block() } }
                .getOrElse { "Error: ${it.message ?: it.javaClass.simpleName}" }
            _state.update { it.copy(busy = false, progress = "", message = msg) }
            reload()
        }
    }

    // ---- Fetch ---------------------------------------------------------------------

    fun fetchAll() = runBusy {
        val report = repo.fetchAll { p -> _state.update { it.copy(progress = p) } }
        if (report.results.isEmpty()) return@runBusy "No enabled companies with an automatic source. Add some in Setup → Companies."
        buildString {
            append("Fetched ${report.results.size} companies · ${report.newShortlisted} new on the shortlist.")
            if (report.failed.isNotEmpty()) append(" Failed: ${report.failed.joinToString { it.company }}.")
        }
    }

    fun fetchCompany(company: Company) = runBusy {
        val r = repo.fetchCompany(company)
        r.error?.let { "${company.name}: $it" }
            ?: "${company.name}: ${r.fetched} postings, ${r.kept} in your locations, ${r.newShortlisted} new on the shortlist."
    }

    // ---- Claude prompts (return null + message when there's nothing to send) -------

    fun scoringPrompt(jobIds: List<String>? = null): String? =
        repo.scoringPrompt(jobIds).also { if (it == null) say("Nothing to score -- the shortlist is empty.") }

    fun applyScores(raw: String) = runBusy {
        repo.applyScores(raw).map { n ->
            val left = repo.pendingScoring().size
            "Applied $n Claude scores." + if (left > 0) " $left still waiting -- copy the next batch." else " Shortlist fully scored."
        }.getOrThrow()
    }

    fun searchPrompt(): String? =
        repo.searchPrompt().also { if (it == null) say("No enabled 'Claude web search' companies. Add one in Setup → Companies.") }

    fun applyFoundJobs(raw: String) = runBusy {
        repo.applyFoundJobs(raw).map { (added, updated) ->
            if (added + updated == 0) "Claude found nothing suitable open right now -- marked as searched."
            else "Claude search: $added new jobs, $updated updated."
        }.getOrThrow()
    }

    // ---- Jobs you add from a link -----------------------------------------------

    /** Paste box on Discover, or text shared into the app from LinkedIn/Chrome/etc. */
    fun addJobFromLink(text: String) = runBusy {
        val result = repo.addJobFromLink(text)
        _state.update { it.copy(openJobRequest = result.jobId) }
        result.note
    }

    fun retryImport(jobId: String) = runBusy { repo.retryImport(jobId) }

    fun readJobPrompt(jobId: String): String? = repo.readJobPrompt(jobId)

    fun applyJobDetails(jobId: String, raw: String) = runBusy {
        repo.applyJobDetails(jobId, raw).map { scored ->
            if (scored) "Job details and Claude score saved." else "Job details saved -- score it with Claude next."
        }.getOrThrow()
    }

    fun setManualDetails(jobId: String, title: String, company: String, description: String) = runBusy {
        repo.setManualDetails(jobId, title, company, description)
        "Saved. You can score it with Claude now."
    }

    fun addSearchTerms(terms: List<String>) = runBusy {
        val s = AppStorage.loadSettings(context)
        val merged = (s.searchTerms + terms).distinctBy { it.lowercase().trim() }
        AppStorage.saveSettings(context, s.copy(searchTerms = merged))
        repo.rescoreLocal()
        "Search terms: ${merged.size} total."
    }

    fun profilePrompt(): String = repo.profilePrompt()

    fun applyProfile(raw: String) = runBusy {
        repo.applyProfile(raw).map { n -> "Profile updated from Claude ($n skills). Local scores recomputed." }.getOrThrow()
    }

    fun tailoringPrompt(jobId: String): String? = repo.tailoringPrompt(jobId)

    fun applyTailoring(jobId: String, raw: String) = runBusy {
        repo.applyTailoring(jobId, raw).map { "Tailored material saved." }.getOrThrow()
    }

    // ---- Tracking ------------------------------------------------------------------

    fun setStatus(jobId: String, status: JobStatus) = runBusy { repo.setStatus(jobId, status) }

    fun saveEvent(jobId: String, event: com.vignesh.jobmatcher.model.JobEvent) = runBusy {
        repo.saveEvent(jobId, event)
        if (event.calendarEventId != null) "Date saved -- tap 📅 Update to change it in Google Calendar too." else "Date saved."
    }

    fun deleteEvent(jobId: String, eventId: String) = runBusy {
        repo.deleteEvent(jobId, eventId)
        "Date removed (and from Google Calendar, if it was there)."
    }

    fun syncEvent(jobId: String, eventId: String) = runBusy { repo.syncEvent(jobId, eventId) }

    fun setNotes(jobId: String, notes: String) = runBusy {
        repo.setNotes(jobId, notes)
        "Notes saved."
    }

    // ---- Setup ---------------------------------------------------------------------

    fun saveCompany(company: Company) = runBusy {
        repo.saveCompany(company)
        "Saved ${company.name}."
    }

    fun deleteCompany(company: Company) = runBusy {
        repo.deleteCompany(company.id)
        "Removed ${company.name}."
    }

    fun saveSettings(settings: AppSettings) = runBusy {
        val old = AppStorage.loadSettings(context)
        AppStorage.saveSettings(context, settings)
        if (old.locationKeywords != settings.locationKeywords || old.excludeTitleKeywords != settings.excludeTitleKeywords ||
            old.searchTerms != settings.searchTerms
        ) {
            repo.rescoreLocal()
        }
        DailyFetchWorker.schedule(context)
        "Settings saved."
    }

    fun saveResumeText(text: String) = runBusy {
        AppStorage.saveResume(context, text.trim())
        "Resume saved (${text.length} chars). Run Profile Analysis to refresh the skill profile."
    }

    fun importResumePdf(uri: Uri) = runBusy {
        PDFBoxResourceLoader.init(context)
        val text = context.contentResolver.openInputStream(uri)!!.use { input ->
            PDDocument.load(input).use { PDFTextStripper().getText(it) }
        }.trim()
        if (text.isBlank()) error("No text found in that PDF (is it a scanned image?).")
        AppStorage.saveResume(context, text)
        "Imported resume (${text.length} chars). Run Profile Analysis to refresh the skill profile."
    }

    fun resetProfile() = runBusy {
        AppStorage.saveProfile(context, DefaultData.profile(context))
        repo.rescoreLocal()
        "Profile reset to the bundled default."
    }
}
