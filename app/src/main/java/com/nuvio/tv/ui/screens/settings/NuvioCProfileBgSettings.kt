package com.nuvio.tv.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nuvio.tv.domain.model.AmbientModeSettings
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.ui.components.NuvioCTrendingBg
import com.nuvio.tv.ui.screens.ambient.AmbientSlideLoader
import com.nuvio.tv.ui.screens.ambient.ambientCatalogs
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// [fork] Nuvio C round 53d `profile_bg_catalogs`: Settings > Appearance > Profile background. A toggle and a catalog
// picker (up to three), the same as Ambient mode, feed the profile picker's pictures: the catalogs' backdrops, with
// each title's logo and genres at the bottom right over the dark overlay. Off = the TMDB trending pool. Kept on this
// TV (the picker shows before a profile is chosen).

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NuvioCProfileBgEntryPoint {
    fun ambientSlideLoader(): AmbientSlideLoader
    fun addonRepository(): AddonRepository
}

/** Reads the chosen catalogs and hands their pictures to the profile picker pool. */
internal suspend fun nuvioCRefreshProfileBgCatalogs(context: Context) {
    if (!com.nuvio.tv.NuvioCFeatures.PROFILE_BG_CATALOGS || !NuvioCTrendingBg.catalogOn.value) return
    val ep = EntryPointAccessors.fromApplication(context.applicationContext, NuvioCProfileBgEntryPoint::class.java)
    val slides = runCatching { ep.ambientSlideLoader().load(NuvioCTrendingBg.catalogKeys.value) }.getOrDefault(emptyList())
    NuvioCTrendingBg.setCatalogPool(context, slides.map { s ->
        NuvioCTrendingBg.Entry(
            id = s.id,
            title = s.title,
            backdrop = s.backdropUrl,
            logo = s.logoUrl,
            tag = "",
            meta = "",
            line = s.genres.joinToString(" · ")
        )
    })
}

@Composable
internal fun NuvioCProfileBgSettingsCard() {
    if (!com.nuvio.tv.NuvioCFeatures.PROFILE_BG_CATALOGS || !com.nuvio.tv.NuvioCFeatures.PROFILE_BG_BUILTIN) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { NuvioCTrendingBg.loadSettings(context) }
    val ep = remember { EntryPointAccessors.fromApplication(context.applicationContext, NuvioCProfileBgEntryPoint::class.java) }
    val catalogs by remember { ep.addonRepository().getInstalledAddons().map { it.enabledAddons().ambientCatalogs() } }.collectAsState(initial = emptyList())
    val on by NuvioCTrendingBg.catalogOn
    val keys by NuvioCTrendingBg.catalogKeys
    val selected = keys.filter { k -> catalogs.any { it.key == k } }
    var showCatalogs by remember { mutableStateOf(false) }

    SettingsGroupCard(
        modifier = Modifier.fillMaxWidth(),
        title = "Profile Background",
        subtitle = "Pictures behind the profiles when the app starts"
    ) {
        SettingsToggleRow(
            title = "Use catalogs",
            subtitle = if (on) "Backdrops from your chosen catalogs, with logo and genres" else "Off: what's trending on TMDB today",
            checked = on,
            onToggle = {
                NuvioCTrendingBg.setCatalogOn(context, !on)
                if (!on) scope.launch { nuvioCRefreshProfileBgCatalogs(context) }
            }
        )
        if (on) SettingsActionRow(
            title = "Catalogs",
            subtitle = "Up to ${AmbientModeSettings.MAX_CATALOGS}, mixed together",
            value = if (selected.isEmpty()) "Home hero" else "${selected.size} of ${AmbientModeSettings.MAX_CATALOGS}",
            enabled = catalogs.isNotEmpty(),
            onClick = { showCatalogs = true }
        )
    }

    if (showCatalogs) {
        SettingsMultiChoiceDialog(
            title = "Profile Background Catalogs",
            subtitle = "Pick up to ${AmbientModeSettings.MAX_CATALOGS}",
            options = catalogs.map { c -> SettingsPickerOption(c.key, c.title, description = c.source) },
            selectedValues = selected,
            onValuesSelected = { picked ->
                NuvioCTrendingBg.setCatalogKeys(context, picked)
                showCatalogs = false
                scope.launch { nuvioCRefreshProfileBgCatalogs(context) }
            },
            onDismiss = { showCatalogs = false },
            width = 620.dp,
            maxHeight = 460.dp,
            maxSelections = AmbientModeSettings.MAX_CATALOGS
        )
    }
}
