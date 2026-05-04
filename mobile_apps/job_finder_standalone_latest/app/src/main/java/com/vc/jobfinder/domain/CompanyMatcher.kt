package com.vc.jobfinder.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Normalizes raw sponsor names ("Google LLC") and matches against canonical
 * company names from companies.yaml. Returns null if no confident match.
 */
@Singleton
class CompanyMatcher @Inject constructor(
    private val companiesProvider: CompaniesProvider,
) {
    private val suffixes = Regex(
        "\\b(inc|incorporated|ltd|limited|llc|llp|pvt|private|corp|corporation|" +
        "company|co|gmbh|sa|ag|plc|labs|technologies|tech|systems|solutions|" +
        "software|india|global)\\b\\.?",
        RegexOption.IGNORE_CASE
    )
    private val nonAlnum = Regex("[^a-z0-9]+")
    private val whitespace = Regex("\\s+")

    private fun normalize(name: String?): String {
        if (name.isNullOrBlank()) return ""
        var s = name.lowercase()
        s = suffixes.replace(s, "")
        s = nonAlnum.replace(s, " ").trim()
        s = whitespace.replace(s, " ")
        return s
    }

    private val index: Map<String, String> by lazy {
        companiesProvider.companies.associate { normalize(it.name) to it.name }
    }

    fun match(raw: String?): String? {
        val norm = normalize(raw)
        if (norm.isEmpty()) return null

        index[norm]?.let { return it }

        val candidates = mutableListOf<Pair<Int, String>>()
        for ((trackedNorm, canonical) in index) {
            if (trackedNorm.isEmpty()) continue
            if (trackedNorm in norm || norm in trackedNorm) {
                val score = minOf(trackedNorm.length, norm.length)
                candidates.add(score to canonical)
            }
        }
        return candidates.maxByOrNull { it.first }?.second
    }
}

interface CompaniesProvider {
    val companies: List<Company>
}
