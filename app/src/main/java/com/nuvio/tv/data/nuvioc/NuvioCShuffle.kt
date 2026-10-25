package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 28 `shuffle_page`: the models, settings and pure picking rules behind Shuffle.
// Idea from TitansparkDev/NuvioTV's "Surprise me" (GPL-3.0); rebuilt for Nuvio C (see NuvioCShufflePool).
//
// Randomness: every roll is a fresh draw from SecureRandom. First a source, weighted by the settings
// (no fixed rotation), then one title from that source, every title equally likely. A source with
// nothing left after the filters sits out that draw, so the others keep their proportions. The only
// rule that isn't random: the last 40 picks are skipped, so the same titles don't come straight back.

import android.content.Context
import androidx.core.content.edit
import com.nuvio.tv.core.profile.ProfileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Where picks come from, in the order duplicates are credited (a title found by several counts once). */
enum class NuvioCShuffleSource(val key: String, val defaultWeight: Int) {
    FOR_YOU("for_you", 3),
    TRAKT("trakt", 2),
    ADDON("addon", 1),
    HOME("home", 0),
    LIBRARY("library", 0)
}

enum class NuvioCShuffleKind(val key: String) { MOVIES("movies"), SHOWS("shows"), EITHER("either") }

/** Moods filter by genre (names lower-cased, "-" as space). ANY doesn't filter. */
enum class NuvioCShuffleMood(val key: String, val genres: Set<String>) {
    ANY("any", emptySet()),
    FEEL_GOOD("feel_good", setOf("comedy", "family", "romance", "animation", "kids")),
    GRIPPING("gripping", setOf("thriller", "action", "crime", "action & adventure", "suspense")),
    FUNNY("funny", setOf("comedy")),
    DARK("dark", setOf("horror", "crime", "war", "war & politics")),
    MIND_BENDING("mind_bending", setOf("science fiction", "sci fi & fantasy", "sci fi", "mystery"))
}

/** One title in the pool. [id] is what opens its details page (IMDb when known, else "tmdb:<id>"). */
@Serializable
data class NuvioCShuffleItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val year: String? = null,
    val released: String? = null,
    val genres: List<String> = emptyList(),
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val from: String = "",
    val addonBaseUrl: String? = null
) {
    val isShow: Boolean get() = type == "series"

    /** Every id this title may be stored under in watch history. */
    fun ids(): List<String> = listOfNotNull(id, imdbId, tmdbId?.let { "tmdb:$it" }).distinct()

    /** Identity across sources: IMDb, else TMDB, else the source id. */
    val identity: String get() = (if (isShow) "s:" else "m:") + (imdbId ?: tmdbId?.let { "tmdb$it" } ?: id)
}

@Serializable
data class NuvioCShufflePoolFile(
    val profile: Int,
    val builtAt: Long,
    val signature: String,
    val items: Map<String, List<NuvioCShuffleItem>>
)

data class NuvioCShuffleConfig(
    val menuVisible: Boolean = true,
    val weights: Map<NuvioCShuffleSource, Int> = NuvioCShuffleSource.entries.associateWith { it.defaultWeight },
    /** Add-on catalogs ticked for the add-on source; null = automatic (catalogs that look like recommendations). */
    val catalogKeys: Set<String>? = null,
    val kind: NuvioCShuffleKind = NuvioCShuffleKind.MOVIES,
    val mood: NuvioCShuffleMood = NuvioCShuffleMood.ANY,
    val unwatched: Boolean = true
) {
    fun weight(source: NuvioCShuffleSource): Int = (weights[source] ?: source.defaultWeight).coerceIn(0, 3)

    /** Changes when what goes into the pool changes (kind / mood / filters don't, they apply at draw time). */
    val poolSignature: String
        get() = NuvioCShuffleSource.entries.joinToString(",") { "${it.key}=${if (weight(it) > 0) 1 else 0}" } +
            "|" + (catalogKeys?.sorted()?.joinToString(";") ?: "auto")
}

fun normalizeShuffleGenre(genre: String): String = genre.trim().lowercase().replace('-', ' ').replace("  ", " ")

fun nuvioCMoodMatches(item: NuvioCShuffleItem, mood: NuvioCShuffleMood): Boolean =
    mood == NuvioCShuffleMood.ANY || item.genres.any { it in mood.genres }

fun nuvioCKindMatches(item: NuvioCShuffleItem, kind: NuvioCShuffleKind): Boolean = when (kind) {
    NuvioCShuffleKind.MOVIES -> !item.isShow
    NuvioCShuffleKind.SHOWS -> item.isShow
    NuvioCShuffleKind.EITHER -> true
}

/**
 * One draw. [eligible] gives each source's titles that pass the filters. Returns null when every
 * weighted source is empty. Weighted source first, then a uniform title.
 */
fun nuvioCDraw(
    eligible: Map<NuvioCShuffleSource, List<NuvioCShuffleItem>>,
    weights: (NuvioCShuffleSource) -> Int,
    random: java.util.Random
): Pair<NuvioCShuffleSource, NuvioCShuffleItem>? {
    val live = eligible.filter { (source, items) -> items.isNotEmpty() && weights(source) > 0 }
    if (live.isEmpty()) return null
    val total = live.keys.sumOf { weights(it) }
    var ticket = random.nextInt(total)
    val source = live.keys.first { s -> ticket -= weights(s); ticket < 0 }
    val items = live.getValue(source)
    return source to items[random.nextInt(items.size)]
}

internal val shuffleRandom: java.util.Random by lazy { SecureRandom() }

/** Settings, last choices and recent picks, per Nuvio profile, on this TV. */
@Singleton
class NuvioCShufflePrefs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: ProfileManager
) {
    private val prefs by lazy { context.getSharedPreferences("nuvio_c_shuffle", Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _config = MutableStateFlow(NuvioCShuffleConfig())
    val config: StateFlow<NuvioCShuffleConfig> = _config.asStateFlow()

    init {
        scope.launch { profiles.activeProfileId.collect { _config.value = read(it) } }
    }

    private fun k(profile: Int, key: String) = "p$profile:$key"
    private val profile: Int get() = profiles.activeProfileId.value

    private fun read(p: Int): NuvioCShuffleConfig = runCatching {
        NuvioCShuffleConfig(
            menuVisible = prefs.getBoolean(k(p, "menu"), true),
            weights = NuvioCShuffleSource.entries.associateWith { prefs.getInt(k(p, "w_${it.key}"), it.defaultWeight).coerceIn(0, 3) },
            catalogKeys = prefs.getStringSet(k(p, "catalogs"), null)?.toSet(),
            kind = NuvioCShuffleKind.entries.firstOrNull { it.key == prefs.getString(k(p, "kind"), null) } ?: NuvioCShuffleKind.MOVIES,
            mood = NuvioCShuffleMood.entries.firstOrNull { it.key == prefs.getString(k(p, "mood"), null) } ?: NuvioCShuffleMood.ANY,
            unwatched = prefs.getBoolean(k(p, "unwatched"), true)
        )
    }.getOrDefault(NuvioCShuffleConfig())

    private fun update(block: android.content.SharedPreferences.Editor.(Int) -> Unit) {
        val p = profile
        prefs.edit { block(p) }
        _config.value = read(p)
    }

    fun setMenuVisible(visible: Boolean) = update { putBoolean(k(it, "menu"), visible) }
    fun setWeight(source: NuvioCShuffleSource, weight: Int) = update { putInt(k(it, "w_${source.key}"), weight.coerceIn(0, 3)) }
    fun setCatalogKeys(keys: Set<String>?) = update { if (keys == null) remove(k(it, "catalogs")) else putStringSet(k(it, "catalogs"), keys) }
    fun setKind(kind: NuvioCShuffleKind) = update { putString(k(it, "kind"), kind.key) }
    fun setMood(mood: NuvioCShuffleMood) = update { putString(k(it, "mood"), mood.key) }
    fun setUnwatched(on: Boolean) = update { putBoolean(k(it, "unwatched"), on) }

    /** The last [RECENT_MAX] picks (identities), newest last. */
    fun recent(): List<String> =
        prefs.getString(k(profile, "recent"), null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun addRecent(identity: String) {
        val list = (recent().filter { it != identity } + identity).takeLast(RECENT_MAX)
        prefs.edit { putString(k(profile, "recent"), list.joinToString("\n")) }
    }

    companion object {
        const val RECENT_MAX = 40
    }
}
