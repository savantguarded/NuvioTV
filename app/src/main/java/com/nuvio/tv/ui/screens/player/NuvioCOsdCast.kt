package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 28 `osd_cast`: a Cast button in the player's main row and a side panel listing the cast.
//
// Why it doesn't slow the OSD: the button is one more icon, shown only when the title has cast (the player
// already holds the cast list from your meta add-on, so nothing is fetched for the button). The panel lives
// in its own dialog window and is only built when you press Cast; face pictures (185 px) load then. For an
// episode, its guest stars come from TMDB on opening (cached per episode). OK on a person shows their short
// bio and four known-for posters in the panel (fetched then, cached). Playback carries on behind it; Back
// steps out of a person, then closes the panel, and focus returns to the Cast button.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.nuvio.tv.ui.util.nuvioCListGate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.domain.model.MetaCastMember
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Named

/** Open / closed, shared between the button (in the controls row) and the panel (over the player). */
@Stable
internal class NuvioCOsdCastState {
    var open by mutableStateOf(false)
}

internal val LocalNuvioCOsdCast = staticCompositionLocalOf<NuvioCOsdCastState?> { null }

internal interface NuvioCOsdCastTmdbApi {
    @GET("tv/{tv_id}/season/{season}/episode/{episode}/credits")
    suspend fun episodeCredits(
        @Path("tv_id") tvId: Int,
        @Path("season") season: Int,
        @Path("episode") episode: Int,
        @Query("api_key") apiKey: String
    ): Response<ResponseBody>

    @GET("person/{person_id}")
    suspend fun person(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String = "combined_credits",
        @Query("language") language: String = "en-US"
    ): Response<ResponseBody>

    /** [fork] round 34 osd_cast_tmdb: the show's whole cast across seasons. */
    @GET("tv/{tv_id}/aggregate_credits")
    suspend fun tvAggregateCredits(@Path("tv_id") tvId: Int, @Query("api_key") apiKey: String): Response<ResponseBody>

    /** [fork] round 34 osd_cast_tmdb: a film's cast. */
    @GET("movie/{movie_id}/credits")
    suspend fun movieCredits(@Path("movie_id") movieId: Int, @Query("api_key") apiKey: String): Response<ResponseBody>

    /** For cast the meta add-on gave no TMDB id for (round 29): found by name. */
    @GET("search/person")
    suspend fun searchPerson(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false
    ): Response<ResponseBody>
}

data class NuvioCCastPerson(val name: String, val character: String?, val photo: String?, val tmdbId: Int?)

/** [biography] is TMDB's text; [summary] is the fallback line (known for / born) used when TMDB has no biography. */
data class NuvioCCastBio(val tmdbId: Int?, val biography: String?, val knownFor: List<String>, val summary: String? = null)

data class NuvioCOsdCastUi(
    val guests: List<NuvioCCastPerson> = emptyList(),
    val regulars: List<NuvioCCastPerson> = emptyList(),
    /** [fork] round 46 osd_crew: director and writers (the episode's for a show, the film's for a film), role in [NuvioCCastPerson.character]. */
    val crew: List<NuvioCCastPerson> = emptyList(),
    val selected: NuvioCCastPerson? = null,
    val bio: NuvioCCastBio? = null,
    val bioLoading: Boolean = false,
    /** List key of the person last opened, so focus returns to them when coming back from their view. */
    val lastKey: String? = null,
    /** [fork] round 31 osd_about_tab: 0 = About, 1 = Cast; remembered per title like [lastKey]. */
    val tab: Int = if (NuvioCFeatures.OSD_ABOUT_TAB) 0 else 1
)

@HiltViewModel
class NuvioCOsdCastViewModel @Inject constructor(
    @Named("tmdb") retrofit: Retrofit,
    private val tmdbService: TmdbService,
    private val metaRepository: com.nuvio.tv.domain.repository.MetaRepository // [fork] round 34 osd_cast_tmdb
) : ViewModel() {
    private val api = retrofit.create(NuvioCOsdCastTmdbApi::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val episodeCache = HashMap<String, List<NuvioCCastPerson>>()
    private val bioCache = HashMap<Int, NuvioCCastBio>()
    private val _ui = MutableStateFlow(NuvioCOsdCastUi())
    val ui = _ui.asStateFlow()

    /** Fills the panel: the cast the player has, plus this episode's guest stars for a show. */
    // [fork] round 31: reopening the panel for the same title keeps the tab and the last person opened
    // (before, every open reset them, so coming back never landed where you had been).
    private var loadedKey: String? = null

    fun load(cast: List<MetaCastMember>, contentId: String?, season: Int?, episode: Int?) {
        val regulars = cast.map { NuvioCCastPerson(it.name, it.character?.takeIf(String::isNotBlank), it.photo, it.tmdbId) }
        val titleKey = "$contentId:$season:$episode"
        val previous = _ui.value
        val same = loadedKey == titleKey
        loadedKey = titleKey
        _ui.value = NuvioCOsdCastUi(
            regulars = regulars,
            guests = if (same) previous.guests else emptyList(),
            crew = if (same) previous.crew else emptyList(),
            lastKey = if (same) previous.lastKey else null,
            tab = if (same) previous.tab else NuvioCOsdCastUi().tab
        )
        if (NuvioCFeatures.OSD_CAST_TMDB && contentId != null) loadTmdbCast(regulars, contentId, isShow = season != null, titleKey)
        if (contentId == null || season == null || episode == null) return
        val key = "$contentId:$season:$episode"
        episodeCache[key]?.let { cached ->
            _ui.value = _ui.value.copy(guests = cached.notIn(regulars), crew = if (NuvioCFeatures.OSD_CREW) crewCache[key].orEmpty() else emptyList())
            return
        }
        viewModelScope.launch {
            val guests = runCatching {
                val tvId = contentId.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
                    ?: contentId.takeIf { it.startsWith("tt") }?.substringBefore(':')?.let { tmdbService.imdbToTmdb(it, "tv") }
                    ?: return@runCatching emptyList()
                val body = api.episodeCredits(tvId, season, episode, tmdbService.apiKey()).takeIf { it.isSuccessful }?.body()?.use { it.string() }
                    ?: return@runCatching emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                crewCache[key] = parseCrew(root) // [fork] round 46 osd_crew: same response, no extra request
                (root["guest_stars"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toPerson() }
            }.getOrDefault(emptyList())
            episodeCache[key] = guests
            _ui.value = _ui.value.copy(guests = guests.notIn(regulars), crew = if (NuvioCFeatures.OSD_CREW) crewCache[key].orEmpty() else _ui.value.crew)
        }
    }

    // ---- [fork] round 34 osd_cast_tmdb ------------------------------------------------------------------
    // The add-on's cast (AIOMetadata with TVDB meta) often lacks character names, photos and TMDB ids, and people
    // without an id were then found by name, which could pick a namesake. TMDB's own cast for the title is now
    // the base list; the add-on only fills in people TMDB doesn't list. Cached per title.
    private val tmdbCastCache = HashMap<String, List<NuvioCCastPerson>>()
    /** [fork] round 46 osd_crew: crew per episode key or per film base key. */
    private val crewCache = HashMap<String, List<NuvioCCastPerson>>()

    /** Directors first, then writers (Screenplay / Writer / Teleplay / Story), one row per person with their jobs joined. */
    private fun parseCrew(root: JsonObject): List<NuvioCCastPerson> {
        val crew = (root["crew"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val wanted = listOf("Director", "Screenplay", "Writer", "Teleplay", "Story")
        data class Row(val name: String, val id: Int?, val photo: String?, val jobs: LinkedHashSet<String>, val rank: Int)
        val rows = LinkedHashMap<String, Row>()
        crew.forEach { o ->
            val job = o.str("job") ?: return@forEach
            val rank = wanted.indexOf(job).takeIf { it >= 0 } ?: return@forEach
            val name = o.str("name") ?: return@forEach
            val label = if (rank == 0) "Director" else "Writer"
            val k = (o.int("id")?.toString() ?: name.lowercase())
            val existing = rows[k]
            if (existing == null) {
                rows[k] = Row(name, o.int("id"), o.str("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" }, linkedSetOf(label), rank)
            } else {
                existing.jobs += label
                if (rank < existing.rank) rows[k] = existing.copy(rank = rank)
            }
        }
        return rows.values.sortedBy { it.rank }.take(4).map { r ->
            NuvioCCastPerson(r.name, r.jobs.sortedBy { if (it == "Director") 0 else 1 }.joinToString(" · "), r.photo, r.id)
        }
    }
    @Volatile private var titleTmdbId: Int? = null

    private fun loadTmdbCast(addon: List<NuvioCCastPerson>, contentId: String, isShow: Boolean, titleKey: String) {
        val baseKey = "${if (isShow) "tv" else "movie"}:${contentId.substringBefore(':')}"
        tmdbCastCache[baseKey]?.let { cached ->
            if (NuvioCFeatures.OSD_CREW && !isShow) _ui.value = _ui.value.copy(crew = crewCache[baseKey].orEmpty())
            applyBase(cached, addon, titleKey)
            return
        }
        viewModelScope.launch {
            val people = runCatching {
                val id = resolveTmdbId(contentId, isShow) ?: return@runCatching null
                titleTmdbId = id
                val response = if (isShow) api.tvAggregateCredits(id, tmdbService.apiKey()) else api.movieCredits(id, tmdbService.apiKey())
                val body = response.takeIf { it.isSuccessful }?.body()?.use { it.string() } ?: return@runCatching null
                val rootObj = json.parseToJsonElement(body).jsonObject
                if (!isShow) crewCache[baseKey] = parseCrew(rootObj) // [fork] round 46 osd_crew: same response
                val cast = (rootObj["cast"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                cast.take(MAX_TMDB_CAST).mapNotNull { o ->
                    val name = o.str("name") ?: return@mapNotNull null
                    val character = o.str("character")
                        ?: ((o["roles"] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("character")
                    NuvioCCastPerson(name, character, o.str("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" }, o.int("id"))
                }
            }.getOrNull() ?: return@launch
            tmdbCastCache[baseKey] = people
            if (NuvioCFeatures.OSD_CREW && !isShow && loadedKey == titleKey) _ui.value = _ui.value.copy(crew = crewCache[baseKey].orEmpty())
            applyBase(people, addon, titleKey)
        }
    }

    private fun applyBase(tmdb: List<NuvioCCastPerson>, addon: List<NuvioCCastPerson>, titleKey: String) {
        if (tmdb.isEmpty() || loadedKey != titleKey) return
        val byName = addon.associateBy { it.name.lowercase() }
        val merged = tmdb.map { p ->
            val a = byName[p.name.lowercase()]
            p.copy(character = p.character ?: a?.character, photo = p.photo ?: a?.photo)
        }
        val tmdbNames = merged.map { it.name.lowercase() }.toHashSet()
        val regulars = merged + addon.filter { it.name.lowercase() !in tmdbNames }
        _ui.value = _ui.value.copy(regulars = regulars, guests = _ui.value.guests.notIn(regulars))
    }

    private suspend fun resolveTmdbId(contentId: String, isShow: Boolean): Int? {
        contentId.takeIf { it.startsWith("tmdb:") }?.removePrefix("tmdb:")?.substringBefore(':')?.toIntOrNull()?.let { return it }
        val type = if (isShow) "series" else "movie"
        val base = contentId.split(':').let { p -> if (p.size >= 2 && !p[0].startsWith("tt")) p.take(2).joinToString(":") else p[0] }
        val imdb = contentId.substringBefore(':').takeIf { it.startsWith("tt") }
            ?: listOf(base, contentId).firstNotNullOfOrNull { k -> runCatching { metaRepository.getCachedMeta(type, k)?.imdbId }.getOrNull() }
                ?.substringBefore(':')?.takeIf { it.startsWith("tt") }
        return kotlinx.coroutines.withTimeoutOrNull(6_000) {
            runCatching { tmdbService.ensureTmdbId(contentId, type, fallbackImdbId = imdb) }.getOrNull()
        }?.toIntOrNull()
    }

    private fun List<NuvioCCastPerson>.notIn(regulars: List<NuvioCCastPerson>): List<NuvioCCastPerson> {
        val names = regulars.map { it.name.lowercase() }.toHashSet()
        return filter { it.name.lowercase() !in names }
    }

    private fun JsonObject.toPerson(): NuvioCCastPerson? {
        val name = str("name") ?: return null
        return NuvioCCastPerson(name, str("character"), str("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" }, int("id"))
    }

    private val nameIds = HashMap<String, Int?>()
    private val nameBios = HashMap<String, NuvioCCastBio>()

    fun select(person: NuvioCCastPerson, key: String? = null) {
        val nameKey = person.name.lowercase()
        val knownId = person.tmdbId ?: nameIds[nameKey]
        val cached = knownId?.let(bioCache::get) ?: nameBios[nameKey]
        _ui.value = _ui.value.copy(selected = person, bio = cached, bioLoading = cached == null, lastKey = key ?: _ui.value.lastKey)
        if (cached != null) return
        viewModelScope.launch {
            val id = knownId ?: lookupByName(person.name)
            val bio = if (id == null) {
                NuvioCCastBio(null, null, emptyList(), null).also { nameBios[nameKey] = it }
            } else {
                bioCache[id] ?: runCatching { fetchBio(id) }.getOrNull()?.also { bioCache[id] = it }
            }
            if (_ui.value.selected === person) _ui.value = _ui.value.copy(bio = bio, bioLoading = false)
        }
    }

    fun back() {
        _ui.value = _ui.value.copy(selected = null, bio = null, bioLoading = false)
    }

    fun setTab(tab: Int) {
        _ui.value = _ui.value.copy(tab = tab)
    }

    /** TMDB person search: the exact-name match with the highest popularity, or null. */
    private suspend fun lookupByName(name: String): Int? {
        val key = name.lowercase()
        if (nameIds.containsKey(key)) return nameIds[key]
        val found = runCatching {
            val body = api.searchPerson(tmdbService.apiKey(), name).takeIf { it.isSuccessful }?.body()?.use { it.string() } ?: return@runCatching null
            val results = (json.parseToJsonElement(body).jsonObject["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val exact = results.filter { it.str("name")?.equals(name, ignoreCase = true) == true }
            val title = titleTmdbId
            if (NuvioCFeatures.OSD_CAST_TMDB && title != null) {
                // [fork] round 34: only someone whose known-for titles include this one, or the only person of that name
                exact.firstOrNull { p -> (p["known_for"] as? JsonArray).orEmpty().any { (it as? JsonObject)?.int("id") == title } }?.int("id")
                    ?: exact.singleOrNull()?.int("id")
            } else exact
                .maxByOrNull { (it["popularity"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0 }
                ?.int("id")
        }.getOrNull()
        nameIds[key] = found
        return found
    }

    private suspend fun fetchBio(id: Int): NuvioCCastBio? {
        val body = api.person(id, tmdbService.apiKey()).takeIf { it.isSuccessful }?.body()?.use { it.string() } ?: return null
        val root = json.parseToJsonElement(body).jsonObject
        val credits = ((root["combined_credits"] as? JsonObject)?.get("cast") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .sortedByDescending { (it["vote_count"] as? JsonPrimitive)?.intOrNull ?: 0 }
            .distinctBy { it.int("id") }
        val posters = credits.filter { it.str("poster_path") != null }.take(4)
            .mapNotNull { c -> c.str("poster_path")?.let { "https://image.tmdb.org/t/p/w185$it" } }
        val biography = root.str("biography")
        val summary = if (biography == null) fallbackSummary(root, credits) else null
        return NuvioCCastBio(id, biography, posters, summary)
    }

    /** Used when TMDB has no biography: what they're known for plus birth / death, from the same TMDB record. */
    private fun fallbackSummary(root: JsonObject, credits: List<JsonObject>): String? {
        val titles = credits.mapNotNull { it.str("title") ?: it.str("name") }.distinct().take(3)
        val lines = buildList {
            if (titles.isNotEmpty()) {
                val list = if (titles.size == 1) titles[0] else titles.dropLast(1).joinToString(", ") + " and " + titles.last()
                add("Known for $list.")
            } else {
                root.str("known_for_department")?.let { add("Known for ${it.lowercase()}.") }
            }
            val born = root.str("birthday")?.let(::prettyDate)
            val place = root.str("place_of_birth")
            if (born != null) add("Born $born${place?.let { " in $it" } ?: ""}.") else if (place != null) add("Born in $place.")
            root.str("deathday")?.let(::prettyDate)?.let { add("Died $it.") }
        }
        return lines.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun prettyDate(iso: String): String? = runCatching {
        java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ENGLISH))
    }.getOrNull()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
}

private const val MAX_TMDB_CAST = 40

/** True when the Cast button should show for this title. */
internal fun nuvioCOsdCastAvailable(cast: List<MetaCastMember>): Boolean = NuvioCFeatures.OSD_CAST && cast.isNotEmpty()

@Composable
internal fun NuvioCOsdCastPanel(
    state: NuvioCOsdCastState,
    cast: List<MetaCastMember>,
    contentId: String?,
    season: Int?,
    episode: Int?,
    hdrDim: Boolean,
    onClosed: () -> Unit,
    about: NuvioCAboutInfo? = null // [fork] round 31 osd_about_tab
) {
    if (!state.open) return
    val viewModel: NuvioCOsdCastViewModel = hiltViewModel()
    LaunchedEffect(cast, contentId, season, episode) { viewModel.load(cast, contentId, season, episode) }
    val ui by viewModel.ui.collectAsState()
    val close = {
        viewModel.back()
        state.open = false
        onClosed()
    }
    // [fork] One Back closes the overlay; only an opened "See more" takes a Back of its own first.
    var bioExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(ui.selected) { bioExpanded = false }
    Dialog(
        onDismissRequest = {
            when {
                bioExpanded -> bioExpanded = false
                // [fork] round 32 osd_cast_back_stack: Back on a person goes back to the list (focus on them)
                NuvioCFeatures.OSD_CAST_BACK_STACK && ui.selected != null -> viewModel.back()
                else -> close()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setWindowAnimations(0)
            window?.setDimAmount(0f)
        }
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(120))) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            }
            AnimatedVisibility(
                visibleState = shown,
                enter = slideInHorizontally(tween(220)) { it },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(460.dp)
                        .nuvioCHdrDim(hdrDim, isolated = true)
                        .clip(RoundedCornerShape(topStart = NuvioTheme.spacing.lg, bottomStart = NuvioTheme.spacing.lg))
                        .background(NuvioTheme.colors.BackgroundElevated)
                ) {
                    val selected = ui.selected
                    val hasCast = ui.regulars.isNotEmpty() || ui.guests.isNotEmpty() || ui.crew.isNotEmpty()
                    if (selected != null) {
                        PersonView(selected, ui.bio, ui.bioLoading, bioExpanded) { bioExpanded = it }
                    } else if (about != null && NuvioCFeatures.OSD_ABOUT_TAB) {
                        // [fork] round 31 osd_about_tab: About | Cast tabs over the same panel
                        NuvioCAboutCastTabs(
                            tab = if (hasCast) ui.tab else 0,
                            hasCast = hasCast,
                            onTab = viewModel::setTab,
                            about = about,
                            castContent = { tabRequester -> CastList(ui, showHeader = false, upRequester = tabRequester) { person, key -> viewModel.select(person, key) } }
                        )
                    } else {
                        CastList(ui) { person, key -> viewModel.select(person, key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CastList(
    ui: NuvioCOsdCastUi,
    showHeader: Boolean = true,
    upRequester: FocusRequester? = null,
    onSelect: (NuvioCCastPerson, String) -> Unit
) {
    val listState = rememberLazyListState()
    // [fork] round 46 osd_crew: one flat list (headers + people) so focus restore works with any mix of groups.
    // Films: Crew (director, writers), then Cast. Shows: This episode (its director and writers, then guest stars),
    // then Regulars.
    val entries = buildList<Pair<String, NuvioCCastPerson?>> {
        val crew = if (NuvioCFeatures.OSD_CREW) ui.crew else emptyList()
        if (ui.guests.isNotEmpty()) {
            add("h_guests" to null)
            crew.forEachIndexed { i, p -> add("c$i${p.name}" to p) }
            ui.guests.forEachIndexed { i, p -> add("g$i${p.name}" to p) }
            add("h_regulars" to null)
        } else if (crew.isNotEmpty()) {
            add("h_crew" to null)
            crew.forEachIndexed { i, p -> add("c$i${p.name}" to p) }
            if (ui.regulars.isNotEmpty()) add("h_cast" to null)
        }
        ui.regulars.forEachIndexed { i, p -> add("r$i${p.name}" to p) }
    }
    val personKeys = entries.filter { it.second != null }.map { it.first }
    // Coming back from a person's view, focus lands on that person again; otherwise on the first row.
    val targetKey = ui.lastKey?.takeIf { it in personKeys } ?: personKeys.firstOrNull()
    val target = remember { FocusRequester() }
    val targetIndex = entries.indexOfFirst { it.first == targetKey }.coerceAtLeast(0)
    LaunchedEffect(targetKey) {
        if (targetIndex > 1) listState.scrollToItem((targetIndex - 1).coerceAtLeast(0))
        target.requestFocusAfterFrames()
    }
    Column(Modifier.fillMaxSize().padding(if (showHeader) NuvioTheme.spacing.xl else 0.dp)) {
        if (showHeader) {
            Text(
                text = stringResource(R.string.nuvio_c_osd_cast),
                style = MaterialTheme.typography.headlineSmall,
                color = NuvioTheme.colors.TextPrimary
            )
            Spacer(Modifier.height(NuvioTheme.spacing.md))
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            modifier = Modifier
                .fillMaxSize()
                .nuvioCListGate() // [fork] round 31 list_repeat_gate
        ) {
            items(entries.size, key = { entries[it].first }, contentType = { if (entries[it].second == null) 0 else 1 }) { i ->
                val (key, person) = entries[i]
                if (person == null) {
                    GroupLabel(
                        when (key) {
                            "h_guests" -> stringResource(R.string.nuvio_c_osd_cast_episode)
                            "h_regulars" -> stringResource(R.string.nuvio_c_osd_cast_regulars)
                            "h_crew" -> stringResource(R.string.nuvio_c_osd_crew)
                            else -> stringResource(R.string.nuvio_c_osd_cast)
                        }
                    )
                } else {
                    PersonRow(person, key, if (key == targetKey) target else null, onSelect)
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = NuvioTheme.colors.TextSecondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonRow(person: NuvioCCastPerson, itemKey: String, focusRequester: FocusRequester?, onSelect: (NuvioCCastPerson, String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = { onSelect(person, itemKey) },
        modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.hasFocus },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1f),
        colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.12f)),
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = shape)
        )
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Face(person.photo, person.name, 52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                // [fork] round 29: long names / characters scroll on focus, one line at a time, like the cast page
                NuvioCCastLines(
                    title = person.name,
                    role = person.character,
                    focused = focused,
                    titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    roleStyle = MaterialTheme.typography.bodySmall,
                    titleColor = NuvioTheme.colors.TextPrimary,
                    roleColor = NuvioTheme.colors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun Face(url: String?, name: String, sizeDp: Int) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(NuvioTheme.colors.BackgroundCard), contentAlignment = Alignment.Center) {
        if (url != null) {
            val request = remember(url, px) { ImageRequest.Builder(context).data(url).size(px, px).crossfade(150).build() }
            AsyncImage(model = request, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.take(1), style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonView(person: NuvioCCastPerson, bio: NuvioCCastBio?, loading: Boolean, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val holder = remember { FocusRequester() }
    val seeMore = remember { FocusRequester() }
    val context = LocalContext.current
    var truncated by remember(person, bio) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val text = bio?.biography ?: bio?.summary
    val hasBio = text != null
    // "See more" takes focus when the bio is cut off; otherwise the view holds focus itself so keys stay in the panel.
    LaunchedEffect(truncated) { if (truncated) seeMore.requestFocusAfterFrames() else holder.requestFocusAfterFrames() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(NuvioTheme.spacing.xl)
            .focusRequester(holder)
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Face(person.photo, person.name, 84)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = NuvioTheme.colors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                person.character?.let {
                    Text(stringResource(R.string.nuvio_c_osd_cast_as, it), style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val bodyText = when {
            text != null -> text
            loading -> " "
            else -> stringResource(R.string.nuvio_c_osd_cast_no_bio)
        }
        if (expanded) {
            // The whole bio scrolls (Up / Down while "See less" is focused); the button stays put below it.
            Box(Modifier.weight(1f, fill = false)) {
                Column(Modifier.verticalScroll(scroll)) {
                    Text(bodyText, style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary)
                }
            }
        } else {
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary,
                maxLines = 9,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { truncated = it.hasVisualOverflow }
            )
        }
        if (truncated || expanded) {
            Surface(
                onClick = {
                    onExpandedChange(!expanded)
                    if (expanded) scope.launch { scroll.scrollTo(0) }
                },
                modifier = Modifier
                    .focusRequester(seeMore)
                    .onPreviewKeyEvent { event ->
                        if (!expanded || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(160f) }; true }
                            Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-160f) }; true }
                            else -> false
                        }
                    },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.10f),
                    contentColor = NuvioTheme.colors.TextPrimary,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black
                )
            ) {
                Text(
                    text = stringResource(if (expanded) R.string.nuvio_c_osd_cast_see_less else R.string.nuvio_c_osd_cast_see_more),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        if (!expanded && !bio?.knownFor.isNullOrEmpty()) {
            Text(
                stringResource(R.string.nuvio_c_osd_cast_known_for).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = NuvioTheme.colors.TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                bio!!.knownFor.forEach { url ->
                    val w = with(LocalDensity.current) { 84.dp.roundToPx() }
                    val h = with(LocalDensity.current) { 126.dp.roundToPx() }
                    val request = remember(url) { ImageRequest.Builder(context).data(url).size(w, h).crossfade(150).build() }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp, 126.dp).clip(RoundedCornerShape(8.dp)).background(NuvioTheme.colors.BackgroundCard)
                    )
                }
            }
        }
    }
}

/**
 * The Cast panel behaves like the Episodes / Sources panels: other overlays close, the controls stay up, the idle
 * timers (controls hide, pause screen) hold off while it is open and start again when it closes.
 */
internal fun PlayerRuntimeController.onCastPanelChanged(open: Boolean) {
    if (castPanelOpen == open) return
    castPanelOpen = open
    if (open) {
        hideControlsJob?.cancel()
        cancelPauseOverlay()
        _uiState.update {
            it.copy(
                showControls = true,
                showAudioOverlay = false,
                showSubtitleOverlay = false,
                showSubtitleStylePanel = false,
                showSubtitleTimingDialog = false,
                showSpeedDialog = false,
                showMoreDialog = false
            )
        }
    } else if (!_uiState.value.isPlaying && userPausedManually) {
        schedulePauseOverlay()
    } else {
        scheduleHideControls()
    }
}
