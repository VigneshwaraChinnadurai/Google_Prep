package com.vignesh.jobmatcher.data

import android.content.Context
import com.vignesh.jobmatcher.claude.ClaudeResponseParser
import com.vignesh.jobmatcher.claude.PromptBuilder
import com.vignesh.jobmatcher.claude.ScoreUpdate
import com.vignesh.jobmatcher.matching.LocalScorer
import com.vignesh.jobmatcher.model.Company
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.calendar.CalendarSync
import com.vignesh.jobmatcher.model.EventType
import com.vignesh.jobmatcher.model.JobEvent
import com.vignesh.jobmatcher.model.JobOrigin
import com.vignesh.jobmatcher.model.StatusChange
import java.util.Calendar
import com.vignesh.jobmatcher.model.JobStatus
import com.vignesh.jobmatcher.model.SourceType
import com.vignesh.jobmatcher.sources.ImportedJob
import com.vignesh.jobmatcher.sources.JobFetcher
import com.vignesh.jobmatcher.sources.JobLink
import com.vignesh.jobmatcher.sources.ManualJobImporter
import com.vignesh.jobmatcher.sources.RawPosting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class CompanyFetchResult(val company: String, val fetched: Int, val kept: Int, val newShortlisted: Int, val error: String?)

data class FetchReport(val results: List<CompanyFetchResult>) {
    val newShortlisted get() = results.sumOf { it.newShortlisted }
    val failed get() = results.filter { it.error != null }
}

/**
 * The pipeline: fetch (automatic sources) -> location/title gate -> on-device LocalScorer
 * -> shortlist (localScore >= prefilter) -> batched Claude scoring via copy/paste ->
 * Matches (claudeScore >= matchThreshold). Companies without an API go through the Claude
 * web-search prompt instead, whose results arrive already scored.
 */
class JobRepository(
    private val context: Context,
    private val fetcher: JobFetcher = JobFetcher(),
    private val importer: ManualJobImporter = ManualJobImporter()
) {

    private fun scorer() = LocalScorer(AppStorage.loadProfile(context), AppStorage.loadSettings(context))

    // ---- Fetching ------------------------------------------------------------------

    suspend fun fetchAll(onProgress: (String) -> Unit = {}): FetchReport = coroutineScope {
        val companies = AppStorage.loadCompanies(context).filter { it.enabled && it.source.automatic }
        val gate = Semaphore(4)
        val results = companies.map { company ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    onProgress("Fetching ${company.name}…")
                    fetchCompany(company)
                }
            }
        }.awaitAll()
        prune()
        FetchReport(results)
    }

    suspend fun fetchCompany(company: Company): CompanyFetchResult = withContext(Dispatchers.IO) {
        val scorer = scorer()
        val now = System.currentTimeMillis()
        val raw = runCatching {
            fetcher.fetch(
                company,
                AppStorage.loadSettings(context).searchTerms,
                AppStorage.loadSettings(context).locationKeywords,
                scorer::isTitleAllowed
            )
        }
        val postings = raw.getOrElse { e ->
            val msg = e.message ?: e.javaClass.simpleName
            AppStorage.updateCompanies(context) { list ->
                list.map { if (it.id == company.id) it.copy(lastFetchedAt = now, lastError = msg) else it }
            }
            return@withContext CompanyFetchResult(company.name, 0, 0, 0, msg)
        }

        val kept = postings.filter { scorer.isLocationAllowed(it.location) && scorer.isTitleAllowed(it.title) }
        val prefilter = AppStorage.loadSettings(context).prefilterThreshold
        var newShortlisted = 0
        AppStorage.updateJobs(context) { existing ->
            val byId = existing.associateBy { it.id }.toMutableMap()
            val freshIds = HashSet<String>()
            kept.forEach { p ->
                val id = jobId(company, p)
                freshIds += id
                val old = byId[id]
                val local = scorer.score(p.title, p.description)
                byId[id] = if (old == null) {
                    if (local.score >= prefilter) newShortlisted++
                    Job(
                        id = id, companyId = company.id, companyName = company.name,
                        title = p.title, location = p.location, url = p.url, description = p.description,
                        postedAt = p.postedAt, source = company.source, firstSeenAt = now, lastSeenAt = now,
                        localScore = local.score, localMatchedSkills = local.matchedSkills
                    )
                } else {
                    old.copy(
                        title = p.title, location = p.location, url = p.url, description = p.description,
                        postedAt = p.postedAt.ifBlank { old.postedAt }, lastSeenAt = now, closed = false,
                        localScore = local.score, localMatchedSkills = local.matchedSkills
                    )
                }
            }
            // Anything this company's API no longer lists has been taken down.
            byId.values.filter { it.companyId == company.id && it.source.automatic && it.id !in freshIds && !it.closed }
                .forEach { byId[it.id] = it.copy(closed = true) }
            byId.values.toList()
        }
        AppStorage.updateCompanies(context) { list ->
            list.map {
                if (it.id == company.id) it.copy(lastFetchedAt = now, lastFetchCount = kept.size, lastError = "") else it
            }
        }
        CompanyFetchResult(company.name, postings.size, kept.size, newShortlisted, null)
    }

    private fun jobId(company: Company, p: RawPosting) = "${company.source.name}:${company.id}:${p.externalId}"

    /** Drop stale, untouched postings so jobs.json doesn't grow forever. */
    private fun prune() {
        val cutoff = System.currentTimeMillis() - 45L * 24 * 3600 * 1000
        AppStorage.updateJobs(context) { jobs ->
            jobs.filterNot { it.closed && it.status == JobStatus.NEW && it.lastSeenAt < cutoff }
        }
    }

    /** Recompute on-device scores after the profile or filters change. */
    fun rescoreLocal() {
        val scorer = scorer()
        AppStorage.updateJobs(context) { jobs ->
            jobs.map { j ->
                val s = scorer.score(j.title, j.description)
                j.copy(localScore = s.score, localMatchedSkills = s.matchedSkills)
            }
        }
    }

    // ---- Claude scoring ------------------------------------------------------------

    /**
     * Unscored jobs worth a Claude round-trip. Jobs you added from a link skip the pre-filter
     * (you already chose them) and go first, as long as their description has been read.
     */
    fun pendingScoring(): List<Job> {
        val prefilter = AppStorage.loadSettings(context).prefilterThreshold
        return AppStorage.loadJobs(context)
            .filter { !it.isScored && !it.closed && it.status != JobStatus.DISMISSED }
            .filter { if (it.isManual) !it.needsDetails else it.localScore >= prefilter }
            .sortedWith(compareByDescending<Job> { it.isManual }.thenByDescending { it.localScore })
    }

    /** Next batch of the shortlist, or a specific set of jobs (re-scoring from the detail screen). */
    fun scoringPrompt(jobIds: List<String>? = null): String? {
        val settings = AppStorage.loadSettings(context)
        val jobs = if (jobIds != null) {
            AppStorage.loadJobs(context).filter { it.id in jobIds }
        } else {
            pendingScoring().take(settings.scoringBatchSize)
        }
        if (jobs.isEmpty()) return null
        return PromptBuilder.scoringPrompt(AppStorage.loadResume(context), AppStorage.loadProfile(context), jobs, settings)
    }

    /** Returns how many jobs received a score. */
    fun applyScores(raw: String): Result<Int> = ClaudeResponseParser.parseScores(raw).mapCatching { updates ->
        val now = System.currentTimeMillis()
        var applied = 0
        AppStorage.updateJobs(context) { jobs ->
            val ids = jobs.map { it.id }.toSet()
            val bySuffix = jobs.associateBy { it.id.substringAfterLast(':') }
            // Claude occasionally trims an id's prefix; fall back to the external-id suffix.
            val resolved: Map<String, ScoreUpdate> = updates.mapNotNull { u ->
                val id = if (u.jobId in ids) u.jobId else bySuffix[u.jobId.substringAfterLast(':')]?.id
                id?.let { it to u }
            }.toMap()
            jobs.map { j ->
                val u = resolved[j.id] ?: return@map j
                applied++
                j.copy(
                    claudeScore = u.score, claudeVerdict = u.verdict, claudeReasons = u.reasons,
                    matchedSkills = u.matchedSkills, gaps = u.gaps, scoredAt = now
                )
            }
        }
        if (applied == 0) error("None of the scored job ids match jobs in the app -- was this reply for an older batch?")
        applied
    }

    // ---- Claude web search ---------------------------------------------------------

    fun searchCompanies(): List<Company> =
        AppStorage.loadCompanies(context).filter { it.enabled && it.source == SourceType.CLAUDE_SEARCH }

    /**
     * Builds the web-search prompt for the next batch (see [nextSearchBatch]) and remembers
     * which companies it covered, so pasting the reply marks exactly those as searched.
     */
    fun searchPrompt(): String? {
        val batch = nextSearchBatch(searchCompanies(), AppStorage.loadSettings(context).searchBatchSize, System.currentTimeMillis())
        if (batch.isEmpty()) return null
        AppStorage.savePendingSearch(context, batch.map { it.id })
        val ids = batch.map { it.id }.toSet()
        val known = AppStorage.loadJobs(context).filter { it.companyId in ids && !it.closed }
        return PromptBuilder.searchPrompt(
            AppStorage.loadResume(context), AppStorage.loadProfile(context), batch, known, AppStorage.loadSettings(context)
        )
    }

    /** Returns (added, updated). */
    fun applyFoundJobs(raw: String): Result<Pair<Int, Int>> = ClaudeResponseParser.parseFoundJobs(raw).mapCatching { found ->
        val pending = AppStorage.loadPendingSearch(context).toSet()
        val now0 = System.currentTimeMillis()
        // An empty result is a legitimate answer ("nothing open that fits") -- still mark the batch searched.
        AppStorage.updateCompanies(context) { list ->
            list.map { if (it.id in pending) it.copy(lastFetchedAt = now0, lastFetchCount = 0, lastError = "") else it }
        }
        if (found.isEmpty()) return@mapCatching 0 to 0
        // Prefer the companies this prompt was for when matching Claude's company names.
        val companies = AppStorage.loadCompanies(context).sortedBy { if (it.id in pending) 0 else 1 }
        val scorer = scorer()
        val now = System.currentTimeMillis()
        var added = 0
        var updated = 0
        AppStorage.updateJobs(context) { jobs ->
            val result = jobs.toMutableList()
            val indexByUrl = HashMap<String, Int>()
            jobs.forEachIndexed { i, j -> indexByUrl.putIfAbsent(normalizeUrl(j.url), i) }
            found.forEach { f ->
                val company = companies.firstOrNull { it.name.equals(f.company, ignoreCase = true) }
                    ?: companies.firstOrNull { f.company.contains(it.name, ignoreCase = true) || it.name.contains(f.company, ignoreCase = true) }
                val companyId = company?.id ?: slug(f.company)
                val key = normalizeUrl(f.url)
                val local = scorer.score(f.title, f.description)
                val oldIndex = indexByUrl[key]
                val old = oldIndex?.let { result[it] }
                val s = f.score
                if (old == null) {
                    added++
                    indexByUrl[key] = result.size
                    result += Job(
                        id = "${SourceType.CLAUDE_SEARCH.name}:$companyId:${key.hashCode().toUInt().toString(16)}",
                        companyId = companyId, companyName = company?.name ?: f.company,
                        title = f.title, location = f.location, url = f.url, description = f.description,
                        postedAt = f.postedAt, source = SourceType.CLAUDE_SEARCH, firstSeenAt = now, lastSeenAt = now,
                        localScore = local.score, localMatchedSkills = local.matchedSkills,
                        claudeScore = s?.score, claudeVerdict = s?.verdict.orEmpty(), claudeReasons = s?.reasons.orEmpty(),
                        matchedSkills = s?.matchedSkills.orEmpty(), gaps = s?.gaps.orEmpty(),
                        scoredAt = if (s != null) now else 0
                    )
                } else {
                    updated++
                    result[oldIndex] = old.copy(
                        lastSeenAt = now, closed = false,
                        description = f.description.ifBlank { old.description },
                        claudeScore = s?.score ?: old.claudeScore,
                        claudeVerdict = s?.verdict ?: old.claudeVerdict,
                        claudeReasons = s?.reasons ?: old.claudeReasons,
                        matchedSkills = s?.matchedSkills ?: old.matchedSkills,
                        gaps = s?.gaps ?: old.gaps,
                        scoredAt = if (s != null) now else old.scoredAt
                    )
                }
            }
            result
        }
        val perCompany = found.groupingBy { f ->
            companies.firstOrNull { it.name.equals(f.company, ignoreCase = true) }?.id
        }.eachCount()
        AppStorage.updateCompanies(context) { list ->
            list.map { c -> perCompany[c.id]?.let { c.copy(lastFetchCount = it) } ?: c }
        }
        added to updated
    }

    // ---- Jobs you add from a link -------------------------------------------------

    data class AddResult(val jobId: String, val alreadyTracked: Boolean, val complete: Boolean, val note: String)

    /**
     * Adds a job from a pasted/shared link: reads it (careers API, LinkedIn page or page
     * data), scores it on-device and stores it tagged MANUAL. If the page can't be read, a
     * placeholder is still saved so you can let Claude read it or paste the description.
     */
    suspend fun addJobFromLink(text: String): AddResult = withContext(Dispatchers.IO) {
        val url = JobLink.extractUrl(text) ?: error("No link found -- copy the job's URL and try again.")
        AppStorage.loadJobs(context).firstOrNull { normalizeUrl(it.url) == normalizeUrl(url) }?.let {
            return@withContext AddResult(it.id, alreadyTracked = true, complete = !it.needsDetails, note = "Already tracked: ${it.title}")
        }
        val now = System.currentTimeMillis()
        val id = "${SourceType.MANUAL_LINK.name}:${slug(hostOf(url))}:${normalizeUrl(url).hashCode().toUInt().toString(16)}"
        val imported = runCatching { importer.import(url) }
        val job = imported.fold(
            onSuccess = { buildManualJob(id, url, it, now) },
            onFailure = {
                Job(
                    id = id, companyId = slug(hostOf(url)), companyName = companyGuess(hostOf(url)),
                    title = "Job from ${hostOf(url)}", location = "", url = url, description = "",
                    source = SourceType.MANUAL_LINK, origin = JobOrigin.MANUAL, firstSeenAt = now, lastSeenAt = now,
                    statusUpdatedAt = now
                )
            }
        )
        AppStorage.updateJobs(context) { it + job }
        val note = imported.fold(
            onSuccess = { r ->
                if (r.complete) "Added \"${job.title}\" (read via ${r.via})."
                else "Added, but only part of the posting could be read -- let Claude read it or paste the description."
            },
            onFailure = { "Saved the link, but couldn't read the page (${it.message}). Let Claude read it, or paste the description." }
        )
        AddResult(id, alreadyTracked = false, complete = !job.needsDetails, note = note)
    }

    /** Re-reads a manual job's link (e.g. after a network error). */
    suspend fun retryImport(jobId: String): String = withContext(Dispatchers.IO) {
        val job = AppStorage.loadJobs(context).firstOrNull { it.id == jobId } ?: error("Job not found.")
        val imported = importer.import(job.url)
        val fresh = buildManualJob(job.id, job.url, imported, job.firstSeenAt)
        updateJob(jobId) {
            it.copy(
                title = fresh.title, companyId = fresh.companyId, companyName = fresh.companyName,
                location = fresh.location, description = fresh.description, postedAt = fresh.postedAt,
                localScore = fresh.localScore, localMatchedSkills = fresh.localMatchedSkills,
                lastSeenAt = System.currentTimeMillis()
            )
        }
        if (imported.complete) "Read the full posting via ${imported.via}." else "Still only part of the posting -- try Claude or paste it."
    }

    private fun buildManualJob(id: String, url: String, imported: ImportedJob, now: Long): Job {
        val p = imported.posting
        val companyName = imported.company.ifBlank { companyGuess(hostOf(url)) }
        val company = matchCompany(companyName, url)
        val local = scorer().score(p.title, p.description)
        return Job(
            id = id,
            companyId = company?.id ?: slug(companyName),
            companyName = company?.name ?: companyName,
            title = p.title.ifBlank { "Job from ${hostOf(url)}" }, location = p.location, url = url,
            description = p.description, postedAt = p.postedAt,
            source = SourceType.MANUAL_LINK, origin = JobOrigin.MANUAL, firstSeenAt = now, lastSeenAt = now,
            localScore = local.score, localMatchedSkills = local.matchedSkills,
            statusUpdatedAt = now
        )
    }

    /** Your configured company with this name, or whose careers URL shares the link's domain. */
    private fun matchCompany(name: String, url: String): Company? {
        val companies = AppStorage.loadCompanies(context)
        val host = hostOf(url)
        return companies.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: companies.firstOrNull {
                name.isNotBlank() && (it.name.contains(name, true) || name.contains(it.name.substringBefore(" ("), true))
            }
            ?: companies.firstOrNull { c ->
                host.isNotBlank() && c.identifier.isNotBlank() &&
                    hostOf(c.identifier).let { it.isNotBlank() && rootDomain(it) == rootDomain(host) }
            }
    }

    fun readJobPrompt(jobId: String): String? {
        val job = AppStorage.loadJobs(context).firstOrNull { it.id == jobId } ?: return null
        return PromptBuilder.readJobPrompt(
            job.url, AppStorage.loadResume(context), AppStorage.loadProfile(context),
            listOf(job.title, job.companyName, job.location, job.description).filter { it.isNotBlank() }.joinToString("\n")
        )
    }

    /** Applies Claude's read of a manual job: fills in the details and (usually) its score. Returns whether it was scored. */
    fun applyJobDetails(jobId: String, raw: String): Result<Boolean> = ClaudeResponseParser.parseJobDetails(raw).map { f ->
        val now = System.currentTimeMillis()
        val company = if (f.company.isBlank()) null else matchCompany(f.company, "")
        val scorer = scorer()
        updateJob(jobId) { j ->
            val title = f.title.ifBlank { j.title }
            val description = f.description.ifBlank { j.description }
            val local = scorer.score(title, description)
            val s = f.score
            j.copy(
                title = title,
                companyId = company?.id ?: if (f.company.isNotBlank()) slug(f.company) else j.companyId,
                companyName = company?.name ?: f.company.ifBlank { j.companyName },
                location = f.location.ifBlank { j.location }, description = description,
                postedAt = f.postedAt.ifBlank { j.postedAt },
                localScore = local.score, localMatchedSkills = local.matchedSkills,
                claudeScore = s?.score ?: j.claudeScore, claudeVerdict = s?.verdict ?: j.claudeVerdict,
                claudeReasons = s?.reasons ?: j.claudeReasons, matchedSkills = s?.matchedSkills ?: j.matchedSkills,
                gaps = s?.gaps ?: j.gaps, scoredAt = if (s != null) now else j.scoredAt
            )
        }
        f.score != null
    }

    /** For pages nobody can read automatically: you paste the job description (and fix title/company). */
    fun setManualDetails(jobId: String, title: String, company: String, description: String) {
        val match = if (company.isBlank()) null else matchCompany(company, "")
        val scorer = scorer()
        updateJob(jobId) { j ->
            val t = title.trim().ifBlank { j.title }
            val d = description.trim()
            val local = scorer.score(t, d)
            j.copy(
                title = t,
                companyName = match?.name ?: company.trim().ifBlank { j.companyName },
                companyId = match?.id ?: if (company.isNotBlank()) slug(company) else j.companyId,
                description = d, localScore = local.score, localMatchedSkills = local.matchedSkills
            )
        }
    }

    // ---- Profile -------------------------------------------------------------------

    fun profilePrompt(): String = PromptBuilder.profilePrompt(AppStorage.loadResume(context))

    fun applyProfile(raw: String): Result<Int> = ClaudeResponseParser.parseProfile(raw).map { profile ->
        AppStorage.saveProfile(context, profile)
        rescoreLocal()
        profile.skills.size
    }

    // ---- Tailoring & tracking ------------------------------------------------------

    fun tailoringPrompt(jobId: String): String? =
        AppStorage.loadJobs(context).firstOrNull { it.id == jobId }
            ?.let { PromptBuilder.tailoringPrompt(AppStorage.loadResume(context), it) }

    fun applyTailoring(jobId: String, raw: String): Result<Unit> = ClaudeResponseParser.parseTailoring(raw).map { t ->
        updateJob(jobId) { it.copy(tailoring = t) }
    }

    /**
     * Changes a job's status and applies its side effects: history entry, shortlist date,
     * applied date + an automatic follow-up date. Returns a user-facing note (incl. a warning
     * when the shortlist goes past your limit).
     */
    fun setStatus(jobId: String, status: JobStatus): String {
        val now = System.currentTimeMillis()
        val settings = AppStorage.loadSettings(context)
        var note = "Marked ${status.label}."
        AppStorage.updateJobs(context) { jobs ->
            jobs.map { j ->
                if (j.id != jobId || j.status == status) return@map j
                var events = j.events
                if (status == JobStatus.APPLIED && events.none { it.type == EventType.FOLLOW_UP }) {
                    val followUp = Calendar.getInstance().apply {
                        timeInMillis = now
                        add(Calendar.DAY_OF_MONTH, settings.followUpDays)
                        set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    events = events + JobEvent(
                        id = newEventId(), type = EventType.FOLLOW_UP,
                        title = "Follow up on application", startMillis = followUp
                    )
                    note = "Marked Applied. Follow-up added for ${DATE_FMT.format(java.util.Date(followUp))} -- tap 📅 to put it in Google Calendar."
                }
                j.copy(
                    status = status, statusUpdatedAt = now,
                    shortlistedAt = if (status == JobStatus.SHORTLISTED && j.shortlistedAt == 0L) now else j.shortlistedAt,
                    appliedAt = if (status == JobStatus.APPLIED && j.appliedAt == 0L) now else j.appliedAt,
                    events = events,
                    history = j.history + StatusChange(status, now)
                )
            }
        }
        if (status == JobStatus.SHORTLISTED) {
            val active = AppStorage.loadJobs(context).count { it.status in JobStatus.ACTIVE }
            note = if (active > settings.shortlistLimit) {
                "Shortlisted -- you now have $active active applications (limit ${settings.shortlistLimit}). " +
                    "Consider finishing or dropping some before adding more."
            } else "Shortlisted ($active of ${settings.shortlistLimit}). Prepare the application kit next."
        }
        return note
    }

    // ---- Dates & Google Calendar ---------------------------------------------------

    fun saveEvent(jobId: String, event: JobEvent) {
        updateJob(jobId) { j ->
            val exists = j.events.any { it.id == event.id }
            val updated = if (exists) j.events.map {
                if (it.id == event.id) event.copy(calendarEventId = it.calendarEventId, calendarStale = it.calendarEventId != null) else it
            } else j.events + event
            j.copy(events = updated.sortedBy { it.startMillis })
        }
    }

    fun deleteEvent(jobId: String, eventId: String) {
        val job = AppStorage.loadJobs(context).firstOrNull { it.id == jobId } ?: return
        job.events.firstOrNull { it.id == eventId }?.calendarEventId?.let { CalendarSync.delete(context, it) }
        updateJob(jobId) { j -> j.copy(events = j.events.filterNot { it.id == eventId }) }
    }

    /** Pushes one date to Google Calendar (insert or update). */
    fun syncEvent(jobId: String, eventId: String): String {
        val job = AppStorage.loadJobs(context).firstOrNull { it.id == jobId } ?: error("Job not found.")
        val event = job.events.firstOrNull { it.id == eventId } ?: error("Date not found.")
        return when (val result = CalendarSync.upsert(context, job, event)) {
            is CalendarSync.Result.Synced -> {
                updateJob(jobId) { j ->
                    j.copy(events = j.events.map { if (it.id == eventId) it.copy(calendarEventId = result.calendarEventId, calendarStale = false) else it })
                }
                "📅 ${event.type.label} on ${DATE_TIME_FMT.format(java.util.Date(event.startMillis))} is in Google Calendar."
            }
            is CalendarSync.Result.Failed -> error(result.reason)
        }
    }

    fun setNotes(jobId: String, notes: String) = updateJob(jobId) { it.copy(notes = notes) }

    private fun updateJob(jobId: String, transform: (Job) -> Job) {
        AppStorage.updateJobs(context) { jobs -> jobs.map { if (it.id == jobId) transform(it) else it } }
    }

    // ---- Companies -----------------------------------------------------------------

    fun saveCompany(company: Company) {
        AppStorage.updateCompanies(context) { list ->
            if (company.id.isBlank()) {
                val base = slug(company.name)
                var id = base
                var n = 2
                while (list.any { it.id == id }) id = "$base-${n++}"
                list + company.copy(id = id)
            } else {
                list.map { if (it.id == company.id) company else it }
            }
        }
    }

    fun deleteCompany(companyId: String) {
        AppStorage.updateCompanies(context) { list -> list.filterNot { it.id == companyId } }
        // Keep jobs the user is actively tracking; drop the rest.
        AppStorage.updateJobs(context) { jobs ->
            jobs.filterNot { it.companyId == companyId && it.status == JobStatus.NEW }
        }
    }

    companion object {
        private val DATE_FMT = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault())
        private val DATE_TIME_FMT = java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault())

        fun newEventId(): String = java.util.UUID.randomUUID().toString()

        private fun hostOf(url: String): String =
            runCatching { java.net.URI(url.trim()).host.orEmpty().removePrefix("www.") }.getOrDefault("")

        /** "careers.qualcomm.com" -> "qualcomm.com" (good enough for company matching). */
        private fun rootDomain(host: String) = host.split('.').takeLast(2).joinToString(".")

        /** "careers.qualcomm.com" -> "Qualcomm" -- a placeholder until the posting names the company. */
        private fun companyGuess(host: String) =
            rootDomain(host).substringBefore('.').replaceFirstChar { it.uppercase() }

        private const val DAY_MS = 24L * 3600 * 1000

        /**
         * Next Claude web-search batch. A top-choice (priority 1) company not searched in the
         * last 24h is searched alone, so it gets Claude's full attention; otherwise the
         * least-recently-searched other companies, [size] at a time, so the list rotates.
         */
        fun nextSearchBatch(companies: List<Company>, size: Int, now: Long): List<Company> {
            val top = companies.filter { it.priority == 1 }
                .filter { now - it.lastFetchedAt > DAY_MS }
                .minByOrNull { it.lastFetchedAt }
            if (top != null) return listOf(top)
            val rest = companies.filter { it.priority != 1 }.ifEmpty { companies }
            return rest.sortedWith(compareBy<Company>({ it.lastFetchedAt }, { it.priority }, { it.name }))
                .take(size.coerceAtLeast(1))
        }

        fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "company" }
        fun normalizeUrl(url: String) = url.trim().substringBefore('#').trimEnd('/').lowercase()
    }
}
