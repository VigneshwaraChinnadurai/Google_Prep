package com.vc.jobfinder.ui

sealed class Dest(val route: String) {
    data object Settings : Dest("settings")
    data object Resume   : Dest("resume")
    data object Run      : Dest("run")
    data object Matches  : Dest("matches")
    data object Contests : Dest("contests")
    data object Apply    : Dest("apply/{matchId}") {
        fun build(id: String) = "apply/$id"
    }
}
