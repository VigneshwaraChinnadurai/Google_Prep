package com.vignesh.leetcodechecker.watchlist

import java.util.UUID

data class BookmarkedVideo(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val videoId: String,
    val category: String,
    val isWatched: Boolean = false,
    val importedAtMillis: Long = System.currentTimeMillis()
)

data class WatchListCategory(
    val name: String,
    val colorHex: String
)

val WATCHLIST_COLOR_PALETTE = listOf(
    "#58A6FF", "#39D353", "#F0883E", "#A371F7",
    "#FF7B72", "#FFD700", "#00D4AA", "#6E7681"
)

fun watchlistColorFor(index: Int): String = WATCHLIST_COLOR_PALETTE[index % WATCHLIST_COLOR_PALETTE.size]
