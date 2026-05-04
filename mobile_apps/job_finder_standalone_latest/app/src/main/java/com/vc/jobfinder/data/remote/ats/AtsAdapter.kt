package com.vc.jobfinder.data.remote.ats

import com.vc.jobfinder.domain.Company
import com.vc.jobfinder.domain.Job
import javax.inject.Inject
import javax.inject.Singleton

interface AtsAdapter {
    val key: String
    suspend fun fetch(company: Company): List<Job>
}

@Singleton
class AtsRegistry @Inject constructor(
    greenhouse: GreenhouseAdapter,
    lever: LeverAdapter,
) {
    private val byKey: Map<String, AtsAdapter> = listOf(greenhouse, lever)
        .associateBy { it.key }

    fun adapterFor(key: String): AtsAdapter? = byKey[key]
}
