package com.nuvio.tv.core.nuvioc

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.calendar.CalendarSettings
import com.nuvio.tv.core.calendar.CalendarSettingsDataStore
import com.nuvio.tv.core.calendar.normalizeCalendarWindowDays
import com.nuvio.tv.core.calendar.normalizeCalendarZoneOverride
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.ui.screens.calendar.NuvioCCalendarStart
import com.nuvio.tv.ui.screens.calendar.NuvioCCalendarViewStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// [fork] Nuvio C nuvio_c_cloud_settings (round 19, 2026-10-22).
//
// Nuvio C's own settings (the toggles and choices in its settings screens, not hidden titles,
// reminders or remembered states) ride along with official's per-profile settings sync, so a new TV
// signed in to the same Nuvio profile gets them. No server change: they are written as ONE extra
// string entry, "nuvio_c_settings", in the profile's layout_settings store, which official already
// uploads and downloads as part of its settings blob. Official ignores entries it doesn't know and
// carries them along unchanged.
//
// The TV's own stores stay the source of truth:
// - a fork setting changes on this TV -> the entry is rewritten -> official's sync uploads it;
// - the entry arrives changed from the cloud (another TV) -> it is applied to the fork's stores;
// - the entry is missing (cloud never had it, or a push from an app without it wiped it) -> this
//   TV's settings are written back, unless they are all still at their defaults (a fresh install
//   before its first download must never overwrite the cloud copy with defaults).
@Singleton
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NuvioCCloudSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profiles: ProfileManager,
    private val factory: ProfileDataStoreFactory,
    private val calendar: CalendarSettingsDataStore,
    private val calendarView: NuvioCCalendarViewStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val viewPrefs: SharedPreferences by lazy { context.getSharedPreferences(VIEW_PREFS, Context.MODE_PRIVATE) }
    private val osdPrefs: SharedPreferences by lazy { context.getSharedPreferences("nuvio_c_osd", Context.MODE_PRIVATE) } // [fork] round 51 cloud_settings_all
    private val audioPrefs: SharedPreferences by lazy { context.getSharedPreferences("nuvio_c_audio", Context.MODE_PRIVATE) }
    private val keyPrefs: SharedPreferences by lazy { context.getSharedPreferences("nuvio_c_api_keys", Context.MODE_PRIVATE) }
    private var started = false

    fun start() {
        if (!NuvioCFeatures.NUVIO_C_CLOUD_SETTINGS || started) return
        started = true
        scope.launch {
            runCatching {
                profiles.activeProfileId.flatMapLatest { profile ->
                    combine(
                        factory.get(profile, LAYOUT).data.map { it[ENTRY] },
                        calendar.settings,
                        prefsTicks(prefs),
                        prefsTicks(viewPrefs),
                        combine(prefsTicks(osdPrefs), prefsTicks(audioPrefs), prefsTicks(keyPrefs), prefsTicks(shufflePrefs)) { _, _, _, _ -> } // [fork] round 52: + Roulette
                    ) { remote, _, _, _, _ -> profile to remote }
                }.debounce(400).collect { (profile, remote) -> reconcile(profile, remote) }
            }.onFailure { Log.w(TAG, "stopped", it) }
        }
    }

    // [fork] round 56 cloud_settings_per_profile: the profile whose settings are in the TV's stores right now
    @Volatile private var loadedFor: Int? = null

    private suspend fun reconcile(profile: Int, remote: String?) = mutex.withLock {
        if (NuvioCFeatures.CLOUD_SETTINGS_PER_PROFILE && loadedFor != profile) switchTo(profile, remote)
        val cal = calendar.current(profile)
        val local = encode(localValues(cal))
        if (NuvioCFeatures.CLOUD_SETTINGS_PER_PROFILE) prefs.edit { putString(localKey(profile), encode(localValues(cal) + localOnly())) }
        val lastKey = "cloud_last_p$profile"
        val last = prefs.getString(lastKey, null)
        when {
            remote == null -> {
                if (local == DEFAULTS) return@withLock
                write(profile, local)
                prefs.edit { putString(lastKey, local) }
                Log.i(TAG, "p$profile: cloud had no Nuvio C settings, wrote this TV's")
            }
            remote != last && remote != local -> {
                // Changed somewhere else: take it.
                val values = decode(remote) ?: return@withLock
                prefs.edit { putString(lastKey, remote) }
                apply(values)
                if (NuvioCFeatures.CLOUD_SETTINGS_PER_PROFILE) {
                    reloadCaches()
                    prefs.edit { putString(localKey(profile), encode(localValues(calendar.current(profile)) + localOnly())) }
                }
                Log.i(TAG, "p$profile: applied Nuvio C settings from the cloud")
            }
            remote != local -> {
                write(profile, local)
                prefs.edit { putString(lastKey, local) }
                Log.i(TAG, "p$profile: uploaded changed Nuvio C settings")
            }
            last != remote -> prefs.edit { putString(lastKey, remote) }
        }
    }

    /**
     * [fork] round 56 cloud_settings_per_profile. The fork's stores hold one set for the TV, so before this every profile
     * switch compared the new profile's cloud copy with the previous profile's values and uploaded those into it. Now
     * each profile keeps its own copy on this TV ("local_p<id>"), taken while it is active; on a switch that copy (else
     * the profile's cloud copy, else this TV's values the first time, else defaults) is put into the stores before any
     * comparison, and the player's remembered choices are read again. Calendar and Roulette choices were already per
     * profile, so a seed never touches them.
     */
    private suspend fun switchTo(profile: Int, remote: String?) {
        val own = prefs.getString(localKey(profile), null)
        val migrated = prefs.getBoolean(MIGRATED, false)
        val values = seedFor(own, remote, migrated)
        if (values != null) {
            apply(values)
            applyLocalOnly(values)
        }
        if (!migrated) prefs.edit { putBoolean(MIGRATED, true) }
        loadedFor = profile
        reloadCaches()
        Log.i(TAG, "p$profile: settings loaded from " + when { own != null -> "this TV's copy"; remote != null -> "the cloud"; !migrated -> "this TV (first run)"; else -> "defaults" })
    }

    /** Values kept per profile on this TV but never uploaded (the saved night mode level). */
    private fun localOnly(): Map<String, JsonPrimitive> =
        mapOf("local_night_level" to JsonPrimitive(audioPrefs.getString("night_mode", "") ?: ""))

    private fun applyLocalOnly(v: Map<String, JsonPrimitive>) {
        v["local_night_level"]?.contentOrNull?.let { lvl -> audioPrefs.edit { if (lvl.isBlank()) remove("night_mode") else putString("night_mode", lvl) } }
    }

    private fun reloadCaches() = runCatching {
        com.nuvio.tv.ui.screens.player.NuvioCOsdStyle.reload(context)
        com.nuvio.tv.ui.screens.player.NuvioCOsdOrder.reload(context)
        com.nuvio.tv.ui.screens.player.NuvioCCleanOrder.reload(context)
        com.nuvio.tv.ui.screens.player.NuvioCNight.reload(context)
    }.onFailure { Log.w(TAG, "cache reload", it) }

    private fun localKey(profile: Int) = "local_p$profile"

    private suspend fun write(profile: Int, value: String) {
        factory.get(profile, LAYOUT).edit { it[ENTRY] = value }
    }

    // ---- which settings travel -----------------------------------------------------------------
    private fun localValues(cal: CalendarSettings): Map<String, JsonPrimitive> = sortedMapOf(
        "bg_trailer_muted" to JsonPrimitive(prefs.getBoolean("bg_trailer_muted", true)),
        "prefer_imdb_trailers" to JsonPrimitive(prefs.getBoolean("prefer_imdb_trailers", false)),
        "prefer_sdh_subtitles" to JsonPrimitive(prefs.getBoolean("prefer_sdh_subtitles", false)),
        "subtitle_font" to JsonPrimitive(prefs.getString("subtitle_font", "") ?: ""),
        "calendar_start" to JsonPrimitive(calendarView.start.value.name),
        "calendar_menu_visible" to JsonPrimitive(cal.menuVisible),
        "calendar_include_personal_lists" to JsonPrimitive(cal.includePersonalLists),
        "calendar_follow_other_services" to JsonPrimitive(cal.followOtherServices),
        "calendar_window_days" to JsonPrimitive(cal.windowDays),
        "calendar_zone_override" to JsonPrimitive(cal.zoneOverride ?: ""),
        "calendar_corner_message" to JsonPrimitive(cal.cornerMessage),
        "calendar_coming_up_row" to JsonPrimitive(cal.comingUpRow),
        "calendar_mdblist" to JsonPrimitive(cal.useMdbListCalendar),
        "calendar_tmdb_soon" to JsonPrimitive(cal.useTmdbSoonCalendar)
    ) + (if (NuvioCFeatures.CLOUD_SETTINGS_ALL) extraLocal() else emptyMap())

    /** [fork] round 51 cloud_settings_all: controls style and button orders, night mode switches, the TheTVDB key. Never the passthrough switches (this TV only). */
    private fun extraLocal(): Map<String, JsonPrimitive> = mapOf(
        "osd_style" to JsonPrimitive(osdPrefs.getString("controls_style", "nuvio_c") ?: "nuvio_c"),
        "osd_order" to JsonPrimitive(osdPrefs.getString("button_order", "") ?: ""),
        "clean_order" to JsonPrimitive(osdPrefs.getString("clean_order", "") ?: ""),
        "night_enabled" to JsonPrimitive(audioPrefs.getBoolean("night_enabled", true)),
        "night_persist" to JsonPrimitive(audioPrefs.getBoolean("night_persist", false)),
        "tvdb_key" to JsonPrimitive(keyPrefs.getString("tvdb", "") ?: "")
    ) + (if (NuvioCFeatures.CLOUD_SETTINGS_MORE) moreLocal() else emptyMap())

    // [fork] round 52 cloud_settings_more: the night mode schedule and this profile's Roulette choices travel too
    private val shufflePrefs: SharedPreferences by lazy { context.getSharedPreferences("nuvio_c_shuffle", Context.MODE_PRIVATE) }
    private fun rk(key: String) = "p${profiles.activeProfileId.value}:$key"
    private fun moreLocal(): Map<String, JsonPrimitive> = mapOf(
        "night_auto" to JsonPrimitive(audioPrefs.getBoolean("night_auto", false)),
        "night_from_min" to JsonPrimitive(audioPrefs.getInt("night_from_min", 18 * 60)),
        "night_until_min" to JsonPrimitive(audioPrefs.getInt("night_until_min", 8 * 60)),
        "night_auto_level" to JsonPrimitive(audioPrefs.getString("night_auto_level", "medium") ?: "medium"),
        "roulette_mood" to JsonPrimitive(shufflePrefs.getString(rk("mood"), "") ?: ""),
        "roulette_kind" to JsonPrimitive(shufflePrefs.getString(rk("kind"), "") ?: ""),
        "roulette_watch_filter" to JsonPrimitive(shufflePrefs.getString(rk("watch_filter"), "") ?: ""),
        "roulette_refine" to JsonPrimitive(shufflePrefs.getString(rk("refine"), "") ?: "")
    )

    private suspend fun apply(v: Map<String, JsonPrimitive>) {
        prefs.edit {
            v["bg_trailer_muted"]?.booleanOrNull?.let { putBoolean("bg_trailer_muted", it) }
            v["prefer_imdb_trailers"]?.booleanOrNull?.let { putBoolean("prefer_imdb_trailers", it) }
            v["prefer_sdh_subtitles"]?.booleanOrNull?.let { putBoolean("prefer_sdh_subtitles", it) }
            v["subtitle_font"]?.contentOrNull?.let { putString("subtitle_font", it) }
        }
        v["calendar_start"]?.contentOrNull
            ?.let { runCatching { NuvioCCalendarStart.valueOf(it) }.getOrNull() }
            ?.let { if (it != calendarView.start.value) calendarView.setStart(it) }
        val cal = calendar.current()
        v["calendar_menu_visible"]?.booleanOrNull?.let { if (it != cal.menuVisible) calendar.setMenuVisible(it) }
        v["calendar_include_personal_lists"]?.booleanOrNull?.let { if (it != cal.includePersonalLists) calendar.setIncludePersonalLists(it) }
        v["calendar_follow_other_services"]?.booleanOrNull?.let { if (it != cal.followOtherServices) calendar.setFollowOtherServices(it) }
        v["calendar_window_days"]?.intOrNull?.let { if (normalizeCalendarWindowDays(it) != cal.windowDays) calendar.setWindowDays(it) }
        v["calendar_zone_override"]?.contentOrNull?.let { zone ->
            val wanted = normalizeCalendarZoneOverride(zone)
            if (wanted != cal.zoneOverride) calendar.setZoneOverride(wanted)
        }
        v["calendar_corner_message"]?.booleanOrNull?.let { if (it != cal.cornerMessage) calendar.setCornerMessage(it) }
        v["calendar_coming_up_row"]?.booleanOrNull?.let { if (it != cal.comingUpRow) calendar.setComingUpRow(it) }
        v["calendar_mdblist"]?.booleanOrNull?.let { if (it != cal.useMdbListCalendar) calendar.setUseMdbListCalendar(it) }
        v["calendar_tmdb_soon"]?.booleanOrNull?.let { if (it != cal.useTmdbSoonCalendar) calendar.setUseTmdbSoonCalendar(it) }
        if (NuvioCFeatures.CLOUD_SETTINGS_ALL) {
            osdPrefs.edit {
                v["osd_style"]?.contentOrNull?.takeIf { it == "nuvio_c" || it == "clean" }?.let { putString("controls_style", it) }
                v["osd_order"]?.contentOrNull?.let { if (it.isBlank()) remove("button_order") else putString("button_order", it) }
                v["clean_order"]?.contentOrNull?.let { if (it.isBlank()) remove("clean_order") else putString("clean_order", it) }
            }
            audioPrefs.edit {
                v["night_enabled"]?.booleanOrNull?.let { putBoolean("night_enabled", it) }
                v["night_persist"]?.booleanOrNull?.let { putBoolean("night_persist", it) }
            }
            v["tvdb_key"]?.contentOrNull?.let { com.nuvio.tv.core.nuvioc.NuvioCApiKeys.setTvdb(context, it) }
        }
        if (NuvioCFeatures.CLOUD_SETTINGS_MORE) { // [fork] round 52: a key the entry doesn't have keeps this TV's value
            audioPrefs.edit {
                v["night_auto"]?.booleanOrNull?.let { putBoolean("night_auto", it) }
                v["night_from_min"]?.intOrNull?.let { putInt("night_from_min", it.coerceIn(0, 24 * 60 - 1)) }
                v["night_until_min"]?.intOrNull?.let { putInt("night_until_min", it.coerceIn(0, 24 * 60 - 1)) }
                v["night_auto_level"]?.contentOrNull?.takeIf { it in setOf("light", "medium", "strong") }?.let { putString("night_auto_level", it) }
            }
            shufflePrefs.edit {
                listOf("mood", "kind", "watch_filter", "refine").forEach { key ->
                    v["roulette_$key"]?.contentOrNull?.let { if (it.isBlank()) remove(rk(key)) else putString(rk(key), it) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "NuvioCCloudSettings"
        private const val PREFS = "nuvio_c_prefs"
        private const val VIEW_PREFS = "nuvio_c_calendar_view"
        private const val LAYOUT = "layout_settings"
        private val ENTRY = stringPreferencesKey("nuvio_c_settings")
        private const val MIGRATED = "per_profile_migrated" // [fork] round 56

        /** Same keys and order every time, so equal settings give an equal string. */
        fun encode(values: Map<String, JsonPrimitive>): String =
            JsonObject(values.toSortedMap() + ("v" to JsonPrimitive(1))).toString()

        fun decode(text: String): Map<String, JsonPrimitive>? = runCatching {
            (Json.parseToJsonElement(text) as JsonObject).mapNotNull { (k, v) -> (v as? JsonPrimitive)?.let { k to it } }.toMap()
        }.getOrNull()

        /**
         * [fork] round 56: what a profile's settings start from when it becomes active: its own copy on this TV, else
         * its cloud copy, else (first run with this build) nothing, so this TV's current values stay, else the defaults
         * without Calendar and Roulette keys (those stores are already per profile). Pure, for tests.
         */
        fun seedFor(own: String?, remote: String?, migrated: Boolean): Map<String, JsonPrimitive>? = when {
            own != null -> decode(own)
            remote != null -> decode(remote)
            !migrated -> null
            else -> decode(DEFAULTS)?.filterKeys { !it.startsWith("calendar_") && !it.startsWith("roulette_") }
        }

        /** Every travelling setting at its default (fresh install). */
        val DEFAULTS: String = encode(
            sortedMapOf(
                "bg_trailer_muted" to JsonPrimitive(true),
                "prefer_imdb_trailers" to JsonPrimitive(false),
                "prefer_sdh_subtitles" to JsonPrimitive(false),
                "subtitle_font" to JsonPrimitive(""),
                "calendar_start" to JsonPrimitive(NuvioCCalendarStart.LAST_USED.name),
                "calendar_menu_visible" to JsonPrimitive(CalendarSettings().menuVisible),
                "calendar_include_personal_lists" to JsonPrimitive(CalendarSettings().includePersonalLists),
                "calendar_follow_other_services" to JsonPrimitive(CalendarSettings().followOtherServices),
                "calendar_window_days" to JsonPrimitive(CalendarSettings().windowDays),
                "calendar_zone_override" to JsonPrimitive(""),
                "calendar_corner_message" to JsonPrimitive(CalendarSettings().cornerMessage),
                "calendar_coming_up_row" to JsonPrimitive(CalendarSettings().comingUpRow),
                "calendar_mdblist" to JsonPrimitive(CalendarSettings().useMdbListCalendar),
                "calendar_tmdb_soon" to JsonPrimitive(CalendarSettings().useTmdbSoonCalendar)
            ) + (if (NuvioCFeatures.CLOUD_SETTINGS_ALL) mapOf(
                "osd_style" to JsonPrimitive("nuvio_c"),
                "osd_order" to JsonPrimitive(""),
                "clean_order" to JsonPrimitive(""),
                "night_enabled" to JsonPrimitive(true),
                "night_persist" to JsonPrimitive(false),
                "tvdb_key" to JsonPrimitive("")
            ) else emptyMap()) + (if (NuvioCFeatures.CLOUD_SETTINGS_MORE) mapOf(
                "night_auto" to JsonPrimitive(false),
                "night_from_min" to JsonPrimitive(18 * 60),
                "night_until_min" to JsonPrimitive(8 * 60),
                "night_auto_level" to JsonPrimitive("medium"),
                "roulette_mood" to JsonPrimitive(""),
                "roulette_kind" to JsonPrimitive(""),
                "roulette_watch_filter" to JsonPrimitive(""),
                "roulette_refine" to JsonPrimitive("")
            ) else emptyMap())
        )

        private fun prefsTicks(p: SharedPreferences) = callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || !(key.startsWith("cloud_last_") || key.startsWith("local_p") || key == MIGRATED)) trySend(Unit)
            }
            p.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
        }.onStart { emit(Unit) }
    }
}
