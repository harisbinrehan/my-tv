package com.shoqremote.app.ui

import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoqremote.app.HOME_PACKAGE
import com.shoqremote.app.LIVE_TV_PACKAGE
import com.shoqremote.app.Link
import com.shoqremote.app.RemoteViewModel
import com.shoqremote.app.SETTINGS_PACKAGE
import com.shoqremote.app.UiState
import com.shoqremote.app.fallbackLabel
import com.shoqremote.atv.Keys
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(22.dp)
private val CardBrush = Brush.verticalGradient(listOf(Color(0xFF18181C), Color(0xFF121215)))

/**
 * The remote, styled after the owner's reference design: what's on and the volume at the top, then the big pad,
 * Back / Home / Voice, the media bar and shortcut tiles low, within the thumb's reach. Sized from the space
 * available, so it never scrolls; the What's on card only shows when the pad still gets 220 dp.
 */
@Composable
internal fun RemotePage(state: UiState, vm: RemoteViewModel, onVoice: () -> Unit, onType: () -> Unit, openApps: () -> Unit) {
    val onKey: (Int, Int) -> Unit = vm::key
    var saving by remember { mutableStateOf<String?>(null) }
    saving?.let { pkg ->
        SaveAppDialog(
            name = state.meta[pkg]?.label ?: fallbackLabel(pkg),
            favourite = pkg in state.favourites,
            onDismiss = { saving = null },
        ) { name, favourite ->
            vm.saveApp(pkg, name, favourite)
            saving = null
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
        val compact = maxHeight < 600.dp
        val gap = if (compact) 10.dp else 12.dp
        val volume = if (compact) 52.dp else 56.dp
        val keys = if (compact) 58.dp else 64.dp
        val media = if (compact) 48.dp else 52.dp
        val tiles = if (compact) 62.dp else 68.dp
        val now = 64.dp
        // Volume, keys, media and tiles, the three gaps below the pad, and at least one gap above it.
        val fixed = volume + keys + media + tiles + gap * 4
        val showNow = maxHeight - fixed - now - gap >= 220.dp
        val pad = (maxHeight - fixed - if (showNow) now + gap else 0.dp)
            .coerceAtMost(290.dp)
            .coerceAtMost(maxWidth - 40.dp)
            .coerceAtLeast(150.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showNow) {
                val app = state.currentApp?.takeIf { state.link == Link.CONNECTED && state.power != false && vm.canSave(it) }
                NowCard(state, openApps, app?.let { { saving = it } }, Modifier.fillMaxWidth().height(now))
                Spacer(Modifier.height(gap))
            }
            VolumeCard(state, vm, Modifier.fillMaxWidth().height(volume))
            // Spare height goes here, so the pad and everything below it sit low on tall phones.
            Spacer(Modifier.weight(1f))
            DPad(onKey, pad)
            Spacer(Modifier.height(gap))
            Row(Modifier.fillMaxWidth().height(keys), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledKey(onKey, Keys.BACK, Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.weight(1f).fillMaxHeight())
                LabeledKey(onKey, Keys.HOME, Icons.Rounded.Home, "Home", Modifier.weight(1f).fillMaxHeight())
                Shortcut(
                    Icons.Rounded.Mic,
                    if (state.listening) "Listening" else "Voice",
                    Modifier.weight(1f).fillMaxHeight(),
                    selected = state.listening,
                    onClick = onVoice,
                )
            }
            Spacer(Modifier.height(gap))
            MediaBar(onKey, Modifier.fillMaxWidth().height(media))
            Spacer(Modifier.height(gap))
            Row(Modifier.fillMaxWidth().height(tiles), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Shortcut(Icons.Rounded.Keyboard, "Keyboard", Modifier.weight(1f).fillMaxHeight(), accent = true, onClick = onType)
                Shortcut(Icons.Rounded.LiveTv, "Live TV", Modifier.weight(1f).fillMaxHeight(), busy = state.opening == LIVE_TV_PACKAGE, onClick = vm::openLiveTv)
                Shortcut(Icons.Rounded.Settings, "Settings", Modifier.weight(1f).fillMaxHeight(), busy = state.opening == SETTINGS_PACKAGE, onClick = vm::openTvSettings)
            }
        }
    }
}

/** What the TV is showing, with the app's icon; tapping it goes to Apps, Save keeps the running app there. */
@Composable
private fun NowCard(state: UiState, openApps: () -> Unit, onSave: (() -> Unit)?, modifier: Modifier) {
    val app = state.currentApp
    val connected = state.link == Link.CONNECTED
    val on = connected && state.power != false
    val title = when {
        !connected -> "Not connected"
        !on -> "TV in standby"
        app == null || app == HOME_PACKAGE -> "Home screen"
        else -> state.meta[app]?.label ?: fallbackLabel(app)
    }
    val subtitle = when {
        !connected -> "Looking for your TV"
        !on -> "Tap power to turn it on"
        else -> "On your TV"
    }
    val path = app?.takeIf { on }?.let { state.meta[it]?.iconPath }
    val icon = remember(path) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    Row(
        modifier
            .clip(CardShape)
            .background(CardBrush)
            .border(1.dp, Palette.Line, CardShape)
            .clickable(onClickLabel = "Open Apps", role = Role.Button, onClick = openApps)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 42.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Raised),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(icon, null, Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Icon(
                    when {
                        !on -> Icons.Rounded.PowerSettingsNew
                        app == null || app == HOME_PACKAGE -> Icons.Rounded.Home
                        else -> Icons.Rounded.LiveTv
                    },
                    null,
                    Modifier.size(22.dp),
                    tint = Palette.Muted,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = Palette.Muted, maxLines = 1)
        }
        if (onSave != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Raised)
                    .border(1.dp, Palette.Line, RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = "Save this app in Apps", role = Role.Button, onClick = onSave)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(16.dp), tint = Palette.Indigo)
                Spacer(Modifier.width(4.dp))
                Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
            }
        } else {
            Icon(Icons.Rounded.ChevronRight, null, tint = Palette.Muted)
        }
    }
}

/** Keeps the running app in Apps under a name you choose, for apps whose own name the box doesn't report. */
@Composable
private fun SaveAppDialog(name: String, favourite: Boolean, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf(name) }
    var star by remember { mutableStateOf(favourite) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Apps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true, label = { Text("Name") })
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { star = !star }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = star, onCheckedChange = { star = it })
                    Text("Add to favourites", color = Palette.Text)
                }
                Text("Use the name you see on the TV, for example NetMirror.", fontSize = 12.sp, color = Palette.Muted)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, star) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Mute, a slider and the level. The box can't jump to a level, so letting go of the slider steps the volume up or
 * down until the box reports the chosen level; while dragging, the slider shows where it will go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeCard(state: UiState, vm: RemoteViewModel, modifier: Modifier) {
    // In standby the box reports a fixed volume, so the card waits until the TV is on.
    val volume = state.volume?.takeIf { state.link == Link.CONNECTED && state.power != false }
    val muted = volume?.muted == true
    var dragging by remember { mutableStateOf<Float?>(null) }
    val max = (volume?.max ?: 15).coerceAtLeast(1)
    val shown = dragging ?: (if (muted) 0f else volume?.level?.toFloat() ?: 0f)
    val view = LocalView.current
    val trackColors = SliderDefaults.colors(
        activeTrackColor = Palette.Indigo,
        inactiveTrackColor = Palette.Line,
        disabledActiveTrackColor = Palette.Line,
        disabledInactiveTrackColor = Palette.Line,
    )
    Row(
        modifier.clip(CardShape).background(CardBrush).border(1.dp, Palette.Line, CardShape).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(if (muted) Modifier.background(Palette.accent()) else Modifier.background(Palette.Raised))
                .clickable(onClickLabel = if (muted) "Unmute" else "Mute", role = Role.Button) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    vm.key(Keys.VOLUME_MUTE)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                if (muted) "Unmute" else "Mute",
                Modifier.size(20.dp),
                tint = Palette.Text,
            )
        }
        Spacer(Modifier.width(8.dp))
        Slider(
            value = shown.coerceIn(0f, max.toFloat()),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { vm.setVolume(it.roundToInt()) }
                dragging = null
            },
            valueRange = 0f..max.toFloat(),
            steps = (max - 1).coerceAtLeast(0),
            enabled = volume != null,
            modifier = Modifier.weight(1f),
            thumb = {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (volume != null) Palette.Text else Palette.Muted),
                )
            },
            track = { slider ->
                SliderDefaults.Track(
                    sliderState = slider,
                    modifier = Modifier.height(6.dp),
                    enabled = volume != null,
                    colors = trackColors,
                    drawStopIndicator = null,
                    drawTick = { _, _ -> },
                    thumbTrackGapSize = 0.dp,
                )
            },
            colors = SliderDefaults.colors(
                thumbColor = Palette.Text,
                activeTrackColor = Palette.Indigo,
                inactiveTrackColor = Palette.Line,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = Palette.Muted,
                disabledActiveTrackColor = Palette.Line,
                disabledInactiveTrackColor = Palette.Line,
            ),
        )
        Text(
            when {
                volume == null -> "–"
                muted && dragging == null -> "Muted"
                else -> "${shown.roundToInt()}"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.Muted,
            modifier = Modifier.width(48.dp).padding(start = 6.dp),
        )
    }
}

/** Left and right are physical directions, so the pad stays left-to-right in every language. */
@Composable
private fun DPad(onKey: (Int, Int) -> Unit, size: Dp) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF222227), Color(0xFF151518))))
                .border(1.dp, Palette.Line, CircleShape),
        ) {
            val zone = Modifier.size(size * 0.33f)
            KeyButton(onKey, Keys.DPAD_UP, "Up", repeat = true, modifier = zone.align(Alignment.TopCenter), color = Color.Transparent) {
                Arrow(Icons.Rounded.KeyboardArrowUp)
            }
            KeyButton(onKey, Keys.DPAD_DOWN, "Down", repeat = true, modifier = zone.align(Alignment.BottomCenter), color = Color.Transparent) {
                Arrow(Icons.Rounded.KeyboardArrowDown)
            }
            KeyButton(onKey, Keys.DPAD_LEFT, "Left", repeat = true, modifier = zone.align(Alignment.CenterStart), color = Color.Transparent) {
                Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft)
            }
            KeyButton(onKey, Keys.DPAD_RIGHT, "Right", repeat = true, modifier = zone.align(Alignment.CenterEnd), color = Color.Transparent) {
                Arrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight)
            }
            KeyButton(
                onKey,
                Keys.DPAD_CENTER,
                "OK",
                Modifier
                    .size(size * 0.42f)
                    .align(Alignment.Center)
                    .border(1.dp, Color(0xFF34343C), CircleShape),
                brush = Brush.verticalGradient(listOf(Color(0xFF2C2C33), Color(0xFF1B1B20))),
            ) {
                Text("OK", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun Arrow(icon: ImageVector) = Icon(icon, null, Modifier.size(36.dp), tint = Palette.Text)

/** A key with its name under the icon, as on the reference design's Back / Home row. */
@Composable
private fun LabeledKey(onKey: (Int, Int) -> Unit, code: Int, icon: ImageVector, label: String, modifier: Modifier) =
    KeyButton(onKey, code, label, modifier.border(1.dp, Palette.Line, RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), brush = CardBrush) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(22.dp), tint = Palette.Text)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = Palette.Muted)
        }
    }

/** An action tile (not a TV key): icon and name; filled with the accent while on, a spinner while it opens. */
@Composable
private fun Shortcut(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    selected: Boolean = false,
    accent: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .then(if (selected) Modifier.background(Palette.accent()) else Modifier.background(CardBrush))
            .border(1.dp, if (accent && !selected) Palette.Indigo.copy(alpha = 0.45f) else Palette.Line, shape)
            .clickable(onClickLabel = label, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Palette.Text)
        } else {
            Icon(icon, null, Modifier.size(22.dp), tint = if (accent && !selected) Palette.Indigo else Palette.Text)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = if (selected) Palette.Text else Palette.Muted, maxLines = 1)
    }
}

/** Rewind, play or pause, and fast forward as one bar. */
@Composable
private fun MediaBar(onKey: (Int, Int) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Row(modifier.clip(shape).background(CardBrush).border(1.dp, Palette.Line, shape), verticalAlignment = Alignment.CenterVertically) {
        KeyButton(onKey, Keys.MEDIA_REWIND, "Rewind", Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent) {
            Icon(Icons.Rounded.FastRewind, null, Modifier.size(24.dp), tint = Palette.Text)
        }
        Box(Modifier.width(1.dp).height(22.dp).background(Palette.Line))
        KeyButton(onKey, Keys.MEDIA_PLAY_PAUSE, "Play or pause", Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(28.dp), tint = Palette.Text)
        }
        Box(Modifier.width(1.dp).height(22.dp).background(Palette.Line))
        KeyButton(onKey, Keys.MEDIA_FAST_FORWARD, "Fast forward", Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent) {
            Icon(Icons.Rounded.FastForward, null, Modifier.size(24.dp), tint = Palette.Text)
        }
    }
}
