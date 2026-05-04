package com.vignesh.jobautomation.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.generationConfig
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.jobautomation.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini AI Client for job automation tasks.
 * Handles all AI-powered analyses including job matching, resume customization, and interview prep.
 */
class GeminiClient private constructor(apiKey: String) {
    
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.3f
            maxOutputTokens = 4096
        }
    )
    
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    companion object {
        private const val TAG = "GeminiClient"
        
        @Volatile
        private var INSTANCE: GeminiClient? = null
        
        fun getInstance(apiKey: String = BuildConfig.GEMINI_API_KEY): GeminiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GeminiClient(apiKey).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Analyze job fitment against user profile.
     * Returns match score (0-100) with justification.
     */
    suspend fun analyzeJobFitment(
        profileSummary: String,
        skills: Map<String, List<String>>,
        experience: String,
        desiredRoles: List<String>,
        jobDescription: String
    ): JobAnalysisResult = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("You are an expert job matching analyst. Analyze how well this candidate matches the job.")
            appendLine()
            appendLine("=== CANDIDATE PROFILE ===")
            appendLine("Summary: $profileSummary")
            appendLine()
            appendLine("Skills:")
            skills.forEach { (category, skillList) ->
                appendLine("- $category: ${skillList.joinToString(", ")}")
            }
            appendLine()
            appendLine("Experience: $experience")
            appendLine()
            appendLine("Desired Roles: ${desiredRoles.joinToString(", ")}")
            appendLine()
            appendLine("=== JOB DESCRIPTION ===")
            appendLine(jobDescription.take(3000))
            appendLine()
            appendLine("=== ANALYSIS REQUIRED ===")
            appendLine("Provide a JSON response with EXACTLY this structure:")
            appendLine("""
{
  "match_score": 75,
  "summary": "Brief 1-sentence summary of match",
  "strengths": ["strength 1", "strength 2", "strength 3"],
  "weaknesses": ["gap 1", "gap 2"],
  "key_requirements_met": ["requirement 1", "requirement 2"],
  "key_requirements_missing": ["missing 1"],
  "salary_range_estimate": {"min": 120000, "max": 180000},
  "recommendation": "APPLY" | "CONSIDER" | "SKIP",
  "next_steps": "What the candidate should do"
}
            """.trimIndent())
            appendLine()
            appendLine("IMPORTANT: Return ONLY valid JSON, no markdown, no extra text.")
        }
        
        try {
            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: throw Exception("Empty response")
            parseJobAnalysis(text)
        } catch (e: Exception) {
            Log.e(TAG, "Job analysis failed", e)
            JobAnalysisResult(
                matchScore = 0f,
                summary = "Analysis failed: ${e.message}",
                strengths = emptyList(),
                weaknesses = emptyList(),
                recommendation = "SKIP"
            )
        }
    }
    
    /**
     * Generate a customized resume for a specific job.
     */
    suspend fun customizeResume(
        profileJson: String,
        jobDescription: String
    ): ResumeCustomization = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("You are an expert ATS resume optimizer. Customize this resume for the job.")
            appendLine()
            appendLine("=== CANDIDATE PROFILE (JSON) ===")
            appendLine(profileJson.take(4000))
            appendLine()
            appendLine("=== TARGET JOB ===")
            appendLine(jobDescription.take(2000))
            appendLine()
            appendLine("=== CUSTOMIZATION REQUIRED ===")
            appendLine("Provide a JSON response with EXACTLY this structure:")
            appendLine("""
{
  "optimized_summary": "ATS-optimized professional summary (2-3 sentences)",
  "highlighted_skills": ["skill1", "skill2", "skill3"],
  "experience_bullets": [
    {
      "company": "Company Name",
      "bullets": ["Achievement-focused bullet 1", "Bullet 2"]
    }
  ],
  "keywords_to_add": ["keyword1", "keyword2"],
  "ats_score_estimate": 85,
  "cover_letter_opening": "Compelling opening paragraph"
}
            """.trimIndent())
            appendLine()
            appendLine("IMPORTANT: Return ONLY valid JSON.")
        }
        
        try {
            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: throw Exception("Empty response")
            parseResumeCustomization(text)
        } catch (e: Exception) {
            Log.e(TAG, "Resume customization failed", e)
            ResumeCustomization(
                optimizedSummary = "",
                highlightedSkills = emptyList(),
                experienceBullets = emptyList(),
                keywordsToAdd = emptyList(),
                atsScoreEstimate = 0,
                coverLetterOpening = ""
            )
        }
    }
    
    /**
     * Generate interview preparation materials.
     */
    suspend fun generateInterviewPrep(
        jobTitle: String,
        companyName: String,
        jobDescription: String,
        profileSummary: String
    ): InterviewPrepResult = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("You are an expert interview coach. Prepare the candidate for this interview.")
            appendLine()
            appendLine("=== JOB ===")
            appendLine("Title: $jobTitle at $companyName")
            appendLine("Description: ${jobDescription.take(2000)}")
            appendLine()
            appendLine("=== CANDIDATE ===")
            appendLine(profileSummary.take(1500))
            appendLine()
            appendLine("=== INTERVIEW PREP REQUIRED ===")
            appendLine("Provide a JSON response with EXACTLY this structure:")
            appendLine("""
{
  "company_research": {
    "key_facts": ["fact1", "fact2"],
    "recent_news": ["news1", "news2"],
    "culture_notes": "Company culture insights"
  },
  "likely_questions": [
    {
      "question": "Interview question",
      "type": "behavioral" | "technical" | "situational",
      "suggested_answer_points": ["point1", "point2"],
      "star_example": "Optional STAR format example"
    }
  ],
  "questions_to_ask": ["Question 1 to ask interviewer", "Question 2"],
  "technical_topics": ["Topic to review 1", "Topic 2"],
  "red_flags_to_avoid": ["Avoid saying X", "Don't forget Y"],
  "preparation_checklist": ["Research company", "Review portfolio", "Prepare questions"]
}
            """.trimIndent())
            appendLine()
            appendLine("IMPORTANT: Return ONLY valid JSON.")
        }
        
        try {
            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: throw Exception("Empty response")
            parseInterviewPrep(text)
        } catch (e: Exception) {
            Log.e(TAG, "Interview prep failed", e)
            InterviewPrepResult(
                companyResearch = CompanyResearch(emptyList(), emptyList(), ""),
                likelyQuestions = emptyList(),
                questionsToAsk = emptyList(),
                technicalTopics = emptyList(),
                redFlagsToAvoid = emptyList(),
                preparationChecklist = emptyList()
            )
        }
    }
    
    /**
     * Analyze skill gaps and provide improvement recommendations.
     */
    suspend fun analyzeWeaknesses(
        currentSkills: Map<String, List<String>>,
        targetRoles: List<String>,
        recentJobMisses: List<String>
    ): WeaknessAnalysis = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("You are a career development advisor. Analyze skill gaps and provide improvement plan.")
            appendLine()
            appendLine("=== CURRENT SKILLS ===")
            currentSkills.forEach { (category, skills) ->
                appendLine("- $category: ${skills.joinToString(", ")}")
            }
            appendLine()
            appendLine("=== TARGET ROLES ===")
            appendLine(targetRoles.joinToString(", "))
            appendLine()
            appendLine("=== RECENT JOB WEAKNESSES IDENTIFIED ===")
            recentJobMisses.take(5).forEachIndexed { i, weakness ->
                appendLine("${i + 1}. $weakness")
            }
            appendLine()
            appendLine("=== ANALYSIS REQUIRED ===")
            appendLine("Provide a JSON response:")
            appendLine("""
{
  "critical_gaps": [
    {
      "skill": "Skill name",
      "importance": "HIGH" | "MEDIUM" | "LOW",
      "current_level": "Beginner/Intermediate/Advanced/None",
      "target_level": "What level is needed",
      "time_to_learn": "Estimated time"
    }
  ],
  "learning_resources": [
    {
      "skill": "Skill name",
      "resources": ["Resource 1", "Resource 2"],
      "certifications": ["Cert 1 if applicable"]
    }
  ],
  "quick_wins": ["Improvement that can be done quickly"],
  "long_term_plan": "6-month improvement roadmap",
  "portfolio_suggestions": ["Project idea to demonstrate skill"]
}
            """.trimIndent())
            appendLine()
            appendLine("IMPORTANT: Return ONLY valid JSON.")
        }
        
        try {
            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: throw Exception("Empty response")
            parseWeaknessAnalysis(text)
        } catch (e: Exception) {
            Log.e(TAG, "Weakness analysis failed", e)
            WeaknessAnalysis(
                criticalGaps = emptyList(),
                learningResources = emptyList(),
                quickWins = emptyList(),
                longTermPlan = "",
                portfolioSuggestions = emptyList()
            )
        }
    }
    
    /**
     * Chat with the AI about job search topics.
     */
    suspend fun chat(
        message: String,
        context: String? = null,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("You are a helpful job search assistant. Help the user with their job search.")
            appendLine()
            if (context != null) {
                appendLine("=== CONTEXT ===")
                appendLine(context.take(2000))
                appendLine()
            }
            if (chatHistory.isNotEmpty()) {
                appendLine("=== CONVERSATION HISTORY ===")
                chatHistory.takeLast(5).forEach { (role, content) ->
                    appendLine("$role: $content")
                }
                appendLine()
            }
            appendLine("=== USER MESSAGE ===")
            appendLine(message)
            appendLine()
            appendLine("Respond helpfully and concisely. If discussing jobs or applications, be specific and actionable.")
        }
        
        try {
            val response = model.generateContent(prompt)
            response.text?.trim() ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            Log.e(TAG, "Chat failed", e)
            "Sorry, I encountered an error: ${e.message}"
        }
    }
    
    // ==================== JSON Parsing Helpers ====================
    
    private fun parseJobAnalysis(json: String): JobAnalysisResult {
        val cleanJson = cleanJsonResponse(json)
        return try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter: JsonAdapter<Map<String, Any>> = moshi.adapter(type)
            val map = adapter.fromJson(cleanJson) ?: emptyMap()
            
            JobAnalysisResult(
                matchScore = (map["match_score"] as? Number)?.toFloat() ?: 0f,
                summary = map["summary"] as? String ?: "",
                strengths = (map["strengths"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                weaknesses = (map["weaknesses"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                keyRequirementsMet = (map["key_requirements_met"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                keyRequirementsMissing = (map["key_requirements_missing"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                salaryEstimate = (map["salary_range_estimate"] as? Map<*, *>)?.let {
                    SalaryEstimate((it["min"] as? Number)?.toInt() ?: 0, (it["max"] as? Number)?.toInt() ?: 0)
                },
                recommendation = map["recommendation"] as? String ?: "CONSIDER",
                nextSteps = map["next_steps"] as? String ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse job analysis: $cleanJson", e)
            JobAnalysisResult(matchScore = 0f, summary = "Parse error", strengths = emptyList(), weaknesses = emptyList(), recommendation = "SKIP")
        }
    }
    
    private fun parseResumeCustomization(json: String): ResumeCustomization {
        val cleanJson = cleanJsonResponse(json)
        return try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter: JsonAdapter<Map<String, Any>> = moshi.adapter(type)
            val map = adapter.fromJson(cleanJson) ?: emptyMap()
            
            ResumeCustomization(
                optimizedSummary = map["optimized_summary"] as? String ?: "",
                highlightedSkills = (map["highlighted_skills"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                experienceBullets = (map["experience_bullets"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { m ->
                        ExperienceBullet(
                            company = m["company"] as? String ?: "",
                            bullets = (m["bullets"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        )
                    }
                } ?: emptyList(),
                keywordsToAdd = (map["keywords_to_add"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                atsScoreEstimate = (map["ats_score_estimate"] as? Number)?.toInt() ?: 0,
                coverLetterOpening = map["cover_letter_opening"] as? String ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse resume customization", e)
            ResumeCustomization("", emptyList(), emptyList(), emptyList(), 0, "")
        }
    }
    
    private fun parseInterviewPrep(json: String): InterviewPrepResult {
        val cleanJson = cleanJsonResponse(json)
        return try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter: JsonAdapter<Map<String, Any>> = moshi.adapter(type)
            val map = adapter.fromJson(cleanJson) ?: emptyMap()
            
            val companyResearchMap = map["company_research"] as? Map<*, *>
            val companyResearch = CompanyResearch(
                keyFacts = (companyResearchMap?.get("key_facts") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                recentNews = (companyResearchMap?.get("recent_news") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                cultureNotes = companyResearchMap?.get("culture_notes") as? String ?: ""
            )
            
            val questions = (map["likely_questions"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { q ->
                    InterviewQuestion(
                        question = q["question"] as? String ?: "",
                        type = q["type"] as? String ?: "general",
                        suggestedAnswerPoints = (q["suggested_answer_points"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        starExample = q["star_example"] as? String
                    )
                }
            } ?: emptyList()
            
            InterviewPrepResult(
                companyResearch = companyResearch,
                likelyQuestions = questions,
                questionsToAsk = (map["questions_to_ask"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                technicalTopics = (map["technical_topics"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                redFlagsToAvoid = (map["red_flags_to_avoid"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                preparationChecklist = (map["preparation_checklist"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse interview prep", e)
            InterviewPrepResult(CompanyResearch(emptyList(), emptyList(), ""), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
    }
    
    private fun parseWeaknessAnalysis(json: String): WeaknessAnalysis {
        val cleanJson = cleanJsonResponse(json)
        return try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter: JsonAdapter<Map<String, Any>> = moshi.adapter(type)
            val map = adapter.fromJson(cleanJson) ?: emptyMap()
            
            WeaknessAnalysis(
                criticalGaps = (map["critical_gaps"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { g ->
                        SkillGap(
                            skill = g["skill"] as? String ?: "",
                            importance = g["importance"] as? String ?: "MEDIUM",
                            currentLevel = g["current_level"] as? String ?: "",
                            targetLevel = g["target_level"] as? String ?: "",
                            timeToLearn = g["time_to_learn"] as? String ?: ""
                        )
                    }
                } ?: emptyList(),
                learningResources = (map["learning_resources"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { r ->
                        LearningResource(
                            skill = r["skill"] as? String ?: "",
                            resources = (r["resources"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            certifications = (r["certifications"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        )
                    }
                } ?: emptyList(),
                quickWins = (map["quick_wins"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                longTermPlan = map["long_term_plan"] as? String ?: "",
                portfolioSuggestions = (map["portfolio_suggestions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse weakness analysis", e)
            WeaknessAnalysis(emptyList(), emptyList(), emptyList(), "", emptyList())
        }
    }
    
    private fun cleanJsonResponse(text: String): String {
        var clean = text.trim()
        // Remove markdown code blocks
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        }
        if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return clean.trim()
    }
}

// ==================== Data Classes ====================

data class JobAnalysisResult(
    val matchScore: Float,
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val keyRequirementsMet: List<String> = emptyList(),
    val keyRequirementsMissing: List<String> = emptyList(),
    val salaryEstimate: SalaryEstimate? = null,
    val recommendation: String, // "APPLY", "CONSIDER", "SKIP"
    val nextSteps: String = ""
)

data class SalaryEstimate(
    val min: Int,
    val max: Int
)

data class ResumeCustomization(
    val optimizedSummary: String,
    val highlightedSkills: List<String>,
    val experienceBullets: List<ExperienceBullet>,
    val keywordsToAdd: List<String>,
    val atsScoreEstimate: Int,
    val coverLetterOpening: String
)

data class ExperienceBullet(
    val company: String,
    val bullets: List<String>
)

data class InterviewPrepResult(
    val companyResearch: CompanyResearch,
    val likelyQuestions: List<InterviewQuestion>,
    val questionsToAsk: List<String>,
    val technicalTopics: List<String>,
    val redFlagsToAvoid: List<String>,
    val preparationChecklist: List<String>
)

data class CompanyResearch(
    val keyFacts: List<String>,
    val recentNews: List<String>,
    val cultureNotes: String
)

data class InterviewQuestion(
    val question: String,
    val type: String,
    val suggestedAnswerPoints: List<String>,
    val starExample: String?
)

data class WeaknessAnalysis(
    val criticalGaps: List<SkillGap>,
    val learningResources: List<LearningResource>,
    val quickWins: List<String>,
    val longTermPlan: String,
    val portfolioSuggestions: List<String>
)

data class SkillGap(
    val skill: String,
    val importance: String,
    val currentLevel: String,
    val targetLevel: String,
    val timeToLearn: String
)

data class LearningResource(
    val skill: String,
    val resources: List<String>,
    val certifications: List<String>
)
