package com.shoqremote.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoqremote.app.Background
import com.shoqremote.app.HOME_PACKAGE
import com.shoqremote.app.Link
import com.shoqremote.app.POWER_TARGET
import com.shoqremote.app.PairingStep
import com.shoqremote.app.RemoteTileService
import com.shoqremote.app.RemoteViewModel
import com.shoqremote.app.SETTINGS_PACKAGE
import com.shoqremote.app.TvLinkService
import com.shoqremote.app.UiState
import com.shoqremote.atv.Keys
import com.shoqremote.atv.ShoqBox

/** The remote is the home page, opened from the header; Apps and TV are the bottom bar's sections. */
private enum class Section { REMOTE, APPS, TV }

@Composable
fun RemoteApp(vm: RemoteViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var section by rememberSaveable { mutableStateOf(Section.REMOTE) }
    val paired = state.link != Link.NOT_PAIRED
    LaunchedEffect(paired, state.notificationControls) {
        if (paired && state.notificationControls) TvLinkService.start(context)
    }
    val connected = state.link == Link.CONNECTED
    LaunchedEffect(connected, state.notificationControls) {
        if (connected && state.notificationControls) Background.askOnce(context)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleVoiceSearch() else vm.notify("Voice search needs permission to use the microphone.")
    }
    val onVoice: () -> Unit = {
        val allowed = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (state.listening || allowed) vm.toggleVoiceSearch() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    var typing by rememberSaveable { mutableStateOf(false) }
    val onType: () -> Unit = { typing = true }
    LaunchedEffect(vm) { vm.imeRequests.collect { typing = true } }
    BackHandler(enabled = paired && !typing && section != Section.REMOTE) { section = Section.REMOTE }
    MyTvTheme {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Scaffold(
                containerColor = Palette.Background,
                topBar = {
                    Column {
                        TopArea(state, vm, onRemote = section == Section.REMOTE) { section = Section.REMOTE }
                        UpdateBanner(vm)
                    }
                },
                bottomBar = { if (paired) BottomBar(section, onType) { section = it } },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when {
                        !paired -> PairingPanel(state, vm)
                        section == Section.REMOTE -> RemotePage(state, vm, onVoice, onType)
                        section == Section.APPS -> AppsPage(state, vm)
                        section == Section.TV -> TvPage(state, vm)
                    }
                    if (state.listening) {
                        ListeningBanner(Modifier.align(Alignment.BottomCenter), onStop = vm::toggleVoiceSearch)
                    }
                }
            }
            if (typing && paired) TypePanel(state, vm) { typing = false }
        }
    }
}

@Composable
private fun TopArea(state: UiState, vm: RemoteViewModel, onRemote: Boolean, openRemote: () -> Unit) {
    val paired = state.link != Link.NOT_PAIRED
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Background)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("My Tv", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            StatusLine(state)
        }
        if (paired) {
            // Only where it leads somewhere: on the remote itself it would do nothing.
            AnimatedVisibility(!onRemote, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                Row {
                    RemoteButton(openRemote)
                    Spacer(Modifier.width(10.dp))
                }
            }
            PowerButton(state, vm::power)
        }
        OverflowMenu(state, vm)
    }
}

/** Green to turn on, red to turn off, a spinner while the box wakes; it reads the box's own power report. */
@Composable
private fun PowerButton(state: UiState, onClick: () -> Unit) {
    val view = LocalView.current
    val on = state.link == Link.CONNECTED && state.power == true
    val label = if (on) "Turn TV off" else "Turn TV on"
    val tint = if (on) Palette.Danger else Palette.Good
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .clickable(onClickLabel = label, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (state.opening == POWER_TARGET) {
            CircularProgressIndicator(Modifier.size(20.dp), color = tint, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.PowerSettingsNew, label, Modifier.size(22.dp), tint = tint)
        }
    }
}

/** Back to the remote from Apps or TV; hidden on the remote page itself. */
@Composable
private fun RemoteButton(onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Palette.Raised)
            .clickable(onClickLabel = "Remote", role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.SettingsRemote, "Remote", Modifier.size(22.dp), tint = Palette.Text)
    }
}

@Composable
private fun StatusLine(state: UiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(state)))
        Spacer(Modifier.width(6.dp))
        Text(statusText(state), fontSize = 12.sp, color = Palette.Muted, maxLines = 1)
    }
}

private fun statusText(state: UiState): String = when (state.link) {
    Link.NOT_PAIRED -> "Not paired yet"
    Link.CONNECTING -> "Connecting to ${ShoqBox.NAME}..."
    Link.CONNECTED -> when {
        state.power == false -> "Connected, TV in standby"
        state.currentApp != null && state.currentApp != HOME_PACKAGE -> "On the TV: ${appName(state.currentApp, state)}"
        else -> "Connected to ${ShoqBox.NAME}"
    }
    Link.OFFLINE -> state.detail.ifBlank { "Can't reach the box" }
}

private fun statusColor(state: UiState): Color = when (state.link) {
    Link.CONNECTED -> if (state.power == false) Palette.Warn else Palette.Good
    Link.CONNECTING -> Palette.Warn
    Link.OFFLINE -> Palette.Danger
    Link.NOT_PAIRED -> Palette.Muted
}

/** Shown when a newer My Tv is published; Update downloads it and hands it to Android's installer. */
@Composable
private fun UpdateBanner(vm: RemoteViewModel) {
    val update by vm.update.collectAsStateWithLifecycle()
    val release = update.available ?: return
    val busy = update.progress != null || update.installing
    if (update.dismissed && !busy) return
    val context = LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Raised)
            .border(1.dp, Palette.Line, shape)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = Palette.Indigo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("My Tv ${release.versionName} is ready", color = Palette.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                val detail = when {
                    update.installing -> "Installing..."
                    update.progress != null -> "Downloading ${update.progress}%"
                    release.notes.isNotBlank() -> release.notes
                    else -> "Your pairing and settings stay."
                }
                Text(detail, color = Palette.Muted, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!busy) {
                TextButton(onClick = vm::dismissUpdate) { Text("Later", color = Palette.Muted) }
                Button(onClick = { vm.installUpdate(context) }) { Text("Update") }
            }
        }
        update.progress?.let { percent ->
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, end = 8.dp),
                color = Palette.Indigo,
                trackColor = Palette.Line,
            )
        }
    }
}

@Composable
private fun OverflowMenu(state: UiState, vm: RemoteViewModel) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Palette.Muted) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Notification controls") },
                leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) },
                trailingIcon = { if (state.notificationControls) Icon(Icons.Rounded.Check, "On") },
                onClick = {
                    open = false
                    vm.setNotificationControls(!state.notificationControls)
                },
            )
            val unrestricted = Background.isUnrestricted(context)
            DropdownMenuItem(
                text = { Text("Always stay connected") },
                leadingIcon = { Icon(Icons.Rounded.BatteryFull, null) },
                trailingIcon = { if (unrestricted) Icon(Icons.Rounded.Check, "On") },
                onClick = {
                    open = false
                    if (unrestricted) {
                        vm.notify("Already on: Android lets My Tv stay connected in the background.")
                    } else {
                        Background.requestUnrestricted(context)
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Add tile to quick settings") },
                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = {
                    open = false
                    activity?.let { RemoteTileService.requestAdd(it, vm::notify) }
                },
            )
            val update by vm.update.collectAsStateWithLifecycle()
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Check for updates")
                        Text("Version ${update.installedName}", fontSize = 12.sp, color = Palette.Muted)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.SystemUpdate, null) },
                onClick = {
                    open = false
                    vm.checkForUpdate(manual = true)
                },
            )
            if (state.link != Link.NOT_PAIRED) {
                DropdownMenuItem(
                    text = { Text("Pair again") },
                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                    onClick = {
                        open = false
                        vm.forgetPairing()
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(section: Section, onType: () -> Unit, onSelect: (Section) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        NavigationBar(containerColor = Palette.Panel, tonalElevation = 0.dp) {
            val colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Palette.Text,
                selectedTextColor = Palette.Text,
                indicatorColor = Palette.Indigo.copy(alpha = 0.30f),
                unselectedIconColor = Palette.Muted,
                unselectedTextColor = Palette.Muted,
            )
            NavItem(section, Section.APPS, Icons.Rounded.Apps, "Apps", colors, onSelect)
            TypeNavItem(onType)
            NavItem(section, Section.TV, Icons.Rounded.LiveTv, "TV", colors, onSelect)
        }
    }
}

/** An action, not a tab: an outlined accent pill, so only the filled pill marks the tab you're on. */
@Composable
private fun RowScope.TypeNavItem(onType: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .height(80.dp)
            .clickable(onClickLabel = "Type on TV", role = Role.Button, onClick = onType),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp, 32.dp).clip(CircleShape).border(1.5.dp, Palette.Indigo, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Keyboard, null, Modifier.size(24.dp), tint = Palette.Indigo)
        }
        Spacer(Modifier.height(4.dp))
        Text("Type", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Palette.Indigo)
    }
}

@Composable
private fun RowScope.NavItem(
    current: Section,
    target: Section,
    icon: ImageVector,
    label: String,
    colors: NavigationBarItemColors,
    onSelect: (Section) -> Unit,
) = NavigationBarItem(
    selected = current == target,
    onClick = { onSelect(target) },
    icon = { Icon(icon, null) },
    label = { Text(label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium) },
    colors = colors,
)

@Composable
private fun ListeningBanner(modifier: Modifier, onStop: () -> Unit) {
    Row(
        modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.accent())
            .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Mic, null, tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Listening", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Speak to your TV, for example \"Open YouTube\"", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
        TextButton(onClick = onStop) { Text("Stop", color = Color.White) }
    }
}

/** Sized from the space available so the whole remote fits on screen without scrolling. */
@Composable
private fun RemotePage(state: UiState, vm: RemoteViewModel, onVoice: () -> Unit, onType: () -> Unit) {
    val onKey: (Int, Int) -> Unit = vm::key
    val muted = state.volume?.muted == true
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 6.dp)) {
        val compact = maxHeight < 600.dp
        val rockerKey = if (compact) 54.dp else 66.dp
        val navKey = if (compact) 54.dp else 64.dp
        val quickRow = if (compact) 72.dp else 82.dp
        val dpad = (maxHeight - quickRow - navKey - (rockerKey * 2 + 22.dp) - 36.dp)
            .coerceIn(160.dp, 300.dp)
            .coerceAtMost(maxWidth - 16.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionButton(Icons.Rounded.Keyboard, "Type", accent = true, onClick = onType)
                ActionButton(Icons.Rounded.Mic, if (state.listening) "Listening" else "Voice", selected = state.listening, onClick = onVoice)
                ActionButton(
                    if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    if (muted) "Unmute" else "Mute",
                    selected = muted,
                ) { vm.key(Keys.VOLUME_MUTE) }
                ActionButton(Icons.Rounded.Settings, "Settings", busy = state.opening == SETTINGS_PACKAGE, onClick = vm::openTvSettings)
            }
            DPad(onKey, dpad)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundKey(onKey, Keys.BACK, Icons.AutoMirrored.Rounded.ArrowBack, "Back", navKey)
                RoundKey(onKey, Keys.HOME, Icons.Rounded.Home, "Home", navKey)
                RoundKey(onKey, Keys.MENU, Icons.Rounded.Menu, "Menu", navKey)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Rocker(onKey, Keys.VOLUME_UP, Keys.VOLUME_DOWN, "VOL", Icons.Rounded.Add, Icons.Rounded.Remove, rockerKey, repeat = true)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KeyButton(onKey, Keys.MEDIA_PLAY_PAUSE, "Play or pause", Modifier.size(rockerKey + 8.dp), color = Palette.Raised) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(34.dp), tint = Palette.Text)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KeyButton(onKey, Keys.MEDIA_REWIND, "Rewind", Modifier.size(rockerKey - 14.dp)) {
                            Icon(Icons.Rounded.FastRewind, null, Modifier.size(22.dp), tint = Palette.Text)
                        }
                        KeyButton(onKey, Keys.MEDIA_FAST_FORWARD, "Fast forward", Modifier.size(rockerKey - 14.dp)) {
                            Icon(Icons.Rounded.FastForward, null, Modifier.size(22.dp), tint = Palette.Text)
                        }
                    }
                }
                Rocker(onKey, Keys.CHANNEL_UP, Keys.CHANNEL_DOWN, "CH", Icons.Rounded.KeyboardArrowUp, Icons.Rounded.KeyboardArrowDown, rockerKey)
            }
        }
    }
}

@Composable
internal fun ActionButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    busy: Boolean = false,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(Palette.accent()) else Modifier.background(Palette.Raised))
                .border(1.dp, if (accent) Palette.Indigo.copy(alpha = 0.5f) else Palette.Line, CircleShape)
                .clickable(onClickLabel = label, role = Role.Button) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Text, strokeWidth = 2.dp)
            } else {
                Icon(icon, label, Modifier.size(24.dp), tint = if (accent && !selected) Palette.Indigo else Palette.Text)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = Palette.Muted)
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
                .background(Brush.verticalGradient(listOf(Palette.Raised, Palette.Panel)))
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
                    .size(size * 0.4f)
                    .align(Alignment.Center)
                    .border(1.5.dp, Palette.Indigo.copy(alpha = 0.55f), CircleShape),
                brush = Brush.verticalGradient(listOf(Color(0xFF2E2E36), Color(0xFF212127))),
            ) {
                Text("OK", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun Arrow(icon: ImageVector) = Icon(icon, null, Modifier.size(42.dp), tint = Palette.Text)

@Composable
private fun RoundKey(onKey: (Int, Int) -> Unit, code: Int, icon: ImageVector, label: String, size: Dp) =
    KeyButton(onKey, code, label, Modifier.size(size)) {
        Icon(icon, null, Modifier.size(28.dp), tint = Palette.Text)
    }

@Composable
private fun Rocker(
    onKey: (Int, Int) -> Unit,
    up: Int,
    down: Int,
    label: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    keyHeight: Dp,
    repeat: Boolean = false,
) {
    Column(
        Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(39.dp))
            .background(Palette.Panel)
            .border(1.dp, Palette.Line, RoundedCornerShape(39.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KeyButton(onKey, up, "$label up", Modifier.fillMaxWidth().height(keyHeight), shape = RectangleShape, color = Color.Transparent, repeat = repeat) {
            Icon(upIcon, null, tint = Palette.Text)
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
        KeyButton(onKey, down, "$label down", Modifier.fillMaxWidth().height(keyHeight), shape = RectangleShape, color = Color.Transparent, repeat = repeat) {
            Icon(downIcon, null, tint = Palette.Text)
        }
    }
}

@Composable
private fun PairingPanel(state: UiState, vm: RemoteViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Logo(76.dp)
        Text("Pair My Tv with your Shoq box", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, textAlign = TextAlign.Center)
        Text(
            "Tap Start pairing, then type the 6-character code that appears on the TV. If the TV is off, turn it on with " +
                "the box's remote this one time: the box only lets a paired phone switch it on.",
            color = Palette.Muted,
            textAlign = TextAlign.Center,
        )
        when (state.pairing) {
            PairingStep.IDLE -> Button(onClick = vm::startPairing) { Text("Start pairing") }
            PairingStep.STARTING -> {
                CircularProgressIndicator(color = Palette.Indigo)
                Text("Asking the box for a code", color = Palette.Muted)
            }
            PairingStep.AWAITING_CODE, PairingStep.CHECKING -> CodeEntry(state, vm)
        }
        state.pairingMessage?.let { Text(it, color = Palette.Danger, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun CodeEntry(state: UiState, vm: RemoteViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    val checking = state.pairing == PairingStep.CHECKING
    OutlinedTextField(
        value = code,
        onValueChange = { typed -> code = typed.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6) },
        label = { Text("Code on the TV") },
        singleLine = true,
        enabled = !checking,
        textStyle = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 26.sp,
            letterSpacing = 6.sp,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (code.length == 6) vm.submitCode(code) }),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = vm::cancelPairing, enabled = !checking) { Text("Cancel") }
        Button(onClick = { vm.submitCode(code) }, enabled = code.length == 6 && !checking) {
            Text(if (checking) "Checking" else "Pair")
        }
    }
}
