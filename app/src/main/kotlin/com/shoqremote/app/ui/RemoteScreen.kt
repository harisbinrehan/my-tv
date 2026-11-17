package com.shoqremote.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.Widgets
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoqremote.app.Widgets
import com.shoqremote.app.Background
import com.shoqremote.app.HOME_PACKAGE
import com.shoqremote.app.Link
import com.shoqremote.app.POWER_TARGET
import com.shoqremote.app.PairingStep
import com.shoqremote.app.RemoteTileService
import com.shoqremote.app.RemoteViewModel
import com.shoqremote.app.TvLinkService
import com.shoqremote.app.UiState
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
    // What the listening banner suggests saying when the mic opens an app by name.
    var voiceHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.listening) { if (!state.listening) voiceHint = null }
    val openByVoice: (String) -> Unit = { pkg ->
        vm.prepareVoiceOpen(pkg)
        voiceHint = "Say \"Open ${vm.appName(pkg)}\""
        section = Section.REMOTE
        if (!state.listening) onVoice()
    }
    val codePrompt by vm.codePrompt.collectAsStateWithLifecycle()
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
                    // The remote has its own Keyboard tile; Apps and TV get the floating button.
                    AnimatedVisibility(paired && !typing && section != Section.REMOTE, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        TypeFab(onType)
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when {
                        !paired -> PairingPanel(state, vm)
                        section == Section.REMOTE -> RemotePage(state, vm, onVoice, onType) { section = Section.APPS }
                        // Opening an app or a channel brings you straight to the remote to control it.
                        section == Section.APPS -> AppsPage(state, vm, openByVoice) { section = Section.REMOTE }
                        section == Section.TV -> TvPage(state, vm) { section = Section.REMOTE }
                    }
                    if (state.listening) {
                        ListeningBanner(Modifier.align(Alignment.BottomCenter), voiceHint, onStop = vm::toggleVoiceSearch)
                    } else {
                        codePrompt?.let { prompt ->
                            CodeBanner(Modifier.align(Alignment.BottomCenter), prompt.name, prompt.code, onEnter = vm::enterCode, onDismiss = vm::dismissCode)
                        }
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

/** Typing is the most used feature: on Apps and TV it floats above the bottom bar (the remote has a Keyboard tile). */
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
    var choosingWidget by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity
    if (choosingWidget) {
        AlertDialog(
            onDismissRequest = { choosingWidget = false },
            title = { Text("Add a widget") },
            text = {
                Column {
                    Widgets.choices.forEach { (name, provider) ->
                        TextButton(
                            onClick = {
                                choosingWidget = false
                                if (!Widgets.requestPin(context, provider)) {
                                    vm.notify("Long-press your home screen, tap Widgets, then find My Tv.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingWidget = false }) { Text("Cancel") } },
        )
    }
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
            DropdownMenuItem(
                text = { Text("Phone volume buttons control the TV") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) },
                trailingIcon = { if (state.volumeButtons) Icon(Icons.Rounded.Check, "On") },
                onClick = {
                    open = false
                    vm.setVolumeButtons(!state.volumeButtons)
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
                text = { Text("Add a widget") },
                leadingIcon = { Icon(Icons.Rounded.Widgets, null) },
                onClick = {
                    open = false
                    choosingWidget = true
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
private fun ListeningBanner(modifier: Modifier, hint: String?, onStop: () -> Unit) {
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
            Text(hint ?: "Listening", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                if (hint != null) "Listening: speak now" else "Speak to your TV, for example \"Open YouTube\"",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
            )
        }
        TextButton(onClick = onStop) { Text("Stop", color = Color.White) }
    }
}

/**
 * After opening an app by name that has a code (NetMirror's OTP): one tap types it and presses Enter. My Tv can't see
 * the app's screens, so it only types on request, or when the box says a text box got focus.
 */
@Composable
private fun CodeBanner(modifier: Modifier, name: String, code: String, onEnter: () -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Sheet)
            .border(1.dp, Palette.Indigo.copy(alpha = 0.5f), shape)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$name asking for a code?", color = Palette.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Tap to type it on the TV", color = Palette.Muted, fontSize = 12.sp)
        }
        Button(onClick = onEnter, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), modifier = Modifier.height(38.dp)) {
            Text("Enter $code", fontSize = 13.sp)
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Not now", tint = Palette.Muted) }
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
