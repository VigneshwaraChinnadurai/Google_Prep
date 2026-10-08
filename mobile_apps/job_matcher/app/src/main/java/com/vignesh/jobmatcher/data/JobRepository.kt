package com.vignesh.jobmatcher.data

import android.content.Context
import com.vignesh.jobmatcher.claude.ClaudeResponseParser
import com.vignesh.jobmatcher.claude.PromptBuilder
import com.vignesh.jobmatcher.claude.ScoreUpdate
import com.vignesh.jobmatcher.matching.LocalScorer
import com.vignesh.jobmatcher.model.Company
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobStatus
import com.vignesh.jobmatcher.model.SourceType
import com.vignesh.jobmatcher.sources.JobFetcher
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
class JobRepository(private val context: Context, private val fetcher: JobFetcher = JobFetcher()) {

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
        val profile = AppStorage.loadProfile(context)
        val now = System.currentTimeMillis()
        val raw = runCatching {
            fetcher.fetch(
                company,
                profile.searchTerms,
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

    fun pendingScoring(): List<Job> {
        val prefilter = AppStorage.loadSettings(context).prefilterThreshold
        return AppStorage.loadJobs(context)
            .filter { !it.isScored && !it.closed && it.status != JobStatus.DISMISSED && it.localScore >= prefilter }
            .sortedByDescending { it.localScore }
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

    fun searchPrompt(): String? {
        val companies = searchCompanies()
        if (companies.isEmpty()) return null
        val names = companies.map { it.id }.toSet()
        val known = AppStorage.loadJobs(context).filter { it.companyId in names && !it.closed }
        return PromptBuilder.searchPrompt(
            AppStorage.loadResume(context), AppStorage.loadProfile(context), companies, known, AppStorage.loadSettings(context)
        )
    }

    /** Returns (added, updated). */
    fun applyFoundJobs(raw: String): Result<Pair<Int, Int>> = ClaudeResponseParser.parseFoundJobs(raw).mapCatching { found ->
        if (found.isEmpty()) error("Claude's reply contained no jobs with a valid URL.")
        val companies = AppStorage.loadCompanies(context)
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
        AppStorage.updateCompanies(context) { list ->
            list.map { if (it.source == SourceType.CLAUDE_SEARCH && it.enabled) it.copy(lastFetchedAt = now, lastError = "") else it }
        }
        added to updated
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

    fun setStatus(jobId: String, status: JobStatus) {
        val now = System.currentTimeMillis()
        updateJob(jobId) {
            it.copy(
                status = status, statusUpdatedAt = now,
                appliedAt = if (status == JobStatus.APPLIED && it.appliedAt == 0L) now else it.appliedAt
            )
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
        fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "company" }
        fun normalizeUrl(url: String) = url.trim().substringBefore('#').trimEnd('/').lowercase()
    }
}
