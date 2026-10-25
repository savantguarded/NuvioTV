package com.nuvio.tv.ui.screens.settings

// [fork] Nuvio C round 28 `shuffle_page`: Settings > Layout > Shuffle, and the sources dialog
// (also opened from the Blend chip on the Shuffle page).

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.nuvioc.NuvioCShuffleConfig
import com.nuvio.tv.data.nuvioc.NuvioCShufflePool
import com.nuvio.tv.data.nuvioc.NuvioCShufflePrefs
import com.nuvio.tv.data.nuvioc.NuvioCShuffleSource
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.calendar.PanelActionRow
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A catalog the add-on source can use: key, catalog name, add-on name. */
data class NuvioCShuffleCatalogChoice(val key: String, val name: String, val addon: String)

@HiltViewModel
class NuvioCShuffleSettingsViewModel @Inject constructor(
    private val prefs: NuvioCShufflePrefs,
    private val pool: NuvioCShufflePool
) : ViewModel() {
    val config: StateFlow<NuvioCShuffleConfig> = prefs.config

    private val _catalogs = MutableStateFlow<List<NuvioCShuffleCatalogChoice>?>(null)
    val catalogs: StateFlow<List<NuvioCShuffleCatalogChoice>?> = _catalogs.asStateFlow()

    fun setMenuVisible(visible: Boolean) = prefs.setMenuVisible(visible)
    fun cycleWeight(source: NuvioCShuffleSource) = prefs.setWeight(source, (config.value.weight(source) + 1) % 4)
    fun refreshPicks() = pool.refresh()

    fun loadCatalogs() {
        viewModelScope.launch {
            _catalogs.value = pool.browsableCatalogs().map { NuvioCShuffleCatalogChoice(it.first, it.second, it.third) }
        }
    }

    /** Ticks or unticks a catalog. Leaving automatic starts an empty choice with just this one. */
    fun toggleCatalog(key: String) {
        val current = config.value.catalogKeys ?: emptySet()
        val next = if (key in current) current - key else current + key
        prefs.setCatalogKeys(next.ifEmpty { null })
    }

    fun setAutomatic() = prefs.setCatalogKeys(null)
}

@Composable
private fun sourceTitle(source: NuvioCShuffleSource): String = stringResource(
    when (source) {
        NuvioCShuffleSource.FOR_YOU -> R.string.nuvio_c_shuffle_src_for_you
        NuvioCShuffleSource.TRAKT -> R.string.nuvio_c_shuffle_src_trakt
        NuvioCShuffleSource.ADDON -> R.string.nuvio_c_shuffle_src_addon
        NuvioCShuffleSource.HOME -> R.string.nuvio_c_shuffle_src_home
        NuvioCShuffleSource.LIBRARY -> R.string.nuvio_c_shuffle_src_library
    }
)

@Composable
private fun sourceSubtitle(source: NuvioCShuffleSource): String = stringResource(
    when (source) {
        NuvioCShuffleSource.FOR_YOU -> R.string.nuvio_c_shuffle_src_for_you_sub
        NuvioCShuffleSource.TRAKT -> R.string.nuvio_c_shuffle_src_trakt_sub
        NuvioCShuffleSource.ADDON -> R.string.nuvio_c_shuffle_src_addon_sub
        NuvioCShuffleSource.HOME -> R.string.nuvio_c_shuffle_src_home_sub
        NuvioCShuffleSource.LIBRARY -> R.string.nuvio_c_shuffle_src_library_sub
    }
)

@Composable
private fun weightLabel(weight: Int): String =
    if (weight <= 0) stringResource(R.string.nuvio_c_shuffle_weight_off) else stringResource(R.string.nuvio_c_shuffle_weight, weight)

/** Lives in Settings > Layout, after Calendar. */
@Composable
internal fun LayoutShuffleSection(viewModel: NuvioCShuffleSettingsViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsState()
    SettingsToggleRow(
        title = stringResource(R.string.nuvio_c_shuffle_setting_menu),
        subtitle = stringResource(R.string.nuvio_c_shuffle_setting_menu_sub),
        checked = config.menuVisible,
        onToggle = { viewModel.setMenuVisible(!config.menuVisible) }
    )
    SettingsActionRow(
        title = stringResource(R.string.nuvio_c_shuffle_refresh),
        subtitle = stringResource(R.string.nuvio_c_shuffle_refresh_sub),
        onClick = { viewModel.refreshPicks() }
    )
}

@Composable
private fun ShuffleCatalogDialog(viewModel: NuvioCShuffleSettingsViewModel, onDismiss: () -> Unit) {
    val config by viewModel.config.collectAsState()
    val catalogs by viewModel.catalogs.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(catalogs != null) { if (catalogs != null) first.requestFocusAfterFrames() }
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.nuvio_c_shuffle_catalogs),
        subtitle = stringResource(R.string.nuvio_c_shuffle_catalogs_dialog_sub)
    ) {
        val list = catalogs
        if (list == null) {
            Text(stringResource(R.string.nuvio_c_shuffle_loading), style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary)
            return@NuvioDialog
        }
        val chosen = config.catalogKeys
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
        ) {
            item(key = "auto") {
                PanelActionRow(
                    label = (if (chosen == null) "✓  " else "     ") + stringResource(R.string.nuvio_c_shuffle_catalogs_auto_long),
                    onClick = viewModel::setAutomatic,
                    focusRequester = first
                )
            }
            itemsIndexed(list, key = { _, c -> c.key }) { _, c ->
                PanelActionRow(
                    label = (if (chosen?.contains(c.key) == true) "✓  " else "     ") + "${c.name} · ${c.addon}",
                    onClick = { viewModel.toggleCatalog(c.key) }
                )
            }
        }
    }
}

/** Which sources go into Blend and how often each gets the turn. OK on a row steps Off, ×1, ×2, ×3. */
@Composable
internal fun NuvioCShuffleSourcesDialog(onDismiss: () -> Unit, viewModel: NuvioCShuffleSettingsViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsState()
    val first = remember { FocusRequester() }
    var catalogsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { first.requestFocusAfterFrames() }
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.nuvio_c_shuffle_blend_title),
        subtitle = stringResource(R.string.nuvio_c_shuffle_blend_sub),
        width = 640.dp
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
        ) {
            NuvioCShuffleSource.entries.forEachIndexed { index, source ->
                item(key = source.name) {
                    SettingsActionRow(
                        title = sourceTitle(source),
                        subtitle = sourceSubtitle(source),
                        value = weightLabel(config.weight(source)),
                        onClick = { viewModel.cycleWeight(source) },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier
                    )
                }
                if (source == NuvioCShuffleSource.ADDON && config.weight(source) > 0) {
                    item(key = "catalogs") {
                        SettingsActionRow(
                            title = stringResource(R.string.nuvio_c_shuffle_catalogs),
                            subtitle = stringResource(R.string.nuvio_c_shuffle_catalogs_sub),
                            value = config.catalogKeys?.size?.let { stringResource(R.string.nuvio_c_shuffle_catalogs_count, it) }
                                ?: stringResource(R.string.nuvio_c_shuffle_catalogs_auto),
                            onClick = {
                                viewModel.loadCatalogs()
                                catalogsOpen = true
                            }
                        )
                    }
                }
            }
        }
    }
    if (catalogsOpen) {
        ShuffleCatalogDialog(viewModel = viewModel, onDismiss = { catalogsOpen = false })
    }
}
