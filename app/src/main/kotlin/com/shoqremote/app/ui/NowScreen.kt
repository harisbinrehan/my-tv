package com.shoqremote.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoqremote.app.HOME_PACKAGE
import com.shoqremote.app.LIVE_TV_PACKAGE
import com.shoqremote.app.Link
import com.shoqremote.app.RemoteViewModel
import com.shoqremote.app.UiState
import com.shoqremote.app.YOUTUBE_PACKAGE
import com.shoqremote.atv.Direction
import com.shoqremote.atv.Keys
import kotlin.math.abs

/** Everything for the app on the TV right now: what it is, a touch pad to move, typing, voice and volume. */
@Composable
internal fun NowPage(state: UiState, vm: RemoteViewModel, onVoice: () -> Unit, onType: () -> Unit) {
    val onKey: (Int, Int) -> Unit = vm::key
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NowCard(state, onKey)
        TypeBar(state.currentApp == YOUTUBE_PACKAGE, onType)
        TouchPad(onKey, Modifier.fillMaxWidth().height(220.dp))
        VoiceCard(state.listening, onVoice)
        VolumeCard(state, onKey)
        Spacer(Modifier.height(8.dp))
    }
}

/** Looks like a text box, so it's obvious where to type; a tap opens the typing panel with the keyboard up. */
@Composable
private fun TypeBar(youTube: Boolean, onType: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Panel)
            .border(1.dp, Palette.Line, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Type on TV", onClick = onType)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Keyboard, null, Modifier.size(22.dp), tint = Palette.Indigo)
        Spacer(Modifier.width(12.dp))
        Text(if (youTube) "Search YouTube" else "Type on your TV", fontSize = 16.sp, color = Palette.Muted, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = Palette.Muted)
    }
}

@Composable
private fun NowCard(state: UiState, onKey: (Int, Int) -> Unit) {
    val app = state.currentApp
    val title = when {
        state.link != Link.CONNECTED -> "Not connected"
        state.power == false -> "TV is off"
        app == null || app == HOME_PACKAGE -> "Home screen"
        else -> appName(app, state)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app ?: HOME_PACKAGE, state, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("On your TV", fontSize = 12.sp, color = Palette.Muted)
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NowKey(onKey, Keys.BACK, Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            NowKey(onKey, Keys.HOME, Icons.Rounded.Home, "Home")
            NowKey(onKey, Keys.MEDIA_PLAY_PAUSE, Icons.Rounded.PlayArrow, "Play or pause")
            if (app == LIVE_TV_PACKAGE) {
                NowKey(onKey, Keys.CHANNEL_DOWN, Icons.Rounded.KeyboardArrowDown, "Channel down")
                NowKey(onKey, Keys.CHANNEL_UP, Icons.Rounded.KeyboardArrowUp, "Channel up")
            } else {
                NowKey(onKey, Keys.MENU, Icons.Rounded.Menu, "Menu")
            }
        }
    }
}

@Composable
private fun NowKey(onKey: (Int, Int) -> Unit, code: Int, icon: ImageVector, label: String) =
    KeyButton(onKey, code, label, Modifier.size(52.dp), color = Palette.Raised) {
        Icon(icon, null, Modifier.size(24.dp), tint = Palette.Text)
    }

/** Swipes become arrow presses, one per step of travel; a tap is OK. */
@Composable
private fun TouchPad(onKey: (Int, Int) -> Unit, modifier: Modifier) {
    val view = LocalView.current
    val send by rememberUpdatedState(onKey)
    val step = with(LocalDensity.current) { 44.dp.toPx() }
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Palette.Raised, Palette.Panel)))
            .border(1.dp, Palette.Line, RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    send(Keys.DPAD_CENTER, Direction.SHORT)
                })
            }
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(onDragStart = {
                    dx = 0f
                    dy = 0f
                }) { change, drag ->
                    change.consume()
                    dx += drag.x
                    dy += drag.y
                    val key = when {
                        abs(dx) >= step && abs(dx) >= abs(dy) -> if (dx > 0) Keys.DPAD_RIGHT else Keys.DPAD_LEFT
                        abs(dy) >= step && abs(dy) > abs(dx) -> if (dy > 0) Keys.DPAD_DOWN else Keys.DPAD_UP
                        else -> null
                    }
                    if (key != null) {
                        send(key, Direction.SHORT)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        dx = 0f
                        dy = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.TouchApp, null, Modifier.size(34.dp), tint = Palette.Muted)
            Spacer(Modifier.height(6.dp))
            Text("Swipe to move, tap for OK", fontSize = 13.sp, color = Palette.Muted)
        }
    }
}

@Composable
private fun VoiceCard(listening: Boolean, onVoice: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (listening) Palette.accent() else SolidColor(Palette.Panel))
            .clickable(onClickLabel = "Ask Google on the TV", onClick = onVoice)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Palette.accent()), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Mic, null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (listening) "Listening, tap to stop" else "Ask Google on the TV",
                fontWeight = FontWeight.SemiBold,
                color = if (listening) Color.White else Palette.Text,
            )
            Text(
                "Say things like \"Open YouTube\" or \"Cricket highlights\"",
                fontSize = 12.sp,
                color = if (listening) Color.White.copy(alpha = 0.85f) else Palette.Muted,
            )
        }
    }
}

@Composable
private fun VolumeCard(state: UiState, onKey: (Int, Int) -> Unit) {
    val volume = state.volume
    val muted = volume?.muted == true
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Panel).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyButton(onKey, Keys.VOLUME_DOWN, "Volume down", Modifier.size(48.dp), color = Palette.Raised) {
            Icon(Icons.Rounded.Remove, null, tint = Palette.Text)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Volume", fontSize = 12.sp, color = Palette.Muted)
            Text(
                when {
                    volume == null -> "-"
                    muted -> "Muted"
                    else -> "${volume.level} of ${volume.max}"
                },
                fontWeight = FontWeight.SemiBold,
                color = if (muted) Palette.Warn else Palette.Text,
            )
        }
        KeyButton(
            onKey,
            Keys.VOLUME_MUTE,
            if (muted) "Unmute" else "Mute",
            Modifier.size(48.dp),
            color = if (muted) Palette.Warn.copy(alpha = 0.25f) else Palette.Raised,
        ) {
            Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Palette.Text)
        }
        Spacer(Modifier.width(8.dp))
        KeyButton(onKey, Keys.VOLUME_UP, "Volume up", Modifier.size(48.dp), color = Palette.Raised) {
            Icon(Icons.Rounded.Add, null, tint = Palette.Text)
        }
    }
}
