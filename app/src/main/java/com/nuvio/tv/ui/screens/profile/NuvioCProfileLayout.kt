package com.nuvio.tv.ui.screens.profile

// [fork] Nuvio C profile screen (choosing a profile; "Manage profiles" stays official), after the
// Netflix-style layout Charles picked (canvas "Nuvio C profile screen, round 2", design 6 Rail):
//  - Nuvio wordmark with the supporter badge top-left, nothing else written on the screen.
//  - Circular profiles stacked down the left over a dark left-side fade, so the profile
//    background (e.g. a rotating poster with its logo bottom-right) fills the rest.
//  - The focused profile grows, gets a white ring and its name beside it; a pencil to its left
//    (press Left, then OK) opens the profile menu, same as press-and-hold / Menu.
//  - Lock badge on PIN profiles, star on the primary one; "Add profile" is the last circle.
//  - Focus starts on the active profile; moving between profiles changes the background as before.
// Switch: NuvioCFeatures.PROFILE_MINIMAL (off = official screen).

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.UserProfile
import com.nuvio.tv.ui.components.MemberBrandWordmark
import com.nuvio.tv.ui.components.ProfileAvatarCircle
import com.nuvio.tv.ui.util.rememberLongPressKeyTracker

internal fun nuvioCProfileRail(isManagementMode: Boolean): Boolean =
    NuvioCFeatures.PROFILE_MINIMAL && !isManagementMode

private val RailEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val EditColumn = 52.dp
private val AvatarColumn = 124.dp
private val AvatarSmall = 58.dp
private val AvatarLarge = 104.dp
private val RingGap = 5.dp
private val RowGap = 10.dp

@Composable
internal fun NuvioCProfileRail(
    profiles: List<UserProfile>,
    activeProfileId: Int,
    canAddProfile: Boolean,
    profilePinEnabled: Map<Int, Boolean>,
    avatarImageUrlsById: Map<String, String>,
    brandWordmarkRes: Int?,
    onProfileFocused: (UserProfile?) -> Unit,
    onProfileSelected: (UserProfile) -> Unit,
    onProfileLongPress: (UserProfile) -> Unit,
    onAddProfileClick: () -> Unit
) {
    val total = profiles.size + if (canAddProfile) 1 else 0
    val requesters = remember(total) { List(total) { FocusRequester() } }
    val initial = profiles.indexOfFirst { it.id == activeProfileId }.takeIf { it >= 0 } ?: 0
    LaunchedEffect(total, initial) {
        repeat(2) { withFrameNanos { } }
        requesters.getOrNull(initial)?.let { runCatching { it.requestFocus() } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.88f),
                        0.2f to Color.Black.copy(alpha = 0.7f),
                        0.38f to Color.Black.copy(alpha = 0.28f),
                        0.55f to Color.Transparent
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 28.dp, top = 30.dp, bottom = 30.dp)
        ) {
            MemberBrandWordmark(
                height = 34.dp,
                contentDescription = stringResource(R.string.cd_nuvio_logo),
                drawableOverride = brandWordmarkRes,
                modifier = Modifier.padding(start = EditColumn + 8.dp)
            )
            Spacer(modifier = Modifier.height(22.dp))
            profiles.forEachIndexed { index, profile ->
                RailProfileRow(
                    profile = profile,
                    avatarImageUrl = profile.avatarUrl?.takeIf { it.isNotBlank() }
                        ?: profile.avatarId?.let(avatarImageUrlsById::get),
                    pinEnabled = profilePinEnabled[profile.id] == true,
                    focusRequester = requesters[index],
                    onFocused = { onProfileFocused(profile) },
                    onClick = { onProfileSelected(profile) },
                    onEdit = { onProfileLongPress(profile) }
                )
                Spacer(modifier = Modifier.height(RowGap))
            }
            if (canAddProfile) {
                RailAddRow(
                    focusRequester = requesters[profiles.size],
                    onFocused = { onProfileFocused(null) },
                    onClick = onAddProfileClick
                )
            }
        }
    }
}

@Composable
private fun RailProfileRow(
    profile: UserProfile,
    avatarImageUrl: String?,
    pinEnabled: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    var avatarFocused by remember { mutableStateOf(false) }
    var editFocused by remember { mutableStateOf(false) }
    val active = avatarFocused || editFocused
    val grow by animateFloatAsState(if (active) 1f else 0f, tween(260, easing = RailEasing), label = "railGrow")
    val ring by animateFloatAsState(if (avatarFocused) 1f else 0f, tween(200, easing = RailEasing), label = "railRing")
    val avatarSize = lerp(AvatarSmall, AvatarLarge, grow)
    var longPressTriggered by remember { mutableStateOf(false) }
    val longPressKeyTracker = rememberLongPressKeyTracker()
    val avatarInteraction = remember { MutableInteractionSource() }
    val editInteraction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier.height(avatarSize + RingGap * 2 + 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pencil: only shown for the focused profile; Left from the avatar reaches it.
        Box(modifier = Modifier.width(EditColumn), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (active) 1f else 0f)
                    .onFocusChanged {
                        editFocused = it.isFocused
                        if (it.isFocused) onFocused()
                    }
                    .clickable(interactionSource = editInteraction, indication = null, enabled = active, onClick = onEdit)
                    .semantics { role = Role.Button }
                    .background(if (editFocused) Color.White else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.profile_edit_header),
                    tint = if (editFocused) Color.Black else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box(modifier = Modifier.width(AvatarColumn), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(avatarSize + RingGap * 2)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        avatarFocused = it.isFocused
                        if (it.isFocused) onFocused()
                    }
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (native.action == AndroidKeyEvent.ACTION_DOWN && native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                            longPressTriggered = true
                            onEdit()
                            return@onPreviewKeyEvent true
                        }
                        if (longPressKeyTracker.handle(native, ::isRailSelectKey) {
                                longPressTriggered = true
                                onEdit()
                            }
                        ) {
                            if (native.action == AndroidKeyEvent.ACTION_UP) longPressTriggered = false
                            return@onPreviewKeyEvent true
                        }
                        if (native.action == AndroidKeyEvent.ACTION_UP && longPressTriggered &&
                            (isRailSelectKey(native.keyCode) || native.keyCode == AndroidKeyEvent.KEYCODE_MENU)
                        ) {
                            longPressTriggered = false
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
                    .clickable(interactionSource = avatarInteraction, indication = null, onClick = onClick)
                    .semantics {
                        role = Role.Button
                        contentDescription = profile.name
                    }
                    .border(3.dp, Color.White.copy(alpha = ring), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ProfileAvatarCircle(
                    name = profile.name,
                    colorHex = profile.avatarColorHex,
                    size = avatarSize,
                    avatarImageUrl = avatarImageUrl,
                    modifier = Modifier.alpha(0.78f + 0.22f * grow)
                )
                if (pinEnabled) {
                    RailBadge(
                        size = lerp(18.dp, 24.dp, grow),
                        modifier = Modifier.align(Alignment.BottomStart).offset(x = 2.dp, y = (-2).dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(lerp(10.dp, 13.dp, grow)))
                    }
                }
                if (profile.isPrimary) {
                    RailBadge(
                        size = lerp(18.dp, 24.dp, grow),
                        color = Color(0xFFFFB300),
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-2).dp)
                    ) {
                        Text("★", color = Color.White, fontSize = if (active) 13.sp else 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            text = profile.name,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 18.dp)
                .widthIn(max = 320.dp)
                .alpha(grow)
        )
    }
}

@Composable
private fun RailBadge(
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.Black.copy(alpha = 0.72f),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
            .border(1.5.dp, Color.Black.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun RailAddRow(
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1f else 0f, tween(220, easing = RailEasing), label = "railAdd")
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.height(AvatarSmall + RingGap * 2 + 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(EditColumn))
        Box(modifier = Modifier.width(AvatarColumn), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(AvatarSmall)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onFocused()
                    }
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .semantics { role = Role.Button }
                    .background(Color.White.copy(alpha = 0.08f + 0.84f * grow), CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.45f * (1f - grow)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.profile_add),
                    tint = if (focused) Color.Black else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.profile_add),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 18.dp).alpha(grow)
        )
    }
}

private fun isRailSelectKey(keyCode: Int): Boolean =
    keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
