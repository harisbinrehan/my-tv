package com.shoqremote.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.shoqremote.app.fallbackLabel
import com.shoqremote.app.Background
import com.shoqremote.app.HOME_PACKAGE
import com.shoqremote.app.Link
import com.shoqremote.app.POWER_TARGET
import com.shoqremote.app.PairingStep
import com.shoqremote.app.RemoteTileService
import com.shoqremote.app.RemoteViewModel
import com.shoqremote.app.TvLinkService
import com.shoqremote.app.UiState
import com.shoqremote.atv.Keys
import com.shoqremote.atv.ShoqBox

/** The bottom bar's sections; the remote is the home page, between Apps and TV. */
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
                        TopArea(state, vm)
                        UpdateBanner(vm)
                    }
                },
                bottomBar = { if (paired) BottomBar(section) { section = it } },
                floatingActionButton = {
                    AnimatedVisibility(paired && !typing, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        TypeFab(onType)
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when {
                        !paired -> PairingPanel(state, vm)
                        section == Section.REMOTE -> RemotePage(state, vm, onVoice)
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
private fun TopArea(state: UiState, vm: RemoteViewModel) {
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
        if (paired) PowerButton(state, vm::power)
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

/** Typing is the most used feature, so it floats above the bottom bar on every page, one tap away. */
@Composable
private fun TypeFab(onClick: () -> Unit) {
    val view = LocalView.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .size(56.dp)
            .shadow(10.dp, shape, ambientColor = Palette.Indigo, spotColor = Palette.Indigo)
            .clip(shape)
            .background(Palette.accent())
            .clickable(onClickLabel = "Type on the TV", role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Keyboard, "Type", Modifier.size(26.dp), tint = Color.White)
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

/**
 * A slim strip when a newer My Tv is published, so it never crowds the remote: Update starts it, the strip
 * opens the details (what's new, Later, Update now), and while it runs a progress bar takes the buttons' place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateBanner(vm: RemoteViewModel) {
    val update by vm.update.collectAsStateWithLifecycle()
    val release = update.available ?: return
    val busy = update.progress != null || update.installing
    if (update.dismissed && !busy) return
    val context = LocalContext.current
    var details by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Panel)
            .border(1.dp, Palette.Indigo.copy(alpha = 0.35f), shape)
            .clickable(enabled = !busy, onClickLabel = "What's new") { details = true },
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Palette.Indigo.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(20.dp), tint = Palette.Indigo)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (update.installing) "Installing update" else if (busy) "Downloading update" else "Update ready",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.Text,
                    maxLines = 1,
                )
                Text(
                    when {
                        update.installing -> "My Tv ${release.versionName}: confirm on the next screen"
                        busy -> "My Tv ${release.versionName}: ${update.progress}%"
                        else -> "My Tv ${release.versionName}  ·  What's new ›"
                    },
                    fontSize = 12.sp,
                    color = Palette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (busy) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), strokeWidth = 2.dp, color = Palette.Indigo)
            } else {
                Button(
                    onClick = { vm.installUpdate(context) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                ) { Text("Update", fontSize = 14.sp) }
                IconButton(onClick = vm::dismissUpdate) { Icon(Icons.Rounded.Close, "Later", tint = Palette.Muted) }
            }
        }
        update.progress?.let { percent ->
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Palette.Indigo,
                trackColor = Palette.Line,
                drawStopIndicator = {},
            )
        }
    }
    if (details && !busy) {
        ModalBottomSheet(
            onDismissRequest = { details = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Palette.Sheet,
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Logo(44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("My Tv ${release.versionName}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                        Text("You have ${update.installedName}", fontSize = 13.sp, color = Palette.Muted)
                    }
                }
                if (release.notes.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("What's new", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Indigo)
                        Text(release.notes, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.Text)
                    }
                }
                Text(
                    "Your pairing and settings stay. My Tv closes while it updates; open it again when it's done.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Palette.Muted,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            details = false
                            vm.dismissUpdate()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, Palette.Line),
                    ) { Text("Later", color = Palette.Text) }
                    Button(
                        onClick = {
                            details = false
                            vm.installUpdate(context)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Update now") }
                }
            }
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
            if (state.link == Link.CONNECTED) {
                DropdownMenuItem(
                    text = { Text("TV settings") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                    onClick = {
                        open = false
                        vm.openTvSettings()
                    },
                )
            }
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
private fun BottomBar(section: Section, onSelect: (Section) -> Unit) {
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
            NavItem(section, Section.REMOTE, Icons.Rounded.SettingsRemote, "Remote", colors, onSelect)
            NavItem(section, Section.TV, Icons.Rounded.LiveTv, "TV", colors, onSelect)
        }
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

/**
 * Built for one thumb and grouped by job: what's on the TV at the top (when there's room), then sound and
 * channels, the arrows, Back / Voice / Home, and the media keys lowest, beside the floating Type button. Sized from
 * the space available, so it never scrolls.
 */
@Composable
private fun RemotePage(state: UiState, vm: RemoteViewModel, onVoice: () -> Unit) {
    val onKey: (Int, Int) -> Unit = vm::key
    val muted = state.volume?.muted == true
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)) {
        val compact = maxHeight < 600.dp
        val gap = if (compact) 12.dp else 16.dp
        val row = if (compact) 50.dp else 56.dp
        val navKey = if (compact) 56.dp else 64.dp
        val fixed = row * 2 + navKey + gap * 3
        // The card only shows when the arrows still get 250 dp.
        val showNow = maxHeight - fixed - NOW_CARD - gap >= 250.dp
        val dpad = (maxHeight - fixed - if (showNow) NOW_CARD + gap else 0.dp)
            .coerceIn(170.dp, 300.dp)
            .coerceAtMost(maxWidth - 24.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.Bottom),
        ) {
            if (showNow) {
                NowOnTv(state)
                Spacer(Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundToggle(
                    if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    if (muted) "Unmute" else "Mute",
                    selected = muted,
                    size = row,
                ) { vm.key(Keys.VOLUME_MUTE) }
                Rocker(onKey, Keys.VOLUME_DOWN, Keys.VOLUME_UP, "VOL", Icons.Rounded.Remove, Icons.Rounded.Add, Modifier.weight(1f).height(row), repeat = true)
                Rocker(onKey, Keys.CHANNEL_DOWN, Keys.CHANNEL_UP, "CH", Icons.Rounded.KeyboardArrowDown, Icons.Rounded.KeyboardArrowUp, Modifier.weight(1f).height(row))
            }
            DPad(onKey, dpad)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RoundKey(onKey, Keys.BACK, Icons.AutoMirrored.Rounded.ArrowBack, "Back", navKey)
                RoundToggle(Icons.Rounded.Mic, if (state.listening) "Stop voice search" else "Voice search", selected = state.listening, size = navKey, onClick = onVoice)
                RoundKey(onKey, Keys.HOME, Icons.Rounded.Home, "Home", navKey)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MediaBar(onKey, Modifier.weight(1f).height(row))
                // The floating Type button sits here, in line with the media keys.
                Spacer(Modifier.width(72.dp))
            }
        }
    }
}

private val NOW_CARD = 68.dp

/** What the TV is showing, with the app's icon, and the volume as a bar. */
@Composable
private fun NowOnTv(state: UiState) {
    val app = state.currentApp
    val connected = state.link == Link.CONNECTED
    val on = connected && state.power != false
    val title = when {
        !connected -> "Not connected"
        !on -> "TV in standby"
        app == null || app == HOME_PACKAGE -> "Home screen"
        else -> state.meta[app]?.label ?: fallbackLabel(app)
    }
    val path = app?.takeIf { on }?.let { state.meta[it]?.iconPath }
    val icon = remember(path) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    val volume = state.volume?.takeIf { on }
    Row(
        Modifier.fillMaxWidth().height(NOW_CARD).clip(RoundedCornerShape(20.dp)).background(Palette.Panel).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Raised), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(icon, null, Modifier.fillMaxSize())
            } else {
                Icon(
                    if (!on) Icons.Rounded.PowerSettingsNew else if (app == null || app == HOME_PACKAGE) Icons.Rounded.Home else Icons.Rounded.LiveTv,
                    null,
                    Modifier.size(22.dp),
                    tint = Palette.Muted,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (volume != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { if (volume.muted || volume.max <= 0) 0f else volume.level.toFloat() / volume.max },
                        modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = Palette.Indigo,
                        trackColor = Palette.Line,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(if (volume.muted) "Muted" else "${volume.level}", fontSize = 12.sp, color = Palette.Muted)
                }
            } else {
                Text(if (on) "On your TV" else "Tap power to turn it on", fontSize = 12.sp, color = Palette.Muted)
            }
        }
    }
}

/** A round on/off key: filled with the accent while on (muted, listening), its icon showing the current state. */
@Composable
private fun RoundToggle(icon: ImageVector, label: String, selected: Boolean, size: Dp, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(Palette.accent()) else Modifier.background(Palette.Raised))
            .border(1.dp, if (selected) Color.Transparent else Palette.Line, CircleShape)
            .clickable(onClickLabel = label, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(24.dp), tint = Palette.Text)
    }
}

/** Rewind, play or pause, and fast forward as one bar. */
@Composable
private fun MediaBar(onKey: (Int, Int) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Row(modifier.clip(shape).background(Palette.Panel).border(1.dp, Palette.Line, shape), verticalAlignment = Alignment.CenterVertically) {
        KeyButton(onKey, Keys.MEDIA_REWIND, "Rewind", Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent) {
            Icon(Icons.Rounded.FastRewind, null, Modifier.size(24.dp), tint = Palette.Text)
        }
        KeyButton(onKey, Keys.MEDIA_PLAY_PAUSE, "Play or pause", Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(30.dp), tint = Palette.Text)
        }
        KeyButton(onKey, Keys.MEDIA_FAST_FORWARD, "Fast forward", Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent) {
            Icon(Icons.Rounded.FastForward, null, Modifier.size(24.dp), tint = Palette.Text)
        }
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

/** A wide two-way key, lower on the left and higher on the right, with its name in the middle. */
@Composable
private fun Rocker(
    onKey: (Int, Int) -> Unit,
    down: Int,
    up: Int,
    label: String,
    downIcon: ImageVector,
    upIcon: ImageVector,
    modifier: Modifier,
    repeat: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier.clip(shape).background(Palette.Panel).border(1.dp, Palette.Line, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyButton(onKey, down, "$label down", Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent, repeat = repeat) {
            Icon(downIcon, null, tint = Palette.Text)
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
        KeyButton(onKey, up, "$label up", Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape, color = Color.Transparent, repeat = repeat) {
            Icon(upIcon, null, tint = Palette.Text)
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
