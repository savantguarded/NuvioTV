package com.nuvio.tv.ui.screens.settings

// [fork] Nuvio C round 37 `api_keys`: TheTVDB and OMDb pages under Settings > Integrations, built like official's
// Anime-Skip page (header, one card, a key row showing "Not set" or the last four characters, and the same key
// dialog with Cancel / Clear / Save). Save checks the key first; a key the service refuses isn't kept.
// Keys stay on this TV (NuvioCApiKeys).

import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.calendar.tvdbKeyWorks
import com.nuvio.tv.core.nuvioc.NuvioCApiKeys
import com.nuvio.tv.core.nuvioc.NuvioCOmdb
import com.nuvio.tv.data.remote.api.TvdbApi
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NuvioCKeyService { TVDB, OMDB }

@HiltViewModel
class NuvioCApiKeysViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tvdbApi: TvdbApi
) : ViewModel() {
    init { NuvioCApiKeys.ensureLoaded(context) }

    val keys = NuvioCApiKeys.keys

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Checks then saves; [onResult] gets null on success, else the message to show. Blank = clear. */
    fun save(service: NuvioCKeyService, key: String, onResult: (Int?) -> Unit) {
        val value = key.trim()
        if (value.isEmpty()) {
            store(service, "")
            onResult(null)
            return
        }
        viewModelScope.launch {
            _saving.value = true
            val works = when (service) {
                NuvioCKeyService.TVDB -> tvdbKeyWorks(tvdbApi, value)
                NuvioCKeyService.OMDB -> NuvioCOmdb.keyWorks(value)
            }
            _saving.value = false
            when (works) {
                true -> { store(service, value); onResult(null) }
                false -> onResult(R.string.nuvio_c_key_refused)
                null -> onResult(R.string.nuvio_c_key_unreachable)
            }
        }
    }

    private fun store(service: NuvioCKeyService, value: String) = when (service) {
        NuvioCKeyService.TVDB -> NuvioCApiKeys.setTvdb(context, value)
        NuvioCKeyService.OMDB -> NuvioCApiKeys.setOmdb(context, value)
    }
}

@Composable
internal fun NuvioCApiKeyContent(
    service: NuvioCKeyService,
    initialFocusRequester: FocusRequester? = null,
    viewModel: NuvioCApiKeysViewModel = hiltViewModel()
) {
    val keys by viewModel.keys.collectAsState()
    val current = when (service) {
        NuvioCKeyService.TVDB -> keys?.tvdb
        NuvioCKeyService.OMDB -> keys?.omdb
    }.orEmpty()
    var showDialog by remember { mutableStateOf(false) }
    val tvdb = service == NuvioCKeyService.TVDB

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsDetailHeader(
            title = stringResource(if (tvdb) R.string.nuvio_c_tvdb_title else R.string.nuvio_c_omdb_title),
            subtitle = stringResource(if (tvdb) R.string.nuvio_c_tvdb_page_subtitle else R.string.nuvio_c_omdb_page_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = NuvioTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "nuvio_c_key_${service.name}") {
                        SettingsActionRow(
                            title = stringResource(R.string.nuvio_c_key_row_title),
                            subtitle = stringResource(if (tvdb) R.string.nuvio_c_tvdb_key_subtitle else R.string.nuvio_c_omdb_key_subtitle),
                            value = NuvioCApiKeys.masked(current, stringResource(R.string.mdblist_not_set)),
                            onClick = { showDialog = true },
                            modifier = Modifier
                                .padding(top = NuvioTheme.spacing.xxs)
                                .then(if (initialFocusRequester != null) Modifier.focusRequester(initialFocusRequester) else Modifier)
                        )
                    }
                }
                SettingsVerticalScrollIndicators(state = listState)
            }
        }
    }

    if (showDialog) {
        NuvioCApiKeyDialog(
            service = service,
            currentValue = current,
            viewModel = viewModel,
            onDone = { showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun NuvioCApiKeyDialog(
    service: NuvioCKeyService,
    currentValue: String,
    viewModel: NuvioCApiKeysViewModel,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(currentValue) { mutableStateOf(currentValue) }
    var isInputFocused by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val saving by viewModel.saving.collectAsState()
    val context = LocalContext.current
    val tvdb = service == NuvioCKeyService.TVDB
    val onResult: (Int?) -> Unit = { error ->
        if (error == null) onDone() else Toast.makeText(context, context.getString(error), Toast.LENGTH_SHORT).show()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(if (tvdb) R.string.nuvio_c_tvdb_dialog_title else R.string.nuvio_c_omdb_dialog_title),
        subtitle = stringResource(if (tvdb) R.string.nuvio_c_tvdb_dialog_subtitle else R.string.nuvio_c_omdb_dialog_subtitle),
        width = 700.dp
    ) {
        Card(
            onClick = { inputFocusRequester.requestFocus() },
            modifier = Modifier.fillMaxWidth().onFocusChanged { isInputFocused = it.isFocused || it.hasFocus },
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.BackgroundElevated
            ),
            border = CardDefaults.border(
                border = Border(
                    border = androidx.compose.foundation.BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ),
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                )
            ),
            shape = CardDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1f)
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = NuvioTheme.spacing.md)) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocusRequester)
                        .onKeyEvent { event ->
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER &&
                                event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = NuvioTheme.colors.TextPrimary,
                        textDirection = TextDirection.Content
                    ),
                    cursorBrush = SolidColor(
                        if (isInputFocused) NuvioTheme.colors.Primary
                        else androidx.compose.ui.graphics.Color.Transparent
                    ),
                    decorationBox = { innerTextField ->
                        if (value.isBlank()) {
                            Text(
                                text = stringResource(R.string.nuvio_c_key_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NuvioTheme.colors.TextTertiary
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundElevated,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) { Text(stringResource(R.string.action_cancel)) }
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
            Button(
                onClick = { viewModel.save(service, "", onResult) },
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundElevated,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) { Text(stringResource(R.string.action_clear)) }
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
            Button(
                onClick = { if (!saving) viewModel.save(service, value, onResult) },
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(if (saving) stringResource(R.string.action_saving) else stringResource(R.string.action_save))
            }
        }
    }
}
