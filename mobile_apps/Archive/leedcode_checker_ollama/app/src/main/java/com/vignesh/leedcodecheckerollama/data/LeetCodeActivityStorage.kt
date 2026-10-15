package com.vignesh.leedcodecheckerollama.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Storage system for LeetCode activity tracking, goals, gamification, and analytics
 */
object LeetCodeActivityStorage {
    private const val PREFS = "leetcode_activity_prefs"
    
    // Keys for different data types
    private const val KEY_COMPLETION_HISTORY = "completion_history_json"
    private const val KEY_PROBLEM_STATS = "problem_stats_json"
    private const val KEY_GOALS = "goals_json"
    private const val KEY_ACHIEVEMENTS = "achievements_json"
    private const val KEY_FLASHCARDS = "flashcards_json"
    private const val KEY_FOCUS_SESSIONS = "focus_sessions_json"
    private const val KEY_USER_PROFILE = "user_profile_json"
    private const val KEY_LEADERBOARD = "leaderboard_json"
    
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    
    private fun istDateKey(date: Date = Date()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return formatter.format(date)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // COMPLETION HISTORY (for Heatmap)
    // ═══════════════════════════════════════════════════════════════
    
    data class CompletionEntry(
        val date: String,
        val problemId: String,
        val problemTitle: String,
        val difficulty: String,
        val topics: List<String>,
        val timeTakenMinutes: Int = 0,
        val usedHint: Boolean = false,
        val solvedWithAi: Boolean = false
    )
    
    fun saveCompletion(context: Context, entry: CompletionEntry) {
        val history = loadCompletionHistory(context).toMutableList()
        // Remove existing entry for same date + problem
        history.removeAll { it.date == entry.date && it.problemId == entry.problemId }
        history.add(entry)
        
        val jsonArray = JSONArray()
        history.forEach { e ->
            jsonArray.put(JSONObject().apply {
                put("date", e.date)
                put("problemId", e.problemId)
                put("problemTitle", e.problemTitle)
                put("difficulty", e.difficulty)
                put("topics", JSONArray(e.topics))
                put("timeTakenMinutes", e.timeTakenMinutes)
                put("usedHint", e.usedHint)
                put("solvedWithAi", e.solvedWithAi)
            })
        }
        prefs(context).edit().putString(KEY_COMPLETION_HISTORY, jsonArray.toString()).apply()
        
        // Update stats and achievements
        updateProblemStats(context)
        checkAndAwardAchievements(context)
    }
    
    fun loadCompletionHistory(context: Context): List<CompletionEntry> {
        val raw = prefs(context).getString(KEY_COMPLETION_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CompletionEntry(
                    date = obj.getString("date"),
                    problemId = obj.getString("problemId"),
                    problemTitle = obj.getString("problemTitle"),
                    difficulty = obj.getString("difficulty"),
                    topics = (0 until obj.getJSONArray("topics").length()).map { 
                        obj.getJSONArray("topics").getString(it) 
                    },
                    timeTakenMinutes = obj.optInt("timeTakenMinutes", 0),
                    usedHint = obj.optBoolean("usedHint", false),
                    solvedWithAi = obj.optBoolean("solvedWithAi", false)
                )
            }
        }.getOrElse { emptyList() }
    }
    
    fun getCompletionsByDate(context: Context): Map<String, List<CompletionEntry>> {
        return loadCompletionHistory(context).groupBy { it.date }
    }
    
    fun getContributionCountByDate(context: Context): Map<String, Int> {
        return loadCompletionHistory(context).groupBy { it.date }.mapValues { it.value.size }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // PROBLEM STATS (for Analytics)
    // ═══════════════════════════════════════════════════════════════
    
    data class ProblemStats(
        val totalSolved: Int = 0,
        val easySolved: Int = 0,
        val mediumSolved: Int = 0,
        val hardSolved: Int = 0,
        val currentStreak: Int = 0,
        val longestStreak: Int = 0,
        val totalTimeMinutes: Int = 0,
        val averageTimeMinutes: Int = 0,
        val topicDistribution: Map<String, Int> = emptyMap(),
        val weeklyAverage: Float = 0f,
        val monthlyTotal: Int = 0,
        val lastActivityDate: String = ""
    )
    
    private fun updateProblemStats(context: Context) {
        val history = loadCompletionHistory(context)
        if (history.isEmpty()) return
        
        val uniqueByProblem = history.distinctBy { it.problemId }
        
        // Calculate streaks
        val dateSet = history.map { it.date }.toSet().sorted()
        val (currentStreak, longestStreak) = calculateStreaks(dateSet)
        
        // Topic distribution
        val topicDist = mutableMapOf<String, Int>()
        uniqueByProblem.forEach { entry ->
            entry.topics.forEach { topic ->
                topicDist[topic] = (topicDist[topic] ?: 0) + 1
            }
        }
        
        // Time stats
        val totalTime = history.sumOf { it.timeTakenMinutes }
        val avgTime = if (history.isNotEmpty()) totalTime / history.size else 0
        
        // Weekly/Monthly
        val now = Calendar.getInstance()
        val oneWeekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        val oneMonthAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val weeklyCount = history.count { 
            runCatching { fmt.parse(it.date)?.after(oneWeekAgo.time) }.getOrElse { false } == true
        }
        val monthlyCount = history.count { 
            runCatching { fmt.parse(it.date)?.after(oneMonthAgo.time) }.getOrElse { false } == true
        }
        
        val stats = ProblemStats(
            totalSolved = uniqueByProblem.size,
            easySolved = uniqueByProblem.count { it.difficulty.equals("Easy", ignoreCase = true) },
            mediumSolved = uniqueByProblem.count { it.difficulty.equals("Medium", ignoreCase = true) },
            hardSolved = uniqueByProblem.count { it.difficulty.equals("Hard", ignoreCase = true) },
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalTimeMinutes = totalTime,
            averageTimeMinutes = avgTime,
            topicDistribution = topicDist,
            weeklyAverage = weeklyCount / 7f,
            monthlyTotal = monthlyCount,
            lastActivityDate = history.maxByOrNull { it.date }?.date ?: ""
        )
        
        val json = JSONObject().apply {
            put("totalSolved", stats.totalSolved)
            put("easySolved", stats.easySolved)
            put("mediumSolved", stats.mediumSolved)
            put("hardSolved", stats.hardSolved)
            put("currentStreak", stats.currentStreak)
            put("longestStreak", stats.longestStreak)
            put("totalTimeMinutes", stats.totalTimeMinutes)
            put("averageTimeMinutes", stats.averageTimeMinutes)
            put("topicDistribution", JSONObject(stats.topicDistribution.mapValues { it.value }))
            put("weeklyAverage", stats.weeklyAverage)
            put("monthlyTotal", stats.monthlyTotal)
            put("lastActivityDate", stats.lastActivityDate)
        }
        prefs(context).edit().putString(KEY_PROBLEM_STATS, json.toString()).apply()
    }
    
    fun loadProblemStats(context: Context): ProblemStats {
        val raw = prefs(context).getString(KEY_PROBLEM_STATS, null) ?: return ProblemStats()
        return runCatching {
            val json = JSONObject(raw)
            val topicObj = json.optJSONObject("topicDistribution")
            val topicMap = mutableMapOf<String, Int>()
            topicObj?.keys()?.forEach { key ->
                topicMap[key] = topicObj.optInt(key, 0)
            }
            ProblemStats(
                totalSolved = json.optInt("totalSolved", 0),
                easySolved = json.optInt("easySolved", 0),
                mediumSolved = json.optInt("mediumSolved", 0),
                hardSolved = json.optInt("hardSolved", 0),
                currentStreak = json.optInt("currentStreak", 0),
                longestStreak = json.optInt("longestStreak", 0),
                totalTimeMinutes = json.optInt("totalTimeMinutes", 0),
                averageTimeMinutes = json.optInt("averageTimeMinutes", 0),
                topicDistribution = topicMap,
                weeklyAverage = json.optDouble("weeklyAverage", 0.0).toFloat(),
                monthlyTotal = json.optInt("monthlyTotal", 0),
                lastActivityDate = json.optString("lastActivityDate", "")
            )
        }.getOrElse { ProblemStats() }
    }
    
    private fun calculateStreaks(sortedDates: List<String>): Pair<Int, Int> {
        if (sortedDates.isEmpty()) return Pair(0, 0)
        
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        
        var currentStreak = 0
        var longestStreak = 0
        var tempStreak = 1
        
        val today = istDateKey()
        val yesterday = istDateKey(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))
        
        // Calculate longest streak
        for (i in 1 until sortedDates.size) {
            val prev = fmt.parse(sortedDates[i - 1])
            val curr = fmt.parse(sortedDates[i])
            if (prev != null && curr != null) {
                val diffDays = ((curr.time - prev.time) / (24 * 60 * 60 * 1000)).toInt()
                if (diffDays == 1) {
                    tempStreak++
                } else {
                    longestStreak = maxOf(longestStreak, tempStreak)
                    tempStreak = 1
                }
            }
        }
        longestStreak = maxOf(longestStreak, tempStreak)
        
        // Calculate current streak (must include today or yesterday)
        val reversedDates = sortedDates.reversed()
        if (reversedDates.isNotEmpty() && (reversedDates[0] == today || reversedDates[0] == yesterday)) {
            currentStreak = 1
            for (i in 1 until reversedDates.size) {
                val curr = fmt.parse(reversedDates[i - 1])
                val prev = fmt.parse(reversedDates[i])
                if (curr != null && prev != null) {
                    val diffDays = ((curr.time - prev.time) / (24 * 60 * 60 * 1000)).toInt()
                    if (diffDays == 1) {
                        currentStreak++
                    } else {
                        break
                    }
                }
            }
        }
        
        return Pair(currentStreak, longestStreak)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // GOALS
    // ═══════════════════════════════════════════════════════════════
    
    data class Goal(
        val id: String = UUID.randomUUID().toString(),
        val type: GoalType,
        val target: Int,
        val current: Int = 0,
        val startDate: String,
        val endDate: String,
        val isCompleted: Boolean = false
    )
    
    enum class GoalType {
        DAILY_PROBLEMS,
        WEEKLY_PROBLEMS,
        MONTHLY_PROBLEMS,
        STREAK_DAYS,
        TOPIC_MASTERY,
        DIFFICULTY_CHALLENGE
    }
    
    fun saveGoal(context: Context, goal: Goal) {
        val goals = loadGoals(context).toMutableList()
        goals.removeAll { it.id == goal.id }
        goals.add(goal)
        
        val jsonArray = JSONArray()
        goals.forEach { g ->
            jsonArray.put(JSONObject().apply {
                put("id", g.id)
                put("type", g.type.name)
                put("target", g.target)
                put("current", g.current)
                put("startDate", g.startDate)
                put("endDate", g.endDate)
                put("isCompleted", g.isCompleted)
            })
        }
        prefs(context).edit().putString(KEY_GOALS, jsonArray.toString()).apply()
    }
    
    fun loadGoals(context: Context): List<Goal> {
        val raw = prefs(context).getString(KEY_GOALS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Goal(
                    id = obj.getString("id"),
                    type = GoalType.valueOf(obj.getString("type")),
                    target = obj.getInt("target"),
                    current = obj.optInt("current", 0),
                    startDate = obj.getString("startDate"),
                    endDate = obj.getString("endDate"),
                    isCompleted = obj.optBoolean("isCompleted", false)
                )
            }
        }.getOrElse { emptyList() }
    }
    
    fun updateGoalProgress(context: Context) {
        val goals = loadGoals(context).toMutableList()
        val history = loadCompletionHistory(context)
        val today = istDateKey()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        goals.forEachIndexed { index, goal ->
            val startDate = runCatching { fmt.parse(goal.startDate) }.getOrNull()
            val endDate = runCatching { fmt.parse(goal.endDate) }.getOrNull()
            
            if (startDate != null && endDate != null) {
                val relevantCompletions = history.filter { entry ->
                    val entryDate = runCatching { fmt.parse(entry.date) }.getOrNull()
                    entryDate != null && !entryDate.before(startDate) && !entryDate.after(endDate)
                }
                
                val progress = when (goal.type) {
                    GoalType.DAILY_PROBLEMS, GoalType.WEEKLY_PROBLEMS, GoalType.MONTHLY_PROBLEMS -> 
                        relevantCompletions.distinctBy { it.problemId }.size
                    GoalType.STREAK_DAYS -> loadProblemStats(context).currentStreak
                    GoalType.TOPIC_MASTERY -> relevantCompletions.distinctBy { it.topics.firstOrNull() }.size
                    GoalType.DIFFICULTY_CHALLENGE -> relevantCompletions.count { it.difficulty == "Hard" }
                }
                
                goals[index] = goal.copy(
                    current = progress,
                    isCompleted = progress >= goal.target
                )
            }
        }
        
        val jsonArray = JSONArray()
        goals.forEach { g ->
            jsonArray.put(JSONObject().apply {
                put("id", g.id)
                put("type", g.type.name)
                put("target", g.target)
                put("current", g.current)
                put("startDate", g.startDate)
                put("endDate", g.endDate)
                put("isCompleted", g.isCompleted)
            })
        }
        prefs(context).edit().putString(KEY_GOALS, jsonArray.toString()).apply()
    }
    
    // ═══════════════════════════════════════════════════════════════
    // ACHIEVEMENTS (Gamification)
    // ═══════════════════════════════════════════════════════════════
    
    data class Achievement(
        val id: String,
        val name: String,
        val description: String,
        val icon: String,
        val category: AchievementCategory,
        val requirement: Int,
        val unlockedAt: String? = null,
        val progress: Int = 0
    )
    
    enum class AchievementCategory {
        STREAK, PROBLEMS_SOLVED, DIFFICULTY, SPEED, CONSISTENCY, MASTERY
    }
    
    private val DEFAULT_ACHIEVEMENTS = listOf(
        Achievement("first_blood", "First Blood", "Solve your first problem", "🎯", AchievementCategory.PROBLEMS_SOLVED, 1),
        Achievement("week_warrior", "Week Warrior", "Maintain a 7-day streak", "🔥", AchievementCategory.STREAK, 7),
        Achievement("month_master", "Month Master", "Maintain a 30-day streak", "🏆", AchievementCategory.STREAK, 30),
        Achievement("century", "Century", "Solve 100 problems", "💯", AchievementCategory.PROBLEMS_SOLVED, 100),
        Achievement("hard_hitter", "Hard Hitter", "Solve 10 hard problems", "💪", AchievementCategory.DIFFICULTY, 10),
        Achievement("speedster", "Speedster", "Solve a problem in under 10 minutes", "⚡", AchievementCategory.SPEED, 10),
        Achievement("night_owl", "Night Owl", "Solve a problem after midnight", "🦉", AchievementCategory.CONSISTENCY, 1),
        Achievement("early_bird", "Early Bird", "Solve a problem before 6 AM", "🐦", AchievementCategory.CONSISTENCY, 1),
        Achievement("diverse", "Diverse Thinker", "Solve problems from 10 different topics", "🎨", AchievementCategory.MASTERY, 10),
        Achievement("easy_peasy", "Easy Peasy", "Solve 50 easy problems", "🟢", AchievementCategory.DIFFICULTY, 50),
        Achievement("medium_rare", "Medium Rare", "Solve 50 medium problems", "🟡", AchievementCategory.DIFFICULTY, 50),
        Achievement("iron_will", "Iron Will", "Solve without AI assistance 10 times", "🛡️", AchievementCategory.MASTERY, 10),
        Achievement("ten_streak", "On Fire", "10-day streak", "🔥", AchievementCategory.STREAK, 10),
        Achievement("fifty_solved", "Half Century", "Solve 50 problems", "5️⃣0️⃣", AchievementCategory.PROBLEMS_SOLVED, 50),
        Achievement("daily_grind", "Daily Grind", "Solve problems for 5 consecutive days", "📅", AchievementCategory.STREAK, 5)
    )
    
    private fun checkAndAwardAchievements(context: Context) {
        val stats = loadProblemStats(context)
        val history = loadCompletionHistory(context)
        val currentAchievements = loadAchievements(context).toMutableList()
        
        DEFAULT_ACHIEVEMENTS.forEach { template ->
            val existing = currentAchievements.find { it.id == template.id }
            if (existing?.unlockedAt != null) return@forEach // Already unlocked
            
            val progress = when (template.category) {
                AchievementCategory.PROBLEMS_SOLVED -> stats.totalSolved
                AchievementCategory.STREAK -> stats.currentStreak
                AchievementCategory.DIFFICULTY -> when (template.id) {
                    "hard_hitter" -> stats.hardSolved
                    "easy_peasy" -> stats.easySolved
                    "medium_rare" -> stats.mediumSolved
                    else -> 0
                }
                AchievementCategory.SPEED -> {
                    val fastest = history.filter { it.timeTakenMinutes > 0 }.minOfOrNull { it.timeTakenMinutes } ?: Int.MAX_VALUE
                    if (fastest <= template.requirement) template.requirement else 0
                }
                AchievementCategory.MASTERY -> when (template.id) {
                    "diverse" -> stats.topicDistribution.size
                    "iron_will" -> history.count { !it.solvedWithAi }
                    else -> 0
                }
                AchievementCategory.CONSISTENCY -> {
                    // Check time-based achievements
                    when (template.id) {
                        "night_owl", "early_bird" -> if (history.isNotEmpty()) 1 else 0
                        else -> 0
                    }
                }
            }
            
            val isUnlocked = progress >= template.requirement
            val updated = template.copy(
                progress = progress,
                unlockedAt = if (isUnlocked) istDateKey() else null
            )
            
            currentAchievements.removeAll { it.id == template.id }
            currentAchievements.add(updated)
        }
        
        saveAchievements(context, currentAchievements)
    }
    
    fun loadAchievements(context: Context): List<Achievement> {
        val raw = prefs(context).getString(KEY_ACHIEVEMENTS, null) 
        if (raw == null) {
            // Return defaults with 0 progress
            return DEFAULT_ACHIEVEMENTS.map { it.copy(progress = 0) }
        }
        
        return runCatching {
            val arr = JSONArray(raw)
            val loaded = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Achievement(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    description = obj.getString("description"),
                    icon = obj.getString("icon"),
                    category = AchievementCategory.valueOf(obj.getString("category")),
                    requirement = obj.getInt("requirement"),
                    unlockedAt = obj.optString("unlockedAt").takeIf { it.isNotBlank() },
                    progress = obj.optInt("progress", 0)
                )
            }
            // Merge with defaults to catch new achievements
            val loadedIds = loaded.map { it.id }.toSet()
            loaded + DEFAULT_ACHIEVEMENTS.filter { it.id !in loadedIds }.map { it.copy(progress = 0) }
        }.getOrElse { DEFAULT_ACHIEVEMENTS.map { it.copy(progress = 0) } }
    }
    
    private fun saveAchievements(context: Context, achievements: List<Achievement>) {
        val jsonArray = JSONArray()
        achievements.forEach { a ->
            jsonArray.put(JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("description", a.description)
                put("icon", a.icon)
                put("category", a.category.name)
                put("requirement", a.requirement)
                put("unlockedAt", a.unlockedAt ?: "")
                put("progress", a.progress)
            })
        }
        prefs(context).edit().putString(KEY_ACHIEVEMENTS, jsonArray.toString()).apply()
    }
    
    // ═══════════════════════════════════════════════════════════════
    // FLASHCARDS
    // ═══════════════════════════════════════════════════════════════
    
    data class Flashcard(
        val id: String = UUID.randomUUID().toString(),
        val problemId: String,
        val problemTitle: String,
        val question: String,  // Could be "What's the time complexity?" or key insight
        val answer: String,
        val difficulty: String,
        val topics: List<String>,
        val lastReviewed: String? = null,
        val nextReviewDate: String? = null,
        val repetitions: Int = 0,
        val easeFactor: Float = 2.5f  // For spaced repetition (SM-2 algorithm)
    )
    
    fun saveFlashcard(context: Context, flashcard: Flashcard) {
        val cards = loadFlashcards(context).toMutableList()
        cards.removeAll { it.id == flashcard.id }
        cards.add(flashcard)
        saveFlashcards(context, cards)
    }
    
    fun loadFlashcards(context: Context): List<Flashcard> {
        val raw = prefs(context).getString(KEY_FLASHCARDS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Flashcard(
                    id = obj.getString("id"),
                    problemId = obj.getString("problemId"),
                    problemTitle = obj.getString("problemTitle"),
                    question = obj.getString("question"),
                    answer = obj.getString("answer"),
                    difficulty = obj.optString("difficulty", "Medium"),
                    topics = (0 until obj.optJSONArray("topics")?.length()!!).map {
                        obj.getJSONArray("topics").getString(it)
                    },
                    lastReviewed = obj.optString("lastReviewed").takeIf { it.isNotBlank() },
                    nextReviewDate = obj.optString("nextReviewDate").takeIf { it.isNotBlank() },
                    repetitions = obj.optInt("repetitions", 0),
                    easeFactor = obj.optDouble("easeFactor", 2.5).toFloat()
                )
            }
        }.getOrElse { emptyList() }
    }
    
    private fun saveFlashcards(context: Context, cards: List<Flashcard>) {
        val jsonArray = JSONArray()
        cards.forEach { c ->
            jsonArray.put(JSONObject().apply {
                put("id", c.id)
                put("problemId", c.problemId)
                put("problemTitle", c.problemTitle)
                put("question", c.question)
                put("answer", c.answer)
                put("difficulty", c.difficulty)
                put("topics", JSONArray(c.topics))
                put("lastReviewed", c.lastReviewed ?: "")
                put("nextReviewDate", c.nextReviewDate ?: "")
                put("repetitions", c.repetitions)
                put("easeFactor", c.easeFactor)
            })
        }
        prefs(context).edit().putString(KEY_FLASHCARDS, jsonArray.toString()).apply()
    }
    
    fun getDueFlashcards(context: Context): List<Flashcard> {
        val today = istDateKey()
        return loadFlashcards(context).filter { card ->
            card.nextReviewDate == null || card.nextReviewDate <= today
        }
    }
    
    fun updateFlashcardAfterReview(context: Context, cardId: String, quality: Int) {
        // quality: 0-5 (SM-2 algorithm)
        val cards = loadFlashcards(context).toMutableList()
        val index = cards.indexOfFirst { it.id == cardId }
        if (index == -1) return
        
        val card = cards[index]
        val newEaseFactor = maxOf(1.3f, card.easeFactor + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f)))
        val newRepetitions = if (quality >= 3) card.repetitions + 1 else 0
        
        val interval = when {
            newRepetitions == 0 -> 1
            newRepetitions == 1 -> 1
            newRepetitions == 2 -> 6
            else -> ((cards[index].repetitions - 1) * newEaseFactor).toInt()
        }
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, interval)
        val nextDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        
        cards[index] = card.copy(
            lastReviewed = istDateKey(),
            nextReviewDate = nextDate,
            repetitions = newRepetitions,
            easeFactor = newEaseFactor
        )
        
        saveFlashcards(context, cards)
    }
    
    // ═══════════════════════════════════════════════════════════════
    // FOCUS SESSIONS
    // ═══════════════════════════════════════════════════════════════
    
    data class FocusSession(
        val id: String = UUID.randomUUID().toString(),
        val startTime: Long,
        val endTime: Long? = null,
        val durationMinutes: Int,
        val problemId: String? = null,
        val completed: Boolean = false,
        val distractions: Int = 0
    )
    
    fun saveFocusSession(context: Context, session: FocusSession) {
        val sessions = loadFocusSessions(context).toMutableList()
        sessions.removeAll { it.id == session.id }
        sessions.add(session)
        
        val jsonArray = JSONArray()
        sessions.forEach { s ->
            jsonArray.put(JSONObject().apply {
                put("id", s.id)
                put("startTime", s.startTime)
                put("endTime", s.endTime ?: 0L)
                put("durationMinutes", s.durationMinutes)
                put("problemId", s.problemId ?: "")
                put("completed", s.completed)
                put("distractions", s.distractions)
            })
        }
        prefs(context).edit().putString(KEY_FOCUS_SESSIONS, jsonArray.toString()).apply()
    }
    
    fun loadFocusSessions(context: Context): List<FocusSession> {
        val raw = prefs(context).getString(KEY_FOCUS_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                FocusSession(
                    id = obj.getString("id"),
                    startTime = obj.getLong("startTime"),
                    endTime = obj.optLong("endTime").takeIf { it > 0 },
                    durationMinutes = obj.getInt("durationMinutes"),
                    problemId = obj.optString("problemId").takeIf { it.isNotBlank() },
                    completed = obj.optBoolean("completed", false),
                    distractions = obj.optInt("distractions", 0)
                )
            }
        }.getOrElse { emptyList() }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // USER PROFILE & LEADERBOARD
    // ═══════════════════════════════════════════════════════════════
    
    data class UserProfile(
        val username: String = "Vignesh",
        val level: Int = 1,
        val xp: Int = 0,
        val title: String = "Beginner"
    )
    
    data class LeaderboardEntry(
        val username: String,
        val score: Int,
        val rank: Int,
        val isCurrentUser: Boolean = false
    )
    
    fun loadUserProfile(context: Context): UserProfile {
        val raw = prefs(context).getString(KEY_USER_PROFILE, null) ?: return UserProfile()
        return runCatching {
            val json = JSONObject(raw)
            UserProfile(
                username = json.optString("username", "Vignesh"),
                level = json.optInt("level", 1),
                xp = json.optInt("xp", 0),
                title = json.optString("title", "Beginner")
            )
        }.getOrElse { UserProfile() }
    }
    
    fun saveUserProfile(context: Context, profile: UserProfile) {
        val json = JSONObject().apply {
            put("username", profile.username)
            put("level", profile.level)
            put("xp", profile.xp)
            put("title", profile.title)
        }
        prefs(context).edit().putString(KEY_USER_PROFILE, json.toString()).apply()
    }
    
    fun calculateLevel(xp: Int): Pair<Int, String> {
        val level = (xp / 100) + 1
        val title = when {
            level >= 50 -> "Grandmaster"
            level >= 40 -> "Master"
            level >= 30 -> "Expert"
            level >= 20 -> "Advanced"
            level >= 10 -> "Intermediate"
            level >= 5 -> "Apprentice"
            else -> "Beginner"
        }
        return Pair(level, title)
    }
    
    fun addXp(context: Context, amount: Int) {
        val profile = loadUserProfile(context)
        val newXp = profile.xp + amount
        val (newLevel, newTitle) = calculateLevel(newXp)
        saveUserProfile(context, profile.copy(xp = newXp, level = newLevel, title = newTitle))
    }
    
    // Mock leaderboard - in production, this would be server-side
    fun getLeaderboard(context: Context): List<LeaderboardEntry> {
        val userProfile = loadUserProfile(context)
        val userScore = loadProblemStats(context).totalSolved * 10 + 
                        loadProblemStats(context).currentStreak * 5 +
                        loadAchievements(context).count { it.unlockedAt != null } * 20
        
        // Mock other players
        val mockPlayers = listOf(
            LeaderboardEntry("CodeNinja", 2500, 1),
            LeaderboardEntry("AlgoMaster", 2200, 2),
            LeaderboardEntry("ByteWizard", 1800, 3),
            LeaderboardEntry("DSAKing", 1500, 4),
            LeaderboardEntry("LeetPro", 1200, 5)
        )
        
        val allPlayers = (mockPlayers + LeaderboardEntry(userProfile.username, userScore, 0, true))
            .sortedByDescending { it.score }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
        
        return allPlayers
    }
}
