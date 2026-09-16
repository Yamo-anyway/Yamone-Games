package com.yamone.games.arcadecore

import java.util.Locale

/** Stored unit for snow_rush_shards_ms is integer milliseconds, never seconds. */
fun preciseDuration(milliseconds: Int): String {
    val ms = milliseconds.coerceAtLeast(0)
    val seconds = ms / 1000
    return if (seconds >= 3600)
        String.format(Locale.US, "%d:%02d:%02d.%03d", seconds / 3600, seconds / 60 % 60, seconds % 60, ms % 1000)
    else String.format(Locale.US, "%02d:%02d.%03d", seconds / 60, seconds % 60, ms % 1000)
}
