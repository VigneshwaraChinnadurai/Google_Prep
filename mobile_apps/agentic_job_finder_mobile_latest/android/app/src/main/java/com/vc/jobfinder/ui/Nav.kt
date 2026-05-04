package com.vc.jobfinder.ui

sealed class Dest(val route: String) {
    data object Settings     : Dest("settings")
    data object Resume       : Dest("resume")
    data object Pipeline     : Dest("pipeline")
    data object Matches      : Dest("matches")
    data object Competitions : Dest("competitions")
    data object Apply        : Dest("apply/{matchId}") {
        fun build(id: String) = "apply/$id"
    }
}
