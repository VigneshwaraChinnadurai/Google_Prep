package com.vignesh.jobmatcher.model

/**
 * List filters. They live in the ViewModel, so they survive switching tabs and opening jobs
 * and are only reset when the app is closed. Every set is multi-select; empty = no filter.
 */
data class MatchFilters(
    val origins: Set<JobOrigin> = emptySet(),
    val statuses: Set<JobStatus> = emptySet()
) {
    val isActive: Boolean get() = origins.isNotEmpty() || statuses.isNotEmpty()

    /** Only ✋ Manual selected: list every job you added, scored or not. */
    val manualOnly: Boolean get() = origins == setOf(JobOrigin.MANUAL)

    /** Not-interested jobs stay hidden unless you filter for them explicitly. */
    fun accepts(job: Job): Boolean =
        (origins.isEmpty() || job.origin in origins) &&
            (if (statuses.isEmpty()) job.status != JobStatus.DISMISSED else job.status in statuses)
}

data class ApplicationFilters(val statuses: Set<JobStatus> = emptySet()) {
    val isActive: Boolean get() = statuses.isNotEmpty()

    /** No selection = the default view: active applications (shortlisted -> offer). */
    fun accepts(job: Job): Boolean =
        if (statuses.isEmpty()) job.status in JobStatus.ACTIVE else job.status in statuses
}

fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
