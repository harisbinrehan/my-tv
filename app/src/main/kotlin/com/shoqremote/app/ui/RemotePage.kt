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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
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
import com.shoqremote.app.Link
import com.shoqremote.app.RemoteViewModel
import com.shoqremote.app.UiState
import com.shoqremote.app.fallbackLabel
import com.shoqremote.app.nowPlaying
import com.shoqremote.atv.CastMediaInfo
import kotlinx.coroutines.delay
import com.shoqremote.atv.Keys
import kotlin.math.roundToInt

/**
 * The remote, styled after the owner's reference design: what's playing (with its play controls) and the volume at
 * the top, then the big pad and Back / Home / Assistant / Keyboard low, within the thumb's reach. Live TV and TV
 * settings live in the bottom bar and the menu, so they aren't repeated here. Sized from the space available, so it
 * never scrolls; the card's title part only shows when the pad still gets 220 dp (its controls always do).
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
        val volume = if (compact) 54.dp else 60.dp
        val keys = if (compact) 60.dp else 68.dp
        val controls = if (compact) 50.dp else 54.dp
        val header = NOW_HEADER + PROGRESS
        // The card's controls, volume and keys, the gaps below the card and the pad, and at least one above the pad.
        val fixed = controls + volume + keys + gap * 3
        val showHeader = maxHeight - fixed - header >= 220.dp
        val pad = (maxHeight - fixed - if (showHeader) header else 0.dp)
            .coerceAtMost(300.dp)
            .coerceAtMost(maxWidth - 40.dp)
            .coerceAtLeast(150.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Save is for an app that isn't in Apps yet; one that is just opens Apps.
            val app = state.currentApp?.takeIf { state.link == Link.CONNECTED && state.power != false && vm.canSave(it) && it !in state.apps }
            NowCard(
                state,
                onKey,
                openApps,
                app?.let { { saving = it } },
                showHeader,
                Modifier.fillMaxWidth().height(controls + if (showHeader) header else 0.dp),
            )
            Spacer(Modifier.height(gap))
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
                    if (state.listening) "Listening" else "Assistant",
                    Modifier.weight(1f).fillMaxHeight(),
                    selected = state.listening,
                    onClick = onVoice,
                )
                Shortcut(Icons.Rounded.Keyboard, "Keyboard", Modifier.weight(1f).fillMaxHeight(), accent = true, onClick = onType)
            }
        }
    }
}

private val NOW_HEADER = 60.dp
private val PROGRESS = 20.dp

/**
 * What's on the TV and its play controls in one card. The top part shows the app's icon and, when the app reports it
 * over Cast (YouTube does), the video's title, channel and progress; tapping it goes to Apps, Save keeps the running
 * app there. The controls stay even when there's no room for the top part.
 */
@Composable
private fun NowCard(
    state: UiState,
    onKey: (Int, Int) -> Unit,
    openApps: () -> Unit,
    onSave: (() -> Unit)?,
    showHeader: Boolean,
    modifier: Modifier,
) {
    val playing = state.nowPlaying()
    val media = playing?.takeIf { it.title != null }
    Column(modifier.card()) {
        if (showHeader) {
            NowHeader(state, media, openApps, onSave, Modifier.fillMaxWidth().height(NOW_HEADER))
            Progress(media, Modifier.fillMaxWidth().height(PROGRESS).padding(horizontal = 14.dp))
        }
        PlayControls(onKey, playing?.playing, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun NowHeader(state: UiState, media: CastMediaInfo?, openApps: () -> Unit, onSave: (() -> Unit)?, modifier: Modifier) {
    val app = state.currentApp
    val connected = state.link == Link.CONNECTED
    val on = connected && state.power != false
    val appName = when {
        app == null || app == HOME_PACKAGE -> "Home screen"
        else -> state.meta[app]?.label ?: fallbackLabel(app)
    }
    val title = when {
        !connected -> "Not connected"
        !on -> "TV in standby"
        media != null -> media.title.orEmpty()
        else -> appName
    }
    val subtitle = when {
        !connected -> "Looking for your TV"
        !on -> "Tap power to turn it on"
        media != null -> listOfNotNull(appName, media.subtitle).joinToString("  ·  ")
        else -> "On your TV"
    }
    val path = app?.takeIf { on }?.let { state.meta[it]?.iconPath }
    val icon = remember(path) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    Row(
        modifier
            .clickable(onClickLabel = "Open Apps", role = Role.Button, onClick = openApps)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp),
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
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onSave != null) {
            Spacer(Modifier.width(8.dp))
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

/**
 * The video's progress with elapsed and total time, moving on by itself while it plays (the box reports the position
 * every 15 s and on every change). A live stream says Live; with nothing reported it's a plain divider.
 */
@Composable
private fun Progress(media: CastMediaInfo?, modifier: Modifier) {
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(media?.playing, media?.readAtMs) {
        clock = System.currentTimeMillis()
        while (media?.playing == true) {
            delay(1_000)
            clock = System.currentTimeMillis()
        }
    }
    val duration = media?.durationSec?.takeIf { !media.live }
    val start = media?.positionSec
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when {
            media?.live == true -> {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Danger))
                Spacer(Modifier.width(6.dp))
                Text("Live", fontSize = 11.sp, color = Palette.Muted)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(1.dp).background(Palette.Line))
            }
            duration != null && start != null -> {
                val ran = if (media.playing == true) (clock - media.readAtMs).coerceAtLeast(0) / 1000.0 else 0.0
                val position = (start + ran).coerceIn(0.0, duration)
                Text(clockText(position), fontSize = 11.sp, color = Palette.Muted, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Palette.Line)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((position / duration).toFloat()).background(Palette.Indigo))
                }
                Spacer(Modifier.width(8.dp))
                Text(clockText(duration), fontSize = 11.sp, color = Palette.Muted, maxLines = 1)
            }
            else -> Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        }
    }
}

private fun clockText(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
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
 * Mute, − and + (hold to keep going), and a slider whose knob shows the level. The box can't jump to a level, so
 * letting go of the slider steps the volume up or down until the box reports the chosen level.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeCard(state: UiState, vm: RemoteViewModel, modifier: Modifier) {
    // In standby the box reports a fixed volume, so the card waits until the TV is on.
    val volume = state.volume?.takeIf { state.link == Link.CONNECTED && state.power != false }
    val live = volume != null
    val muted = volume?.muted == true
    var dragging by remember { mutableStateOf<Float?>(null) }
    val max = (volume?.max ?: 15).coerceAtLeast(1)
    val shown = dragging ?: volume?.level?.toFloat() ?: 0f
    val view = LocalView.current
    // While muted the level is kept but drawn grey, so it's clear nothing is playing out loud.
    val trackColors = SliderDefaults.colors(
        activeTrackColor = if (muted && dragging == null) Palette.Muted else Palette.Indigo,
        inactiveTrackColor = Palette.Line,
        disabledActiveTrackColor = Palette.Line,
        disabledInactiveTrackColor = Palette.Line,
    )
    val step: (Int, Int) -> Unit = { code, direction -> if (live) vm.key(code, direction) }
    Row(
        modifier.card().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .then(if (muted) Modifier.background(Palette.accent()) else Modifier.background(Palette.Raised))
                .border(1.dp, if (muted) Color.Transparent else Palette.Line, CircleShape)
                .clickable(enabled = live, onClickLabel = if (muted) "Unmute" else "Mute", role = Role.Button) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    vm.key(Keys.VOLUME_MUTE)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                if (muted) "Unmute" else "Mute",
                Modifier.size(20.dp),
                tint = if (live) Palette.Text else Palette.Muted,
            )
        }
        Spacer(Modifier.width(8.dp))
        StepKey(step, Keys.VOLUME_DOWN, "Volume down", Icons.Rounded.Remove, live)
        Slider(
            value = shown.coerceIn(0f, max.toFloat()),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { vm.setVolume(it.roundToInt()) }
                dragging = null
            },
            valueRange = 0f..max.toFloat(),
            steps = (max - 1).coerceAtLeast(0),
            enabled = live,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            thumb = {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (live) Palette.Text else Palette.Raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (live) "${shown.roundToInt()}" else "–",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (live) Palette.Background else Palette.Muted,
                        maxLines = 1,
                    )
                }
            },
            track = { slider ->
                SliderDefaults.Track(
                    sliderState = slider,
                    modifier = Modifier.height(6.dp),
                    enabled = live,
                    colors = trackColors,
                    drawStopIndicator = null,
                    drawTick = { _, _ -> },
                    thumbTrackGapSize = 0.dp,
                )
            },
        )
        StepKey(step, Keys.VOLUME_UP, "Volume up", Icons.Rounded.Add, live)
    }
}

/** A round − or + that steps the volume, and keeps stepping while held. */
@Composable
private fun StepKey(onKey: (Int, Int) -> Unit, code: Int, label: String, icon: ImageVector, enabled: Boolean) =
    KeyButton(onKey, code, label, Modifier.size(42.dp), repeat = true) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (enabled) Palette.Text else Palette.Muted)
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

/**
 * Rewind, play or pause, and fast forward. The middle icon follows what the app on the TV reports (pause while it
 * plays, play while paused); when it reports nothing, it shows both, since the key toggles.
 */
@Composable
private fun PlayControls(onKey: (Int, Int) -> Unit, playing: Boolean?, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        KeyButton(onKey, Keys.MEDIA_REWIND, "Rewind", Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent) {
            Icon(Icons.Rounded.FastRewind, null, Modifier.size(24.dp), tint = Palette.Text)
        }
        Box(Modifier.width(1.dp).height(22.dp).background(Palette.Line))
        val label = when (playing) {
            true -> "Pause"
            false -> "Play"
            null -> "Play or pause"
        }
        KeyButton(onKey, Keys.MEDIA_PLAY_PAUSE, label, Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent) {
            when (playing) {
                true -> Icon(Icons.Rounded.Pause, null, Modifier.size(28.dp), tint = Palette.Text)
                false -> Icon(Icons.Rounded.PlayArrow, null, Modifier.size(30.dp), tint = Palette.Text)
                null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp), tint = Palette.Text)
                    Icon(Icons.Rounded.Pause, null, Modifier.size(20.dp).offset(x = (-3).dp), tint = Palette.Text)
                }
            }
        }
        Box(Modifier.width(1.dp).height(22.dp).background(Palette.Line))
        KeyButton(onKey, Keys.MEDIA_FAST_FORWARD, "Fast forward", Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent) {
            Icon(Icons.Rounded.FastForward, null, Modifier.size(24.dp), tint = Palette.Text)
        }
    }
}
