package com.nuvio.tv.ui.util

import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C round 31 (switch `runtime_format`): add-ons write running times their own way
// ("2hr37min", "2h37m", "157 min", "2 hours 37 minutes"). This reads the hours and minutes out of such
// a text and writes them the app's way, "2h 37m" (or "2h", "45m"). Anything it can't read is returned
// as given, so an unusual format is never lost or mangled.

private val HOURS_MINUTES = Regex(
    """^\s*(\d{1,2})\s*(?:h|hr|hrs|hour|hours)\.?\s*(?:(\d{1,2})\s*(?:m|min|mins|minute|minutes)\.?)?\s*$""",
    RegexOption.IGNORE_CASE
)
private val CLOCK = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""")
private val MINUTES_ONLY = Regex("""^\s*(\d{1,3})\s*(?:m|min|mins|minute|minutes)?\.?\s*$""", RegexOption.IGNORE_CASE)

/** Hours and minutes in [raw] as total minutes, or null when it isn't a plain running time. */
internal fun nuvioCRuntimeMinutes(raw: String?): Int? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    HOURS_MINUTES.matchEntire(text)?.let { m ->
        val hours = m.groupValues[1].toIntOrNull() ?: return null
        val minutes = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        if (minutes >= 60) return null
        return hours * 60 + minutes
    }
    CLOCK.matchEntire(text)?.let { m ->
        val minutes = m.groupValues[2].toInt()
        if (minutes >= 60) return null
        return (m.groupValues[1].toInt() * 60 + minutes).takeIf { it > 0 }
    }
    MINUTES_ONLY.matchEntire(text)?.let { m ->
        return m.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
    }
    return null
}

/** Minutes as "2h 37m", "2h" or "45m". */
internal fun nuvioCRuntimeText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/** [raw] written as "2h 37m" when it is a running time; otherwise [raw] unchanged. */
fun nuvioCRuntime(raw: String?): String? {
    if (raw.isNullOrBlank()) return raw
    if (!NuvioCFeatures.RUNTIME_FORMAT) return raw
    val minutes = nuvioCRuntimeMinutes(raw) ?: return raw
    return nuvioCRuntimeText(minutes)
}
