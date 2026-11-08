package com.example.ui.components

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One tick is one simulated minute; a dispatch day is 30 ticks, compressed into a 24h clock. */
data class SimClock(val day: Int, val hour: Int, val minute: Int) {
    val time: String get() = String.format(Locale.US, "%02d:%02d", hour, minute)
    val label: String get() = "Day $day · $time"
}

fun simClock(tick: Long): SimClock {
    val day = (tick / TICKS_PER_DAY).toInt() + 1
    val minuteOfDay = ((tick % TICKS_PER_DAY) * MINUTES_PER_TICK + DAY_START_MINUTES) % MINUTES_PER_DAY
    return SimClock(day, (minuteOfDay / 60).toInt(), (minuteOfDay % 60).toInt())
}

const val TICKS_PER_DAY = 30L
private const val MINUTES_PER_TICK = 48L
private const val MINUTES_PER_DAY = 1440L
private const val DAY_START_MINUTES = 360L // 06:00

fun money(value: Double, decimals: Boolean = false): String {
    val sign = if (value < 0) "-" else ""
    val magnitude = abs(value)
    return if (decimals) {
        sign + "$" + String.format(Locale.US, "%,.2f", magnitude)
    } else {
        sign + "$" + String.format(Locale.US, "%,.0f", magnitude)
    }
}

fun signedMoney(value: Double): String = (if (value > 0) "+" else "") + money(value)

/** Compact money for tiles: $18.5k / $1.2M. */
fun compactMoney(value: Double): String {
    val magnitude = abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        magnitude >= 1_000_000 -> sign + "$" + String.format(Locale.US, "%.1fM", magnitude / 1_000_000)
        magnitude >= 10_000 -> sign + "$" + String.format(Locale.US, "%.1fk", magnitude / 1_000)
        else -> sign + "$" + String.format(Locale.US, "%,.0f", magnitude)
    }
}

fun km(value: Double): String = if (value >= 100) {
    String.format(Locale.US, "%,.0f km", value)
} else {
    String.format(Locale.US, "%.1f km", value)
}

fun percent(value: Double, digits: Int = 0): String =
    String.format(Locale.US, "%.${digits}f%%", value * 100)

fun ticksLeft(deadlineTick: Long, currentTick: Long): Long = deadlineTick - currentTick

/** "12c left" / "4c late" — deadlines are measured in dispatch cycles. */
fun deadlineLabel(deadlineTick: Long, currentTick: Long): String {
    val left = ticksLeft(deadlineTick, currentTick)
    return when {
        left < 0 -> "${-left}c late"
        left == 0L -> "due now"
        else -> "${left}c left"
    }
}

fun isLate(deadlineTick: Long, currentTick: Long): Boolean = deadlineTick < currentTick

fun Int.plural(word: String): String = "$this $word" + if (this == 1) "" else "s"

/** Rounded percentage for meters, clamped to a sane 0..1 range. */
fun meterFraction(value: Double): Float = (value / 100.0).coerceIn(0.0, 1.0).toFloat()

fun scorePercent(value: Double): Int = (value * 100.0).roundToInt().coerceIn(0, 100)
