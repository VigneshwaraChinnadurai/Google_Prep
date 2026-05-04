package com.vc.jobfinder.data.remote.contests

import com.vc.jobfinder.domain.Competition
import javax.inject.Inject
import javax.inject.Singleton

interface ContestAdapter {
    suspend fun fetch(): List<Competition>
}

@Singleton
class ContestRegistry @Inject constructor(
    he: HackerEarthAdapter,
    hr: HackerRankAdapter,
    un: UnstopAdapter,
    kg: KaggleAdapter,
) {
    val all: List<ContestAdapter> = listOf(he, hr, un, kg)
}
