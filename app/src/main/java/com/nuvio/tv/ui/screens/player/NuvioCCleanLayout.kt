package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 47 `osd_clean_v2`: the Clean controls, second version.
//  - Zones: Left (Subtitles, Audio), a fixed Centre (Start over, Play / Pause, Next) that sits exactly in the middle of
//    the screen whatever the sides hold (an empty slot keeps Play centred on films), Right (Sources, Details,
//    Episodes), then the More icon.
//  - Clean has its own button order (Settings > Playback > Player buttons shows zones while Clean is picked); the
//    Nuvio C layout keeps its own list, so switching styles never moves the other one's buttons.
//  - A side that runs out of room never overlaps the centre: the buttons that don't fit move into More.
// Nothing here touches playback.

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.NuvioCFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class NuvioCCleanZone(val key: String) { LEFT("l"), CENTRE("c"), RIGHT("r"), MORE("x") }

internal data class NuvioCCleanSlot(val button: NuvioCOsdButton, val zone: NuvioCCleanZone)

/** Start over and Next live in the centre (or More); everything else on either side (or More). Play is fixed. */
internal fun NuvioCOsdButton.cleanZones(): List<NuvioCCleanZone> = when (this) {
    NuvioCOsdButton.START_OVER, NuvioCOsdButton.NEXT -> listOf(NuvioCCleanZone.CENTRE, NuvioCCleanZone.MORE)
    // [fork] 47b: Subtitles and Audio are always on a side (their panels hold everything More used to repeat)
    NuvioCOsdButton.SUBTITLES, NuvioCOsdButton.AUDIO -> listOf(NuvioCCleanZone.LEFT, NuvioCCleanZone.RIGHT)
    else -> listOf(NuvioCCleanZone.LEFT, NuvioCCleanZone.RIGHT, NuvioCCleanZone.MORE)
}

internal object NuvioCCleanOrder {
    private const val PREFS = "nuvio_c_osd"
    private const val KEY = "clean_order"

    val defaults: List<NuvioCCleanSlot> = listOf(
        NuvioCCleanSlot(NuvioCOsdButton.SUBTITLES, NuvioCCleanZone.LEFT),
        NuvioCCleanSlot(NuvioCOsdButton.AUDIO, NuvioCCleanZone.LEFT),
        NuvioCCleanSlot(NuvioCOsdButton.START_OVER, NuvioCCleanZone.CENTRE),
        NuvioCCleanSlot(NuvioCOsdButton.NEXT, NuvioCCleanZone.CENTRE),
        NuvioCCleanSlot(NuvioCOsdButton.SOURCES, NuvioCCleanZone.RIGHT),
        NuvioCCleanSlot(NuvioCOsdButton.CAST, NuvioCCleanZone.RIGHT),
        NuvioCCleanSlot(NuvioCOsdButton.EPISODES, NuvioCCleanZone.RIGHT),
        NuvioCCleanSlot(NuvioCOsdButton.CHAPTERS, NuvioCCleanZone.MORE),
        NuvioCCleanSlot(NuvioCOsdButton.SPEED, NuvioCCleanZone.MORE),
        NuvioCCleanSlot(NuvioCOsdButton.ASPECT, NuvioCCleanZone.MORE),
        NuvioCCleanSlot(NuvioCOsdButton.AUTO_SKIP, NuvioCCleanZone.MORE),
        NuvioCCleanSlot(NuvioCOsdButton.INFO, NuvioCCleanZone.MORE),
        NuvioCCleanSlot(NuvioCOsdButton.EXTERNAL, NuvioCCleanZone.MORE),
        NuvioCCleanSlot(NuvioCOsdButton.ENGINE, NuvioCCleanZone.MORE),
        NuvioCCleanSlot(NuvioCOsdButton.PARTY, NuvioCCleanZone.MORE),
        NuvioCCleanSlot(NuvioCOsdButton.REPORT, NuvioCCleanZone.MORE)
    ).filter { it.button != NuvioCOsdButton.AUTO_SKIP || NuvioCFeatures.OSD_AUTO_SKIP }

    private val _slots = MutableStateFlow(defaults)
    val slots: StateFlow<List<NuvioCCleanSlot>> = _slots.asStateFlow()
    @Volatile private var loaded = false

    fun load(context: Context): List<NuvioCCleanSlot> {
        if (!loaded) {
            loaded = true
            _slots.value = parse(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
        }
        return _slots.value
    }

    fun save(context: Context, slots: List<NuvioCCleanSlot>) {
        val sorted = sortedByZone(slots)
        _slots.value = sorted
        loaded = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(sorted)).apply()
    }

    fun reset(context: Context) = save(context, defaults)

    internal fun encode(slots: List<NuvioCCleanSlot>): String = slots.joinToString(",") { "${it.button.key}:${it.zone.key}" }

    /** Saved order; unknown keys dropped, zones a button can't take reset, buttons the save doesn't know join at their default. */
    internal fun parse(raw: String?): List<NuvioCCleanSlot> {
        if (raw.isNullOrBlank()) return defaults
        val known = defaults.map { it.button }.toSet()
        val read = raw.split(',').mapNotNull { part ->
            val b = NuvioCOsdButton.byKey(part.substringBefore(':'))?.takeIf { it in known } ?: return@mapNotNull null
            val zone = NuvioCCleanZone.entries.firstOrNull { it.key == part.substringAfter(':', "") }
                ?.takeIf { it in b.cleanZones() } ?: defaults.first { it.button == b }.zone
            NuvioCCleanSlot(b, zone)
        }.distinctBy { it.button }
        return sortedByZone(read + defaults.filter { d -> read.none { it.button == d.button } })
    }

    /** Left, centre, right, More; the order inside a zone is kept. */
    fun sortedByZone(slots: List<NuvioCCleanSlot>): List<NuvioCCleanSlot> = slots.sortedBy { it.zone.ordinal }

    /**
     * Moves a button one place up or down. Inside its zone it swaps with its neighbour; at the edge of its zone it
     * steps into the next zone it can take in that direction (last place going up, first place going down), so a
     * button can travel the whole list with Up / Down alone. Returns the same list when it can't move.
     */
    fun moved(slots: List<NuvioCCleanSlot>, index: Int, delta: Int): List<NuvioCCleanSlot> {
        if (index !in slots.indices || delta == 0) return slots
        val s = slots[index]
        val target = index + delta
        if (target in slots.indices && slots[target].zone == s.zone) {
            return slots.toMutableList().apply { add(target, removeAt(index)) }
        }
        val allowed = s.button.cleanZones().sortedBy { it.ordinal }
        val next = if (delta < 0) allowed.lastOrNull { it.ordinal < s.zone.ordinal } else allowed.firstOrNull { it.ordinal > s.zone.ordinal }
        next ?: return slots
        val rest = slots.toMutableList().apply { removeAt(index) }
        val at = if (delta < 0) rest.indexOfLast { it.zone.ordinal <= next.ordinal } + 1
        else rest.indexOfFirst { it.zone.ordinal >= next.ordinal }.let { if (it < 0) rest.size else it }
        rest.add(at, s.copy(zone = next))
        return rest
    }

    /** The next zone this button can take (Left, Right, More / Centre, More); it goes last in its new zone. */
    fun cycled(slots: List<NuvioCCleanSlot>, index: Int): List<NuvioCCleanSlot> {
        val s = slots.getOrNull(index) ?: return slots
        val zones = s.button.cleanZones()
        val next = zones[(zones.indexOf(s.zone) + 1) % zones.size]
        val rest = slots.toMutableList().apply { removeAt(index) }
        val at = rest.indexOfLast { it.zone.ordinal <= next.ordinal } + 1
        rest.add(at, s.copy(zone = next))
        return rest
    }
}

/**
 * Lays out [items] in a row with [gap] between them, as many as fit in the width it's given; the rest aren't composed
 * (and so can't take focus). [onFit] reports how many fit whenever that changes, so they can be listed in More.
 * The count starts again from all of them whenever the set of [keys] changes.
 */
@Composable
internal fun NuvioCCleanFitRow(
    keys: List<String>,
    gap: Dp,
    modifier: Modifier = Modifier,
    onFit: (Int) -> Unit,
    items: List<@Composable () -> Unit>
) {
    var fitted by remember(keys) { mutableIntStateOf(Int.MAX_VALUE) }
    val shown = items.take(fitted)
    Layout(content = { shown.forEach { it() } }, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(loose) }
        var used = 0
        var count = 0
        for (p in placeables) {
            val next = (if (count == 0) 0 else used + gapPx) + p.width
            if (next > constraints.maxWidth) break
            used = next
            count++
        }
        if (count < placeables.size && count != fitted) fitted = count
        val height = placeables.take(count).maxOfOrNull { it.height } ?: 0
        layout(used, height) {
            var x = 0
            placeables.take(count).forEach { p -> p.place(x, (height - p.height) / 2); x += p.width + gapPx }
        }
    }
    val n = minOf(fitted, items.size)
    androidx.compose.runtime.SideEffect { onFit(n) }
}

/** Left group at the start, [centre] exactly in the middle of the width, right group at the end; the sides get the room either side of the centre. */
@Composable
internal fun NuvioCCleanThreeZones(
    gap: Dp,
    modifier: Modifier = Modifier,
    left: @Composable () -> Unit,
    centre: @Composable () -> Unit,
    right: @Composable () -> Unit
) {
    Layout(contents = listOf(left, centre, right), modifier = modifier) { (l, c, r), constraints ->
        val w = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val loose = Constraints(maxWidth = w, maxHeight = constraints.maxHeight)
        val cp = c.firstOrNull()?.measure(loose)
        val cw = cp?.width ?: 0
        val side = ((w - cw) / 2 - gapPx).coerceAtLeast(0)
        val sideC = Constraints(maxWidth = side, maxHeight = constraints.maxHeight)
        val lp = l.firstOrNull()?.measure(sideC)
        val rp = r.firstOrNull()?.measure(sideC)
        val h = listOfNotNull(cp?.height, lp?.height, rp?.height).maxOrNull() ?: 0
        layout(w, h) {
            lp?.place(0, (h - lp.height) / 2)
            cp?.place((w - cw) / 2, (h - cp.height) / 2)
            rp?.place(w - rp.width, (h - rp.height) / 2)
        }
    }
}

/**
 * [fork] round 47 hud_any_device: the stream info page always offers its HUD button. On a TV box without an Info key
 * the HUD could only be reached once "Player stats HUD" was on in Settings on that device; pressing the button now
 * turns that on too (it's a per-device setting), then shows the HUD.
 */
internal fun PlayerViewModel.nuvioCToggleHud(state: PlayerUiState) {
    if (NuvioCFeatures.HUD_ANY_DEVICE && !state.playerStatsHudButtonAvailable) {
        val prefs = controller.deviceLocalPlayerPreferences
        viewModelScope.launch {
            prefs.setPlayerStatsHudButtonEnabled(true)
            prefs.setPlayerStatsHudActive(true)
        }
    } else {
        onEvent(PlayerEvent.OnTogglePlayerStatsHud)
    }
}
