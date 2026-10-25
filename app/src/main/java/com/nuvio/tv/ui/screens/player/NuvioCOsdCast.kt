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
        @Query("append_to_response") append: String = "combined_credits"
    ): Response<ResponseBody>
}

data class NuvioCCastPerson(val name: String, val character: String?, val photo: String?, val tmdbId: Int?)

data class NuvioCCastBio(val tmdbId: Int, val biography: String?, val knownFor: List<String>)

data class NuvioCOsdCastUi(
    val guests: List<NuvioCCastPerson> = emptyList(),
    val regulars: List<NuvioCCastPerson> = emptyList(),
    val selected: NuvioCCastPerson? = null,
    val bio: NuvioCCastBio? = null
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

    fun select(person: NuvioCCastPerson) {
        _ui.value = _ui.value.copy(selected = person, bio = person.tmdbId?.let(bioCache::get))
        val id = person.tmdbId ?: return
        if (bioCache.containsKey(id)) return
        viewModelScope.launch {
            val bio = runCatching {
                val body = api.person(id, tmdbService.apiKey()).takeIf { it.isSuccessful }?.body()?.use { it.string() } ?: return@runCatching null
                val root = json.parseToJsonElement(body).jsonObject
                val credits = ((root["combined_credits"] as? JsonObject)?.get("cast") as? JsonArray).orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .filter { it.str("poster_path") != null }
                    .sortedByDescending { (it["vote_count"] as? JsonPrimitive)?.intOrNull ?: 0 }
                    .distinctBy { it.int("id") }
                    .take(4)
                    .mapNotNull { c -> c.str("poster_path")?.let { "https://image.tmdb.org/t/p/w185$it" } }
                NuvioCCastBio(id, root.str("biography"), credits)
            }.getOrNull() ?: return@launch
            bioCache[id] = bio
            if (_ui.value.selected?.tmdbId == id) _ui.value = _ui.value.copy(bio = bio)
        }
    }

    fun back() {
        _ui.value = _ui.value.copy(selected = null, bio = null)
    }

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
                    if (selected == null) CastList(ui) { viewModel.select(it) } else PersonView(selected, ui.bio)
                }
            }
        }
    }
}

@Composable
private fun CastList(ui: NuvioCOsdCastUi, onSelect: (NuvioCCastPerson) -> Unit) {
    val first = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { first.requestFocusAfterFrames() }
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
                    PersonRow(p, if (i == 0) first else null, onSelect)
                }
                item(key = "h_regulars") { GroupLabel(stringResource(R.string.nuvio_c_osd_cast_regulars)) }
            }
            itemsIndexed(ui.regulars, key = { i, p -> "r$i${p.name}" }) { i, p ->
                PersonRow(p, if (ui.guests.isEmpty() && i == 0) first else null, onSelect)
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
private fun PersonRow(person: NuvioCCastPerson, focusRequester: FocusRequester?, onSelect: (NuvioCCastPerson) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Card(
        onClick = { onSelect(person) },
        modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
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
                Text(person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                person.character?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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

@Composable
private fun PersonView(person: NuvioCCastPerson, bio: NuvioCCastBio?) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocusAfterFrames() }
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(NuvioTheme.spacing.xl)
            .focusRequester(focus)
            .focusable(), // nothing in it is clickable, so the view holds focus itself and keys stay in the panel
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
        val text = bio?.biography
        Text(
            text = text ?: if (bio == null && person.tmdbId != null) " " else stringResource(R.string.nuvio_c_osd_cast_no_bio),
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.colors.TextSecondary,
            maxLines = 9,
            overflow = TextOverflow.Ellipsis
        )
        if (!bio?.knownFor.isNullOrEmpty()) {
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
