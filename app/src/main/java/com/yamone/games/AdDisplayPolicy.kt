package com.yamone.games

import java.util.Locale

/** Pure presentation rules shared by the header, banner host and entitlement snapshot. */
internal object AdDisplayPolicy {
    fun showBanner(permanentAdFree: Boolean, untilMillis: Long, nowMillis: Long): Boolean =
        !permanentAdFree && untilMillis <= nowMillis

    // Starting, restarting and resuming a game never depend on ads or connectivity.
    fun requireInterstitial(): Boolean = false

    fun remainingSeconds(untilMillis: Long, nowMillis: Long): Long {
        val remaining = (untilMillis.coerceAtLeast(0L) - nowMillis.coerceAtLeast(0L)).coerceAtLeast(0L)
        return remaining / 1000L + if (remaining % 1000L > 0L) 1L else 0L
    }

    fun clock(untilMillis: Long, nowMillis: Long): String {
        val seconds = remainingSeconds(untilMillis, nowMillis)
        // Hours do not wrap at 24: accumulated rewards are retained in full.
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L)
    }
}
