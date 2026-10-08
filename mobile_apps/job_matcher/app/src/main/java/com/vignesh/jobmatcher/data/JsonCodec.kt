package com.vignesh.jobmatcher.data

import com.vignesh.jobmatcher.model.AppSettings
import com.vignesh.jobmatcher.model.CandidateProfile
import com.vignesh.jobmatcher.model.Company
import com.vignesh.jobmatcher.model.Job
import com.vignesh.jobmatcher.model.JobStatus
import com.vignesh.jobmatcher.model.ProfileSkill
import com.vignesh.jobmatcher.model.SourceType
import com.vignesh.jobmatcher.model.Tailoring
import org.json.JSONArray
import org.json.JSONObject

/** Hand-written org.json (de)serialization for everything persisted on disk. */
object JsonCodec {

    // org.json's optString returns the literal "null" for JSON nulls -- guard every read.
    fun JSONObject.str(key: String, default: String = ""): String =
        optString(key, default).takeIf { it.isNotBlank() && it != "null" } ?: default

    fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            optString(i).takeIf { it.isNotBlank() && it != "null" }?.trim()
        }
    }

    fun List<String>.toJsonArray(): JSONArray = JSONArray().also { arr -> forEach { arr.put(it) } }

    // ---- Company -------------------------------------------------------------------

    fun companyToJson(c: Company) = JSONObject()
        .put("id", c.id)
        .put("name", c.name)
        .put("source", c.source.name)
        .put("identifier", c.identifier)
        .put("priority", c.priority)
        .put("enabled", c.enabled)
        .put("lastFetchedAt", c.lastFetchedAt)
        .put("lastFetchCount", c.lastFetchCount)
        .put("lastError", c.lastError)

    fun companyFromJson(o: JSONObject) = Company(
        id = o.str("id"),
        name = o.str("name"),
        source = SourceType.parse(o.str("source")),
        identifier = o.str("identifier"),
        priority = o.optInt("priority", 2).coerceIn(1, 3),
        enabled = o.optBoolean("enabled", true),
        lastFetchedAt = o.optLong("lastFetchedAt"),
        lastFetchCount = o.optInt("lastFetchCount"),
        lastError = o.str("lastError")
    )

    // ---- Job -----------------------------------------------------------------------

    private fun tailoringToJson(t: Tailoring) = JSONObject()
        .put("summary", t.summary)
        .put("resumeBullets", t.resumeBullets)
        .put("coverLetter", t.coverLetter)
        .put("gapPlan", t.gapPlan)
        .put("referralMessage", t.referralMessage)
        .put("createdAt", t.createdAt)

    private fun tailoringFromJson(o: JSONObject) = Tailoring(
        summary = o.str("summary"),
        resumeBullets = o.str("resumeBullets"),
        coverLetter = o.str("coverLetter"),
        gapPlan = o.str("gapPlan"),
        referralMessage = o.str("referralMessage"),
        createdAt = o.optLong("createdAt")
    )

    fun jobToJson(j: Job): JSONObject = JSONObject()
        .put("id", j.id)
        .put("companyId", j.companyId)
        .put("companyName", j.companyName)
        .put("title", j.title)
        .put("location", j.location)
        .put("url", j.url)
        .put("description", j.description)
        .put("postedAt", j.postedAt)
        .put("source", j.source.name)
        .put("firstSeenAt", j.firstSeenAt)
        .put("lastSeenAt", j.lastSeenAt)
        .put("closed", j.closed)
        .put("localScore", j.localScore)
        .put("localMatchedSkills", j.localMatchedSkills.toJsonArray())
        .put("claudeScore", j.claudeScore ?: JSONObject.NULL)
        .put("claudeVerdict", j.claudeVerdict)
        .put("claudeReasons", j.claudeReasons)
        .put("matchedSkills", j.matchedSkills.toJsonArray())
        .put("gaps", j.gaps.toJsonArray())
        .put("scoredAt", j.scoredAt)
        .put("status", j.status.name)
        .put("statusUpdatedAt", j.statusUpdatedAt)
        .put("appliedAt", j.appliedAt)
        .put("notes", j.notes)
        .put("tailoring", j.tailoring?.let { tailoringToJson(it) } ?: JSONObject.NULL)

    fun jobFromJson(o: JSONObject) = Job(
        id = o.str("id"),
        companyId = o.str("companyId"),
        companyName = o.str("companyName"),
        title = o.str("title"),
        location = o.str("location"),
        url = o.str("url"),
        description = o.str("description"),
        postedAt = o.str("postedAt"),
        source = SourceType.parse(o.str("source")),
        firstSeenAt = o.optLong("firstSeenAt"),
        lastSeenAt = o.optLong("lastSeenAt"),
        closed = o.optBoolean("closed"),
        localScore = o.optInt("localScore"),
        localMatchedSkills = o.optJSONArray("localMatchedSkills").strings(),
        claudeScore = if (o.isNull("claudeScore")) null else o.optInt("claudeScore"),
        claudeVerdict = o.str("claudeVerdict"),
        claudeReasons = o.str("claudeReasons"),
        matchedSkills = o.optJSONArray("matchedSkills").strings(),
        gaps = o.optJSONArray("gaps").strings(),
        scoredAt = o.optLong("scoredAt"),
        status = JobStatus.parse(o.str("status")),
        statusUpdatedAt = o.optLong("statusUpdatedAt"),
        appliedAt = o.optLong("appliedAt"),
        notes = o.str("notes"),
        tailoring = o.optJSONObject("tailoring")?.let { tailoringFromJson(it) }
    )

    // ---- Profile -------------------------------------------------------------------

    fun profileToJson(p: CandidateProfile): JSONObject = JSONObject()
        .put("headline", p.headline)
        .put("years_experience", p.yearsExperience)
        .put("target_titles", p.targetTitles.toJsonArray())
        .put("skills", JSONArray().also { arr ->
            p.skills.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("name", s.name)
                        .put("aliases", s.aliases.toJsonArray())
                        .put("weight", s.weight)
                )
            }
        })
        .put("search_terms", p.searchTerms.toJsonArray())
        .put("updatedAt", p.updatedAt)
        .put("origin", p.origin)

    /** Also the parser for Claude's <profile_json> block, hence the snake_case keys. */
    fun profileFromJson(o: JSONObject): CandidateProfile {
        val skillsArr = o.optJSONArray("skills") ?: JSONArray()
        val skills = (0 until skillsArr.length()).mapNotNull { i ->
            when (val item = skillsArr.opt(i)) {
                is JSONObject -> item.str("name").takeIf { it.isNotBlank() }?.let { name ->
                    ProfileSkill(
                        name = name,
                        aliases = item.optJSONArray("aliases").strings(),
                        weight = item.optInt("weight", 2).coerceIn(1, 3)
                    )
                }
                is String -> item.takeIf { it.isNotBlank() }?.let { ProfileSkill(it) }
                else -> null
            }
        }
        return CandidateProfile(
            headline = o.str("headline"),
            yearsExperience = o.optInt("years_experience"),
            targetTitles = o.optJSONArray("target_titles").strings(),
            skills = skills,
            searchTerms = o.optJSONArray("search_terms").strings(),
            updatedAt = o.optLong("updatedAt"),
            origin = o.str("origin", "default")
        )
    }

    // ---- Settings ------------------------------------------------------------------

    fun settingsToJson(s: AppSettings): JSONObject = JSONObject()
        .put("matchThreshold", s.matchThreshold)
        .put("prefilterThreshold", s.prefilterThreshold)
        .put("scoringBatchSize", s.scoringBatchSize)
        .put("searchBatchSize", s.searchBatchSize)
        .put("maxDescriptionChars", s.maxDescriptionChars)
        .put("includeResumeInScoring", s.includeResumeInScoring)
        .put("locationKeywords", s.locationKeywords.toJsonArray())
        .put("excludeTitleKeywords", s.excludeTitleKeywords.toJsonArray())
        .put("autoFetchEnabled", s.autoFetchEnabled)
        .put("autoFetchHour", s.autoFetchHour)

    fun settingsFromJson(o: JSONObject): AppSettings {
        val d = AppSettings()
        return AppSettings(
            matchThreshold = o.optInt("matchThreshold", d.matchThreshold),
            prefilterThreshold = o.optInt("prefilterThreshold", d.prefilterThreshold),
            scoringBatchSize = o.optInt("scoringBatchSize", d.scoringBatchSize),
            searchBatchSize = o.optInt("searchBatchSize", d.searchBatchSize),
            maxDescriptionChars = o.optInt("maxDescriptionChars", d.maxDescriptionChars),
            includeResumeInScoring = o.optBoolean("includeResumeInScoring", d.includeResumeInScoring),
            locationKeywords = o.optJSONArray("locationKeywords")?.strings() ?: d.locationKeywords,
            excludeTitleKeywords = o.optJSONArray("excludeTitleKeywords")?.strings() ?: d.excludeTitleKeywords,
            autoFetchEnabled = o.optBoolean("autoFetchEnabled", d.autoFetchEnabled),
            autoFetchHour = o.optInt("autoFetchHour", d.autoFetchHour)
        )
    }
}
