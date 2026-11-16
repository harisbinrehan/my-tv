package com.shoqremote.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoqremote.app.HOME_PACKAGE
import com.shoqremote.app.Link
import com.shoqremote.app.RemoteViewModel
import com.shoqremote.app.UiState
import com.shoqremote.app.YOUTUBE_PACKAGE
import com.shoqremote.atv.Keys
import kotlinx.coroutines.delay

private val LINE_BREAKS = Regex("[\\r\\n\\t]+")

/**
 * Typing on the TV, one tap from anywhere: the phone keyboard opens with the panel and every change appears in the
 * TV's focused text box as it's made. It also opens by itself if the TV asks for a keyboard.
 */
@Composable
internal fun TypePanel(state: UiState, vm: RemoteViewModel, onClose: () -> Unit) {
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val onTv by vm.onTv.collectAsStateWithLifecycle()
    var field by remember { vm.typedText.let { mutableStateOf(TextFieldValue(it, TextRange(it.length))) } }
    var focused by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf<String?>(null) }
    val youTube = state.currentApp == YOUTUBE_PACKAGE
    val connected = state.link == Link.CONNECTED

    // One line only: a newline would reach the TV as Enter and submit half the text.
    fun setText(text: String, cursor: Int = text.length) {
        val clean = text.replace(LINE_BREAKS, " ")
        field = TextFieldValue(clean, TextRange(cursor.coerceIn(0, clean.length)))
        vm.typeLive(clean)
    }

    val close: () -> Unit = {
        keyboard?.hide()
        onClose()
    }
    val submit: (String) -> Unit = { text ->
        if (connected) {
            if (text != field.text) setText(text)
            if (youTube && text.isNotBlank()) vm.searchYouTube(text) else vm.submitTyped()
            field = TextFieldValue("")
            close()
        }
    }
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            val current = field.text
            setText(if (current.isBlank()) spoken else "${current.trimEnd()} $spoken")
        }
        focus.requestFocus()
    }
    val backspace by rememberUpdatedState { repeat: Boolean ->
        val text = field.text
        if (text.isNotEmpty()) {
            setText(text.substring(0, text.offsetByCodePoints(text.length, -1)))
        } else if (!repeat && connected) {
            // Deletes a character this app didn't type; never repeated, as an empty box may take it as Back.
            vm.key(Keys.DEL)
        }
    }

    BackHandler(onBack = close)
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(2_200)
            flash = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Palette.Sheet)
                .border(1.dp, Palette.Line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(Palette.Line))
            Header(state, onClose = close)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.Raised)
                    .border(1.dp, if (focused) Palette.Indigo.copy(alpha = 0.8f) else Palette.Line, RoundedCornerShape(18.dp))
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty()) {
                        Text(
                            if (youTube) "Search YouTube" else "Type on your TV",
                            fontSize = 18.sp,
                            color = Palette.Muted.copy(alpha = 0.7f),
                        )
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { value ->
                            // Keep the keyboard's composing region: rebuilding the value breaks autocorrect and can
                            // make some keyboards repeat letters. Only a pasted line break forces a rebuild.
                            val changed = value.text != field.text
                            if (LINE_BREAKS.containsMatchIn(value.text)) {
                                setText(value.text, value.selection.end)
                            } else {
                                field = value
                                if (changed) vm.typeLive(value.text)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged { focused = it.isFocused },
                        singleLine = true,
                        textStyle = TextStyle(color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(Palette.Indigo),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(field.text) }, onDone = { submit(field.text) }),
                    )
                }
                FieldIcon(Icons.Rounded.Mic, "Speak to type") {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak, and My Tv types it on the TV")
                    try {
                        dictation.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        flash = "This phone has no speech-to-text app"
                    }
                }
            }
            StatusLine(
                flash = flash,
                connected = connected,
                typed = field.text,
                onTv = onTv,
                youTube = youTube,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                var deleting by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (deleting) Palette.Line else Palette.Raised)
                        .border(1.dp, Palette.Line, RoundedCornerShape(14.dp))
                        .semantics { contentDescription = "Delete" }
                        .holdToRepeat(onPressed = { deleting = it }) { repeat ->
                            view.performHapticFeedback(if (repeat) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.VIRTUAL_KEY)
                            backspace(repeat)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, null, Modifier.size(20.dp), tint = Palette.Text)
                }
                AnimatedVisibility(field.text.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    Pill(null, "Clear") { setText("") }
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("Search", enabled = connected) { submit(field.text) }
            }
        }
    }
}

@Composable
private fun Header(state: UiState, onClose: () -> Unit) {
    val app = state.currentApp
    val into = when {
        state.link != Link.CONNECTED -> "Not connected"
        app == null || app == HOME_PACKAGE -> "Into the TV's text box"
        else -> "Into ${appName(app, state)}"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Palette.accent()), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Keyboard, null, Modifier.size(20.dp), tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Type on TV", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            Text(into, fontSize = 12.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FieldIcon(Icons.Rounded.KeyboardHide, "Close keyboard", onClose)
    }
}

/** Says what actually happened: the TV holds the text, it's still sending, or the box is unreachable. */
@Composable
private fun StatusLine(flash: String?, connected: Boolean, typed: String, onTv: String, youTube: Boolean) {
    val (text, color, done) = when {
        flash != null -> Triple(flash, Palette.Text, false)
        !connected -> Triple("Not connected. What you type is sent when the TV is back.", Palette.Warn, false)
        typed.isEmpty() && onTv.isEmpty() -> Triple(
            if (youTube) "Type what to watch, then Search. No need to open YouTube's search box."
            else "Pick a text box on the TV first. Letters appear as you type.",
            Palette.Muted,
            false,
        )
        typed == onTv -> Triple("On your TV", Palette.Good, true)
        else -> Triple("Sending...", Palette.Muted, false)
    }
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(15.dp), tint = Palette.Good)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = color, maxLines = 2)
    }
}

@Composable
private fun FieldIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = Palette.Muted)
    }
}

@Composable
private fun Pill(icon: ImageVector?, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Raised)
            .border(1.dp, Palette.Line, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = Palette.Text)
            Spacer(Modifier.width(7.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.background(Palette.accent()) else Modifier.background(Palette.Line))
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = if (enabled) Color.White else Palette.Muted)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.White else Palette.Muted, maxLines = 1)
    }
}
