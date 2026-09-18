package com.yamone.games.arcadecore

import java.util.Locale
import kotlin.math.floor

/** Accumulate before quantizing, rather than rounding every frame. */
class IceHeight {
    private var meters = 0.0
    val centimeters: Int get() = floor(meters * 100.0 + 1e-7).coerceIn(0.0, 2_000_000_000.0).toInt()
    fun reset() { meters = 0.0 }
    fun addScroll(normalizedDistance: Double) {
        if (normalizedDistance.isFinite() && normalizedDistance > 0.0) {
            meters = (meters + normalizedDistance * 1000.0).coerceAtMost(20_000_000.0)
        }
    }
}
fun formatIceHeight(centimeters: Int): String {
    val value = centimeters.coerceAtLeast(0)
    return String.format(Locale.US, "%d.%02dm", value / 100, value % 100)
}
fun legacyIceToCentimeters(meters: Int): Int =
    (meters.coerceAtLeast(0).toLong() * 100L).coerceAtMost(2_000_000_000L).toInt()
