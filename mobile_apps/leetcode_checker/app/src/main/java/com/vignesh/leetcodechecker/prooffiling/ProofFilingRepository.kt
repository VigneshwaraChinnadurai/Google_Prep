package com.vignesh.leetcodechecker.prooffiling

import android.content.Context
import android.util.Base64
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.BuildConfig
import com.vignesh.leetcodechecker.R
import com.vignesh.leetcodechecker.api.GitHubGraphQLApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * ProofFilingRepository - Handles all data operations for ProofFiling feature
 * 
 * Responsibilities:
 * - Local storage of entries (JSON files)
 * - GitHub API for pushing markdown entries
 * - GitHub API for fetching commit stats
 * - LeetCode stats fetching
 * - Gemini LLM for summarization
 */
class ProofFilingRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "ProofFilingRepo"
        private const val ENTRIES_FILE = "prooffiling_entries.json"
        private const val CONFIG_FILE = "prooffiling_config.json"
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
    
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val entriesAdapter = moshi.adapter<List<ProofFilingEntry>>(
        Types.newParameterizedType(List::class.java, ProofFilingEntry::class.java)
    )
    
    private val configAdapter = moshi.adapter(ProofFilingConfig::class.java)
    private val entryAdapter = moshi.adapter(ProofFilingEntry::class.java)
    
    // ═══════════════════════════════════════════════════════════════════════
    // Local Storage Operations
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Load all saved entries from local storage
     */
    suspend fun loadEntries(): List<ProofFilingEntry> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, ENTRIES_FILE)
            if (!file.exists()) return@withContext emptyList()
            
            val json = file.readText()
            entriesAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load entries", e)
            emptyList()
        }
    }
    
    /**
     * Save all entries to local storage
     */
    suspend fun saveEntries(entries: List<ProofFilingEntry>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, ENTRIES_FILE)
            val json = entriesAdapter.toJson(entries)
            file.writeText(json)
            Log.d(TAG, "Saved ${entries.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save entries", e)
        }
    }
    
    /**
     * Save or update a single entry
     */
    suspend fun saveEntry(entry: ProofFilingEntry): ProofFilingEntry = withContext(Dispatchers.IO) {
        val entries = loadEntries().toMutableList()
        val index = entries.indexOfFirst { it.id == entry.id }
        
        val updatedEntry = entry.copy(
            lastUpdatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        
        if (index >= 0) {
            entries[index] = updatedEntry
        } else {
            entries.add(0, updatedEntry)
        }
        
        saveEntries(entries)
        updatedEntry
    }
    
    /**
     * Load configuration
     */
    suspend fun loadConfig(): ProofFilingConfig = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CONFIG_FILE)
            if (!file.exists()) return@withContext ProofFilingConfig()
            
            val json = file.readText()
            configAdapter.fromJson(json) ?: ProofFilingConfig()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config", e)
            ProofFilingConfig()
        }
    }
    
    /**
     * Save configuration
     */
    suspend fun saveConfig(config: ProofFilingConfig) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CONFIG_FILE)
            val json = configAdapter.toJson(config)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config", e)
        }
    }
    
    /**
     * Get or create entry for current week
     */
    suspend fun getCurrentWeekEntry(): ProofFilingEntry {
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        
        val entries = loadEntries()
        val existingEntry = entries.find { it.weekStartDate == weekStart.toString() }
        
        return existingEntry ?: ProofFilingEntry(
            weekStartDate = weekStart.toString(),
            weekEndDate = weekEnd.toString()
        )
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // GitHub Stats Fetching
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Fetch GitHub stats for the current week
     */
    suspend fun fetchGitHubStats(
        username: String = BuildConfig.GITHUB_OWNER,
        weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        weekEnd: LocalDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    ): Result<GitHubWeeklyStats> = withContext(Dispatchers.IO) {
        try {
            val settings = AppSettingsStore.load(context)
            val token = settings.globalGithubToken
            if (token.isBlank()) {
                return@withContext Result.failure(Exception("GitHub token not configured. Set it in Global Settings."))
            }

            // Fetch recent commits using GraphQL
            val commits = fetchRecentCommits(username, token, weekStart, weekEnd)
            
            // Fetch contribution count from contribution calendar
            val contributions = fetchContributionCount(username, token, weekStart, weekEnd)
            
            // Fetch PR and issue counts
            val prCount = fetchPRCount(username, token, weekStart, weekEnd)
            val issueCount = fetchIssueCount(username, token, weekStart, weekEnd)
            
            // Group commits by repo
            val reposContributed = commits.map { it.repoName }.distinct()
            
            val stats = GitHubWeeklyStats(
                username = username,
                periodStart = weekStart.toString(),
                periodEnd = weekEnd.toString(),
                totalCommits = commits.size,
                totalPRs = prCount,
                totalIssues = issueCount,
                reposContributed = reposContributed,
                commitMessages = commits,
                contributionCount = contributions
            )
            
            Result.success(stats)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch GitHub stats", e)
            Result.failure(e)
        }
    }
    
    /**
     * Commit messages the app itself auto-generates (daily revision pushes, weekly
     * ProofFiling pushes) -- these aren't real work and would otherwise dominate
     * the weekly summary since they fire on a schedule regardless of what else happened.
     */
    private val autoGeneratedCommitPatterns = listOf("leetcode qa revision", "prooffiling entry")

    private fun isAutoGeneratedCommit(message: String): Boolean {
        val firstLine = message.lineSequence().firstOrNull()?.lowercase().orEmpty()
        return autoGeneratedCommitPatterns.any { firstLine.contains(it) }
    }

    /**
     * List every repo owned by the authenticated user (not just one configured repo).
     */
    private suspend fun fetchOwnedRepoNames(username: String, token: String): List<String> = withContext(Dispatchers.IO) {
        val names = mutableListOf<String>()
        try {
            var page = 1
            while (true) {
                val url = "$GITHUB_API_BASE/user/repos?affiliation=owner&per_page=100&page=$page"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to list owned repos: HTTP ${response.code}")
                    break
                }
                val body = response.body?.string() ?: break
                val items = JSONArray(body)
                if (items.length() == 0) break

                for (i in 0 until items.length()) {
                    val repo = items.getJSONObject(i)
                    val ownerLogin = repo.optJSONObject("owner")?.optString("login").orEmpty()
                    if (ownerLogin.equals(username, ignoreCase = true)) {
                        names.add(repo.optString("name"))
                    }
                }
                if (items.length() < 100) break
                page++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list owned repos", e)
        }
        names
    }

    /**
     * Fetch this week's commits across every repo the user owns (not a search-index
     * approximation), authored by them, on that repo's default branch.
     */
    private suspend fun fetchRecentCommits(
        username: String,
        token: String,
        weekStart: LocalDate,
        weekEnd: LocalDate
    ): List<CommitInfo> = coroutineScope {
        val sinceIso = "${weekStart}T00:00:00Z"
        val untilIso = "${weekEnd}T23:59:59Z"

        val repos = fetchOwnedRepoNames(username, token)
        Log.d(TAG, "Scanning ${repos.size} owned repos for commits from $weekStart to $weekEnd")

        val perRepoResults = repos.map { repo ->
            async(Dispatchers.IO) {
                fetchCommitsForRepo(username, repo, token, sinceIso, untilIso)
            }
        }.awaitAll()

        val commits = perRepoResults.flatten().sortedByDescending { it.date }
        Log.d(TAG, "Total ${commits.size} commits retrieved across ${repos.size} repos (auto-generated commits excluded)")
        commits
    }

    private fun fetchCommitsForRepo(
        username: String,
        repo: String,
        token: String,
        sinceIso: String,
        untilIso: String
    ): List<CommitInfo> {
        val commits = mutableListOf<CommitInfo>()
        try {
            val url = "$GITHUB_API_BASE/repos/$username/$repo/commits" +
                "?author=$username&since=$sinceIso&until=$untilIso&per_page=100"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                // 409 = empty repo (no commits yet); not worth logging as an error.
                if (response.code != 409) {
                    Log.e(TAG, "Commit fetch failed for $repo: HTTP ${response.code}")
                }
                return commits
            }

            val body = response.body?.string() ?: return commits
            val items = JSONArray(body)
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val commitObj = item.optJSONObject("commit")
                val message = commitObj?.optString("message", "") ?: ""
                if (isAutoGeneratedCommit(message)) continue

                commits.add(
                    CommitInfo(
                        sha = item.optString("sha", ""),
                        message = message,
                        repoName = repo,
                        date = commitObj?.optJSONObject("committer")?.optString("date", "") ?: "",
                        url = item.optString("html_url", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch commits for $repo", e)
        }
        return commits
    }
    
    private suspend fun fetchContributionCount(
        username: String,
        token: String,
        weekStart: LocalDate,
        weekEnd: LocalDate
    ): Int = withContext(Dispatchers.IO) {
        try {
            // Use GraphQL to fetch contribution calendar
            val query = """
                query {
                  user(login: "$username") {
                    contributionsCollection(from: "${weekStart}T00:00:00Z", to: "${weekEnd}T23:59:59Z") {
                      contributionCalendar {
                        totalContributions
                      }
                    }
                  }
                }
            """.trimIndent()
            
            val requestBody = JSONObject().apply {
                put("query", query)
            }.toString()
            
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/graphql")
                .header("Authorization", "Bearer $token")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext 0
                val json = JSONObject(body)
                json.optJSONObject("data")
                    ?.optJSONObject("user")
                    ?.optJSONObject("contributionsCollection")
                    ?.optJSONObject("contributionCalendar")
                    ?.optInt("totalContributions", 0) ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch contribution count", e)
            0
        }
    }
    
    private suspend fun fetchPRCount(
        username: String,
        token: String,
        weekStart: LocalDate,
        weekEnd: LocalDate
    ): Int = withContext(Dispatchers.IO) {
        try {
            val query = java.net.URLEncoder.encode(
                "author:$username created:${weekStart}..${weekEnd} type:pr",
                "UTF-8"
            )
            val url = "$GITHUB_API_BASE/search/issues?q=$query"
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext 0
                val json = JSONObject(body)
                json.optInt("total_count", 0)
            } else {
                Log.e(TAG, "PR search failed: ${response.code}")
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch PR count", e)
            0
        }
    }
    
    private suspend fun fetchIssueCount(
        username: String,
        token: String,
        weekStart: LocalDate,
        weekEnd: LocalDate
    ): Int = withContext(Dispatchers.IO) {
        try {
            val query = java.net.URLEncoder.encode(
                "author:$username created:${weekStart}..${weekEnd} type:issue",
                "UTF-8"
            )
            val url = "$GITHUB_API_BASE/search/issues?q=$query"
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext 0
                val json = JSONObject(body)
                json.optInt("total_count", 0)
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch issue count", e)
            0
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // LeetCode Stats Fetching
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Fetch LeetCode stats (total solved count)
     */
    suspend fun fetchLeetCodeStats(
        username: String,
        previousTotal: Int = 0
    ): Result<LeetCodeWeeklyStats> = withContext(Dispatchers.IO) {
        try {
            // Use LeetCode GraphQL API
            val query = """
                query getUserProfile(${"$"}username: String!) {
                  matchedUser(username: ${"$"}username) {
                    submitStatsGlobal {
                      acSubmissionNum {
                        difficulty
                        count
                      }
                    }
                  }
                }
            """.trimIndent()
            
            val requestBody = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().put("username", username))
            }.toString()
            
            val request = Request.Builder()
                .url("https://leetcode.com/graphql")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val json = JSONObject(body)
                
                val submissions = json.optJSONObject("data")
                    ?.optJSONObject("matchedUser")
                    ?.optJSONObject("submitStatsGlobal")
                    ?.optJSONArray("acSubmissionNum")
                
                var total = 0
                var easy = 0
                var medium = 0
                var hard = 0
                
                submissions?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val difficulty = item.optString("difficulty", "")
                        val count = item.optInt("count", 0)
                        
                        when (difficulty) {
                            "All" -> total = count
                            "Easy" -> easy = count
                            "Medium" -> medium = count
                            "Hard" -> hard = count
                        }
                    }
                }
                
                val today = LocalDate.now()
                val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                
                Result.success(LeetCodeWeeklyStats(
                    username = username,
                    periodStart = weekStart.toString(),
                    periodEnd = weekEnd.toString(),
                    previousTotalSolved = previousTotal,
                    currentTotalSolved = total,
                    problemsSolvedThisWeek = total - previousTotal,
                    easyDelta = easy,
                    mediumDelta = medium,
                    hardDelta = hard
                ))
            } else {
                Result.failure(Exception("LeetCode API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch LeetCode stats", e)
            Result.failure(e)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // LLM Summarization (Gemini)
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Generate a summary of GitHub commits using Gemini
     */
    suspend fun generateGitHubSummary(commits: List<CommitInfo>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val settings = AppSettingsStore.load(context)
            val apiKey = settings.globalGeminiApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("Gemini API key not configured"))
            }
            
            if (commits.isEmpty()) {
                return@withContext Result.success("No commits this week.")
            }
            
            Log.d(TAG, "Generating GitHub summary for ${commits.size} commits")
            
            val commitList = commits.take(20).joinToString("\n") { commit ->
                "- [${commit.repoName}] ${commit.message.lines().first()}"
            }
            
            val prompt = """
                You are analyzing a developer's weekly GitHub activity for their career progress journal.
                
                Here are their commits from this week:
                $commitList
                
                Please provide a concise summary (2-3 sentences) highlighting:
                1. Main themes/areas of work
                2. Any notable achievements or patterns
                3. Skills being developed
                
                Keep it professional but encouraging. This will help them in behavioral interviews and career tracking.
            """.trimIndent()
            
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 256)
                })
            }.toString()
            
            val url = "$GEMINI_API_BASE/models/gemini-2.5-flash:generateContent?key=$apiKey"
            Log.d(TAG, "Calling Gemini API at: ${url.substringBefore("?")}")
            
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Gemini response code: ${response.code}")
            
            if (response.isSuccessful) {
                if (responseBody.isNullOrBlank()) {
                    Log.e(TAG, "Empty response body from Gemini")
                    return@withContext Result.failure(Exception("Empty response from Gemini"))
                }
                
                Log.d(TAG, "Gemini response: ${responseBody.take(500)}")
                val json = JSONObject(responseBody)
                
                // Check for error in response
                if (json.has("error")) {
                    val error = json.optJSONObject("error")
                    val errorMsg = error?.optString("message", "Unknown error") ?: "Unknown error"
                    Log.e(TAG, "Gemini API error: $errorMsg")
                    return@withContext Result.failure(Exception("Gemini error: $errorMsg"))
                }
                
                val text = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                
                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "Generated summary: ${text.take(100)}...")
                    Result.success(text.trim())
                } else {
                    Log.e(TAG, "Failed to parse Gemini response - no text found")
                    Log.e(TAG, "Full response: $responseBody")
                    Result.failure(Exception("Failed to parse Gemini response"))
                }
            } else {
                Log.e(TAG, "Gemini API error ${response.code}: $responseBody")
                Result.failure(Exception("Gemini API error: ${response.code} - ${responseBody?.take(200)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate summary", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generate a weekly summary of all activity
     */
    suspend fun generateWeeklySummary(entry: ProofFilingEntry): Result<String> = withContext(Dispatchers.IO) {
        try {
            val settings = AppSettingsStore.load(context)
            val apiKey = settings.globalGeminiApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("Gemini API key not configured"))
            }
            
            Log.d(TAG, "Generating weekly summary for ${entry.weekStartDate} to ${entry.weekEndDate}")
            
            val winsText = if (entry.wins.isEmpty()) "None recorded" else entry.wins.joinToString("\n") { "- ${it.description}" }
            val learningsText = if (entry.learnings.isEmpty()) "None recorded" else entry.learnings.joinToString("\n") { "- ${it.description}" }
            val evidenceText = if (entry.evidence.isEmpty()) "None recorded" else entry.evidence.joinToString("\n") { "- ${it.description}" }
            val githubText = entry.githubStats?.let {
                "GitHub: ${it.totalCommits} commits, ${it.totalPRs} PRs across ${it.reposContributed.size} repos"
            } ?: "GitHub: Not fetched"
            val leetcodeText = entry.leetcodeStats?.let {
                "LeetCode: Solved ${it.problemsSolvedThisWeek} problems"
            } ?: "LeetCode: Not fetched"
            
            val prompt = """
                Generate a professional weekly summary for a career progress journal entry.
                
                Week: ${entry.weekStartDate} to ${entry.weekEndDate}
                
                WINS:
                $winsText
                
                LEARNINGS:
                $learningsText
                
                EVIDENCE:
                $evidenceText
                
                METRICS:
                $githubText
                $leetcodeText
                
                Write a 3-4 sentence summary that:
                1. Highlights the most impressive accomplishment
                2. Notes key learning themes
                3. Quantifies progress where possible
                4. Ends with an encouraging note about career growth
                
                Keep it professional and suitable for sharing on LinkedIn or in interviews.
            """.trimIndent()
            
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 512)
                })
            }.toString()
            
            val url = "$GEMINI_API_BASE/models/gemini-2.5-flash:generateContent?key=$apiKey"
            Log.d(TAG, "Calling Gemini for weekly summary")
            
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Gemini weekly summary response code: ${response.code}")
            
            if (response.isSuccessful) {
                if (responseBody.isNullOrBlank()) {
                    Log.e(TAG, "Empty response body from Gemini for weekly summary")
                    return@withContext Result.failure(Exception("Empty response from Gemini"))
                }
                
                val json = JSONObject(responseBody)
                
                // Check for error in response
                if (json.has("error")) {
                    val error = json.optJSONObject("error")
                    val errorMsg = error?.optString("message", "Unknown error") ?: "Unknown error"
                    Log.e(TAG, "Gemini API error for weekly summary: $errorMsg")
                    return@withContext Result.failure(Exception("Gemini error: $errorMsg"))
                }
                
                val text = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                
                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "Generated weekly summary successfully")
                    Result.success(text.trim())
                } else {
                    Log.e(TAG, "Failed to parse Gemini weekly summary response")
                    Log.e(TAG, "Response: $responseBody")
                    Result.failure(Exception("Failed to parse Gemini response"))
                }
            } else {
                Log.e(TAG, "Gemini API error for weekly summary: ${response.code} - $responseBody")
                Result.failure(Exception("Gemini API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate weekly summary", e)
            Result.failure(e)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // GitHub Push Operations
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Push entry to GitHub as markdown file
     */
    suspend fun pushToGitHub(entry: ProofFilingEntry): Result<ProofFilingEntry> = withContext(Dispatchers.IO) {
        try {
            val settings = AppSettingsStore.load(context)
            val token = settings.globalGithubToken
            if (token.isBlank()) {
                return@withContext Result.failure(Exception("GitHub token not configured. Set it in Global Settings."))
            }

            val config = loadConfig()
            val owner = BuildConfig.GITHUB_OWNER
            val repo = config.targetRepo
            val branch = config.targetBranch
            
            // Generate filename: entries/2026-W25.md
            val weekNumber = LocalDate.parse(entry.weekStartDate).get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val year = LocalDate.parse(entry.weekStartDate).year
            val filename = "entries/${year}-W${weekNumber.toString().padStart(2, '0')}.md"
            
            // Generate markdown content
            val markdown = entry.toMarkdown()
            val contentBase64 = Base64.encodeToString(markdown.toByteArray(), Base64.NO_WRAP)
            
            // Check if file exists to get SHA for update
            val existingSha = getFileSha(owner, repo, filename, token)
            
            val requestBody = if (existingSha != null) {
                JSONObject().apply {
                    put("message", "Update ProofFiling entry for Week $weekNumber, $year")
                    put("content", contentBase64)
                    put("sha", existingSha)
                    put("branch", branch)
                }.toString()
            } else {
                JSONObject().apply {
                    put("message", "Add ProofFiling entry for Week $weekNumber, $year")
                    put("content", contentBase64)
                    put("branch", branch)
                }.toString()
            }
            
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/contents/$filename")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .put(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val json = JSONObject(body)
                
                val commitSha = json.optJSONObject("commit")?.optString("sha", "")
                
                val updatedEntry = entry.copy(
                    isPushedToGitHub = true,
                    pushedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    commitSha = commitSha
                )
                
                // Save updated entry
                saveEntry(updatedEntry)
                
                Result.success(updatedEntry)
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "GitHub push failed: ${response.code} - $errorBody")
                Result.failure(Exception("GitHub push failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push to GitHub", e)
            Result.failure(e)
        }
    }
    
    private suspend fun getFileSha(
        owner: String,
        repo: String,
        path: String,
        token: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/contents/$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                json.optString("sha", null)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Auto-capture Learnings from Commits
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Extract potential learning items from commit messages
     */
    fun extractLearningsFromCommits(commits: List<CommitInfo>): List<LearningItem> {
        val learnings = mutableListOf<LearningItem>()
        
        val learningKeywords = listOf(
            "fix", "refactor", "optimize", "improve", "add", "implement",
            "learn", "discover", "found", "solved", "debug"
        )
        
        commits.forEach { commit ->
            val message = commit.message.lowercase()
            val firstLine = commit.message.lines().first()
            
            // Check if commit message contains learning indicators
            val hasLearningIndicator = learningKeywords.any { keyword ->
                message.contains(keyword)
            }
            
            if (hasLearningIndicator && firstLine.length > 10) {
                // Categorize based on keywords
                val category = when {
                    message.contains("algorithm") || message.contains("dsa") || 
                    message.contains("leetcode") -> LearningCategory.DSA
                    message.contains("design") || message.contains("architecture") -> LearningCategory.SYSTEM_DESIGN
                    message.contains("pattern") -> LearningCategory.PATTERN
                    message.contains("debug") || message.contains("fix") -> LearningCategory.DEBUGGING
                    message.contains("mistake") || message.contains("lesson") -> LearningCategory.MISTAKE_LESSON
                    else -> LearningCategory.OTHER
                }
                
                learnings.add(LearningItem(
                    description = "From ${commit.repoName}: $firstLine",
                    source = LearningSource.GITHUB_COMMIT,
                    category = category,
                    relatedCommitUrl = commit.url
                ))
            }
        }
        
        return learnings.take(10) // Limit suggestions
    }
}
