package com.vc.jobfinder.data

import com.vc.jobfinder.data.db.*
import com.vc.jobfinder.data.local.CompaniesLoader
import com.vc.jobfinder.data.local.SettingsStore
import com.vc.jobfinder.data.remote.ats.AtsRegistry
import com.vc.jobfinder.data.remote.contests.ContestRegistry
import com.vc.jobfinder.domain.*
import com.vc.jobfinder.llm.CoverLetterGenerator
import com.vc.jobfinder.llm.Matcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device replacement for the FastAPI orchestrator. All state lives in Room.
 * Long-running operations expose progress via Flow.
 */
@Singleton
class JobFinderRepository @Inject constructor(
    private val db: AppDatabase,
    private val companies: CompaniesLoader,
    private val ats: AtsRegistry,
    private val contests: ContestRegistry,
    private val matcher: Matcher,
    private val coverLetters: CoverLetterGenerator,
    private val settings: SettingsStore,
) {
    // ---- Observed state ----
    val resumeFlow: Flow<Resume?> = db.resumeDao().observe().map { e ->
        e?.let {
            Resume(
                firstName = it.firstName, lastName = it.lastName,
                email = it.email, phone = it.phone,
                linkedinUrl = it.linkedinUrl, githubUrl = it.githubUrl,
                portfolioUrl = it.portfolioUrl,
                topSkills = emptyList(),
                rawText = it.rawText,
            )
        }
    }

    val jobCount: Flow<Int> = db.jobDao().count()

    fun matchesFlow(minScore: Double, limit: Int = 200): Flow<List<MatchResult>> =
        db.matchDao().observe(minScore, limit).map { list -> list.map { it.toDomain() } }

    fun competitionsFlow(trackedOnly: Boolean, hiringOnly: Boolean, limit: Int = 200): Flow<List<Competition>> =
        db.competitionDao().observe(trackedOnly, hiringOnly, limit).map { list ->
            list.mapNotNull { e ->
                Platform.fromKey(e.platformKey)?.let { p ->
                    Competition(
                        id = e.id, platform = p, title = e.title,
                        sponsorCompany = e.sponsorCompany, matchedCompany = e.matchedCompany,
                        isTrackedCompany = e.isTrackedCompany, isHiring = e.isHiring,
                        startsAt = e.startsAt, endsAt = e.endsAt,
                        registrationUrl = e.registrationUrl, prizePool = e.prizePool,
                        descriptionSnippet = e.descriptionSnippet,
                    )
                }
            }
        }

    // ---- Resume ----
    suspend fun saveResume(r: Resume) {
        db.resumeDao().upsert(ResumeEntity(
            firstName = r.firstName, lastName = r.lastName, email = r.email,
            phone = r.phone, linkedinUrl = r.linkedinUrl,
            githubUrl = r.githubUrl, portfolioUrl = r.portfolioUrl,
            rawText = r.rawText, updatedAt = System.currentTimeMillis(),
        ))
    }

    // ---- Scrape ----
    sealed interface ScrapeEvent {
        data class Progress(val company: String, val cumulative: Int, val target: Int) : ScrapeEvent
        data class Done(val total: Int) : ScrapeEvent
        data class Error(val message: String) : ScrapeEvent
    }

    suspend fun runScrape(maxConcurrent: Int = 4, onEvent: suspend (ScrapeEvent) -> Unit) =
        coroutineScope {
            val list = companies.companies
            val target = list.size
            val sem = Semaphore(maxConcurrent)
            var cumulative = 0
            val mutex = Mutex()
            val collected = mutableListOf<Job>()

            try {
                val jobs = list.map { company ->
                    async {
                        sem.withPermit {
                            val adapter = ats.adapterFor(company.ats)
                                ?: return@withPermit Pair(company.name, emptyList<Job>())
                            val fetched = runCatching { adapter.fetch(company) }.getOrDefault(emptyList())
                            mutex.withLock {
                                collected += fetched
                                cumulative += fetched.size
                                onEvent(ScrapeEvent.Progress(company.name, cumulative, target))
                            }
                            company.name to fetched
                        }
                    }
                }
                jobs.awaitAll()
                val deduped = dedupe(collected)
                db.jobDao().clear()
                db.jobDao().upsertAll(deduped.map { it.toEntity() })
                onEvent(ScrapeEvent.Done(deduped.size))
            } catch (t: Throwable) {
                onEvent(ScrapeEvent.Error(t.message ?: "Scrape failed"))
            }
        }

    // ---- Match ----
    sealed interface MatchEvent {
        data class Progress(val done: Int, val total: Int) : MatchEvent
        data class Done(val total: Int) : MatchEvent
        data class Error(val message: String) : MatchEvent
    }

    suspend fun runMatch(maxConcurrent: Int = 4, onEvent: suspend (MatchEvent) -> Unit) =
        coroutineScope {
            val resume = currentResume() ?: run {
                onEvent(MatchEvent.Error("Upload a resume first"))
                return@coroutineScope
            }
            val jobs = db.jobDao().all()
            if (jobs.isEmpty()) {
                onEvent(MatchEvent.Error("No jobs cached. Run scrape first."))
                return@coroutineScope
            }

            val sem = Semaphore(maxConcurrent)
            var done = 0
            val mutex = Mutex()
            val model = settings.settings.firstOrNull()?.llmModel ?: "gemini-2.5-flash"

            try {
                val tasks = jobs.map { jobEntity ->
                    async {
                        sem.withPermit {
                            val job = jobEntity.toDomain()
                            val previousStatus = db.matchDao().getStatus(job.id)
                            val result = runCatching { matcher.score(resume, job, model) }.getOrNull()
                            if (result != null) {
                                val status = if (previousStatus != null && previousStatus != "new") previousStatus
                                             else result.status
                                db.matchDao().upsert(result.copy(status = status).toEntity())
                            }
                            mutex.withLock {
                                done += 1
                                onEvent(MatchEvent.Progress(done, jobs.size))
                            }
                        }
                    }
                }
                tasks.awaitAll()
                onEvent(MatchEvent.Done(jobs.size))
            } catch (t: Throwable) {
                onEvent(MatchEvent.Error(t.message ?: "Match failed"))
            }
        }

    suspend fun setMatchStatus(id: String, status: String) {
        db.matchDao().setStatus(id, status)
    }

    suspend fun buildApplyPacket(matchId: String): ApplyPacket? {
        val m = db.matchDao().get(matchId) ?: return null
        val r = currentResume() ?: return null
        val match = m.toDomain()
        val model = settings.settings.firstOrNull()?.llmModel ?: "gemini-2.5-flash"
        return coverLetters.build(r, match, model)
    }

    // ---- Contests ----
    sealed interface ContestEvent {
        data class Progress(val platform: String, val count: Int, val totalPlatforms: Int) : ContestEvent
        data class Done(val total: Int) : ContestEvent
        data class Error(val message: String) : ContestEvent
    }

    suspend fun refreshContests(onEvent: suspend (ContestEvent) -> Unit) = coroutineScope {
        val adapters = contests.all
        val totalPlatforms = adapters.size
        val collected = mutableListOf<Competition>()
        val mutex = Mutex()
        var platformsDone = 0

        try {
            adapters.map { adapter ->
                async {
                    val list = runCatching { adapter.fetch() }.getOrDefault(emptyList())
                    mutex.withLock {
                        collected += list
                        platformsDone += 1
                        val platformName = list.firstOrNull()?.platform?.displayName ?: "Platform"
                        onEvent(ContestEvent.Progress(platformName, list.size, totalPlatforms))
                    }
                }
            }.awaitAll()

            db.competitionDao().clear()
            db.competitionDao().upsertAll(collected.map { it.toEntity() })
            onEvent(ContestEvent.Done(collected.size))
        } catch (t: Throwable) {
            onEvent(ContestEvent.Error(t.message ?: "Contest fetch failed"))
        }
    }

    // ---- Helpers ----
    private suspend fun currentResume(): Resume? {
        val e = db.resumeDao().get() ?: return null
        return Resume(
            firstName = e.firstName, lastName = e.lastName,
            email = e.email, phone = e.phone,
            linkedinUrl = e.linkedinUrl, githubUrl = e.githubUrl,
            portfolioUrl = e.portfolioUrl,
            topSkills = emptyList(),
            rawText = e.rawText,
        )
    }

    private fun dedupe(jobs: List<Job>): List<Job> {
        val seen = mutableSetOf<Triple<String, String, String>>()
        return jobs.filter { j ->
            val key = Triple(j.company.lowercase(), j.title.lowercase(), j.applyUrl)
            if (key in seen) false else { seen.add(key); true }
        }
    }
}

// --- mappers ---
private fun Job.toEntity() = JobEntity(
    id = id, title = title, company = company, location = location,
    applyUrl = applyUrl, description = description, postedAt = postedAt,
    fetchedAt = System.currentTimeMillis(),
)

private fun JobEntity.toDomain() = Job(
    id = id, title = title, company = company, location = location,
    applyUrl = applyUrl, description = description, postedAt = postedAt,
)

private fun MatchResult.toEntity() = MatchEntity(
    id = id,
    jobTitle = job.title, jobCompany = job.company, jobLocation = job.location,
    jobApplyUrl = job.applyUrl, jobDescription = job.description,
    fitScore = fitScore, fitReasoning = fitReasoning,
    matchedSkillsCsv = matchedSkills.joinToString("|"),
    gapSkillsCsv = gapSkills.joinToString("|"),
    status = status,
    scoredAt = System.currentTimeMillis(),
)

private fun MatchEntity.toDomain() = MatchResult(
    id = id,
    job = Job(
        id = id, title = jobTitle, company = jobCompany, location = jobLocation,
        applyUrl = jobApplyUrl, description = jobDescription,
    ),
    fitScore = fitScore,
    fitReasoning = fitReasoning,
    matchedSkills = matchedSkillsCsv.split('|').filter { it.isNotBlank() },
    gapSkills = gapSkillsCsv.split('|').filter { it.isNotBlank() },
    status = status,
)

private fun Competition.toEntity() = CompetitionEntity(
    id = id, platformKey = platform.key, title = title,
    sponsorCompany = sponsorCompany, matchedCompany = matchedCompany,
    isTrackedCompany = isTrackedCompany, isHiring = isHiring,
    startsAt = startsAt, endsAt = endsAt,
    registrationUrl = registrationUrl, prizePool = prizePool,
    descriptionSnippet = descriptionSnippet,
    fetchedAt = System.currentTimeMillis(),
)
