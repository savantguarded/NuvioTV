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
    val selected: NuvioCCastPerson? = null,
    val bio: NuvioCCastBio? = null,
    val bioLoading: Boolean = false,
    /** List key of the person last opened, so focus returns to them when coming back from their view. */
    val lastKey: String? = null
)

@HiltViewModel
class NuvioCOsdCastViewModel @Inject constructor(
    @Named("tmdb") retrofit: Retrofit,
    private val tmdbService: TmdbService
) : ViewModel() {
    private val api = retrofit.create(NuvioCOsdCastTmdbApi::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val episodeCache = HashMap<String, List<NuvioCCastPerson>>()
    private val bioCache = HashMap<Int, NuvioCCastBio>()
    private val _ui = MutableStateFlow(NuvioCOsdCastUi())
    val ui = _ui.asStateFlow()

    /** Fills the panel: the cast the player has, plus this episode's guest stars for a show. */
    fun load(cast: List<MetaCastMember>, contentId: String?, season: Int?, episode: Int?) {
        val regulars = cast.map { NuvioCCastPerson(it.name, it.character?.takeIf(String::isNotBlank), it.photo, it.tmdbId) }
        _ui.value = NuvioCOsdCastUi(regulars = regulars)
        if (contentId == null || season == null || episode == null) return
        val key = "$contentId:$season:$episode"
        episodeCache[key]?.let { cached -> _ui.value = _ui.value.copy(guests = cached.notIn(regulars)); return }
        viewModelScope.launch {
            val guests = runCatching {
                val tvId = contentId.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
                    ?: contentId.takeIf { it.startsWith("tt") }?.substringBefore(':')?.let { tmdbService.imdbToTmdb(it, "tv") }
                    ?: return@runCatching emptyList()
                val body = api.episodeCredits(tvId, season, episode, tmdbService.apiKey()).takeIf { it.isSuccessful }?.body()?.use { it.string() }
                    ?: return@runCatching emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                (root["guest_stars"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toPerson() }
            }.getOrDefault(emptyList())
            episodeCache[key] = guests
            _ui.value = _ui.value.copy(guests = guests.notIn(regulars))
        }
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

    /** TMDB person search: the exact-name match with the highest popularity, or null. */
    private suspend fun lookupByName(name: String): Int? {
        val key = name.lowercase()
        if (nameIds.containsKey(key)) return nameIds[key]
        val found = runCatching {
            val body = api.searchPerson(tmdbService.apiKey(), name).takeIf { it.isSuccessful }?.body()?.use { it.string() } ?: return@runCatching null
            val results = (json.parseToJsonElement(body).jsonObject["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            results.filter { it.str("name")?.equals(name, ignoreCase = true) == true }
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
    onClosed: () -> Unit
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
    Dialog(
        onDismissRequest = { if (ui.selected != null) viewModel.back() else close() },
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
                    if (selected == null) CastList(ui) { person, key -> viewModel.select(person, key) } else PersonView(selected, ui.bio, ui.bioLoading)
                }
            }
        }
    }
}

@Composable
private fun CastList(ui: NuvioCOsdCastUi, onSelect: (NuvioCCastPerson, String) -> Unit) {
    val listState = rememberLazyListState()
    val guestKeys = ui.guests.mapIndexed { i, p -> "g$i${p.name}" }
    val regularKeys = ui.regulars.mapIndexed { i, p -> "r$i${p.name}" }
    // Coming back from a person's view, focus lands on that person again; otherwise on the first row.
    val firstKey = guestKeys.firstOrNull() ?: regularKeys.firstOrNull()
    val targetKey = ui.lastKey?.takeIf { it in guestKeys || it in regularKeys } ?: firstKey
    val target = remember { FocusRequester() }
    val headerOffset = if (ui.guests.isNotEmpty()) 1 else 0
    val targetIndex = when {
        targetKey == null -> 0
        targetKey in guestKeys -> headerOffset + guestKeys.indexOf(targetKey)
        else -> headerOffset + guestKeys.size + headerOffset + regularKeys.indexOf(targetKey)
    }
    LaunchedEffect(targetKey) {
        if (targetIndex > 1) listState.scrollToItem((targetIndex - 1).coerceAtLeast(0))
        target.requestFocusAfterFrames()
    }
    Column(Modifier.fillMaxSize().padding(NuvioTheme.spacing.xl)) {
        Text(
            text = stringResource(R.string.nuvio_c_osd_cast),
            style = MaterialTheme.typography.headlineSmall,
            color = NuvioTheme.colors.TextPrimary
        )
        Spacer(Modifier.height(NuvioTheme.spacing.md))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (ui.guests.isNotEmpty()) {
                item(key = "h_guests") { GroupLabel(stringResource(R.string.nuvio_c_osd_cast_episode)) }
                itemsIndexed(ui.guests, key = { i, p -> "g$i${p.name}" }) { i, p ->
                    PersonRow(p, guestKeys[i], if (guestKeys[i] == targetKey) target else null, onSelect)
                }
                item(key = "h_regulars") { GroupLabel(stringResource(R.string.nuvio_c_osd_cast_regulars)) }
            }
            itemsIndexed(ui.regulars, key = { i, p -> "r$i${p.name}" }) { i, p ->
                PersonRow(p, regularKeys[i], if (regularKeys[i] == targetKey) target else null, onSelect)
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
                com.nuvio.tv.ui.screens.cast.NuvioCTurnTakingLines(
                    title = person.name,
                    role = person.character,
                    focused = focused,
                    titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    roleStyle = MaterialTheme.typography.bodySmall,
                    roleColor = NuvioTheme.colors.TextSecondary,
                    titleTopPadding = 0.dp,
                    sidePadding = 0.dp
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
private fun PersonView(person: NuvioCCastPerson, bio: NuvioCCastBio?, loading: Boolean) {
    val holder = remember { FocusRequester() }
    val seeMore = remember { FocusRequester() }
    val context = LocalContext.current
    var expanded by remember(person) { mutableStateOf(false) }
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
                    expanded = !expanded
                    if (!expanded) scope.launch { scroll.scrollTo(0) }
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
