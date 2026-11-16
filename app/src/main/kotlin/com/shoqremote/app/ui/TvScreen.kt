package com.shoqremote.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoqremote.app.Link
import com.shoqremote.app.CHANNEL_CATEGORIES
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.shoqremote.app.CHANNEL_PREFIX
import com.shoqremote.app.ChannelLogos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.shoqremote.app.RemoteViewModel
import com.shoqremote.app.TvChannel
import com.shoqremote.app.UiState

private const val ALL = "All"

/** Live TV channels with their logos: dial a number, search, filter by category, and star favourites to keep them on top. */
@Composable
internal fun TvPage(state: UiState, vm: RemoteViewModel, onWatching: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val watch: (Int) -> Unit = { number ->
        val connected = state.link == Link.CONNECTED
        keyboard?.hide()
        vm.watchChannel(number)
        if (connected) onWatching()
    }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ALL) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TvChannel?>(null) }
    val pinned = state.pinnedChannels.mapNotNull { number -> state.channels.firstOrNull { it.number == number } }
    val categories = listOf(ALL) + CHANNEL_CATEGORIES.filter { name -> state.channels.any { it.category == name } }
    val needle = query.trim()
    // A typed number can go straight to that channel, listed or not.
    val dial = needle.takeIf { it.length in 1..4 && it.all(Char::isDigit) }?.toIntOrNull()
    val shown = state.channels.filter { channel ->
        (category == ALL || channel.category == category) &&
            (needle.isEmpty() || channel.name.contains(needle, ignoreCase = true) || channel.number.toString().startsWith(needle))
    }
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        // The bottom gap keeps the floating Type button clear of the last row's star.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search, or type a channel number") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Clear search") }
                },
                keyboardOptions = KeyboardOptions(imeAction = if (dial != null) ImeAction.Go else ImeAction.Search),
                keyboardActions = KeyboardActions(onGo = { dial?.let(watch) }),
            )
        }
        if (dial != null) {
            item(key = "dial") { DialRow(dial, state, watch) }
        }
        if (categories.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { name -> Chip(name, selected = name == category) { category = name } }
                }
            }
        }
        if (pinned.isNotEmpty() && needle.isEmpty() && category == ALL) {
            item { SectionLabel("Favourites") }
            itemsIndexed(pinned, key = { _, channel -> "pinned-${channel.number}" }) { index, channel ->
                ChannelRow(channel, state, vm, watch, pinned = true, order = index to pinned.size, onEdit = { editing = channel })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(if (category == ALL) "All channels" else category, Modifier.weight(1f))
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add channel")
                }
            }
        }
        if (state.channels.isEmpty()) {
            item { EmptyChannels() }
        } else if (shown.isEmpty() && dial == null) {
            item { Text("No channels match.", color = Palette.Muted) }
        }
        items(shown, key = { "channel-${it.number}" }) { channel ->
            ChannelRow(channel, state, vm, watch, pinned = channel.number in state.pinnedChannels, order = null, onEdit = { editing = channel })
        }
    }
    if (adding || editing != null) {
        val current = editing
        ChannelDialog(
            initial = current,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { channel ->
                vm.saveChannel(channel, current?.number)
                adding = false
                editing = null
            },
            onDelete = current?.let { channel ->
                {
                    vm.deleteChannel(channel.number)
                    editing = null
                }
            },
        )
    }
}

/** "Watch channel 123" for a typed number, so any channel works even before it's in the list. */
@Composable
private fun DialRow(number: Int, state: UiState, watch: (Int) -> Unit) {
    val busy = state.opening == "$CHANNEL_PREFIX$number"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Raised)
            .clickable(onClickLabel = "Watch channel $number") { watch(number) }
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LiveTv, null, tint = Palette.Indigo)
        Spacer(Modifier.width(12.dp))
        Text("Watch channel $number", fontWeight = FontWeight.SemiBold, color = Palette.Text, modifier = Modifier.weight(1f))
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Palette.Indigo)
        } else {
            Button(onClick = { watch(number) }) { Text("Go") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: TvChannel,
    state: UiState,
    vm: RemoteViewModel,
    watch: (Int) -> Unit,
    pinned: Boolean,
    order: Pair<Int, Int>?,
    onEdit: () -> Unit,
) {
    val busy = state.opening == "$CHANNEL_PREFIX${channel.number}"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Panel)
            .combinedClickable(
                onClickLabel = "Watch",
                onLongClickLabel = "Edit channel",
                onLongClick = onEdit,
                onClick = { watch(channel.number) },
            )
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel, busy, vm.logos)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, fontWeight = FontWeight.Medium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${channel.number}  ·  ${channel.category}", fontSize = 12.sp, color = Palette.Muted, maxLines = 1)
        }
        if (order != null) {
            val (index, size) = order
            IconButton(onClick = { vm.movePinnedChannel(channel.number, -1) }, enabled = index > 0) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = if (index > 0) Palette.Text else Palette.Line)
            }
            IconButton(onClick = { vm.movePinnedChannel(channel.number, 1) }, enabled = index < size - 1) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Move down", tint = if (index < size - 1) Palette.Text else Palette.Line)
            }
        }
        IconButton(onClick = { vm.togglePinChannel(channel.number) }) {
            Icon(
                if (pinned) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                if (pinned) "Remove from favourites" else "Add to favourites",
                tint = if (pinned) Palette.Gold else Palette.Muted,
            )
        }
    }
}

/**
 * The channel's logo on a light tile (most logos are drawn for light backgrounds), or its number until the logo
 * arrives or when it has none; a spinner while the TV switches to it.
 */
@Composable
private fun ChannelLogo(channel: TvChannel, busy: Boolean, logos: ChannelLogos) {
    val logo by produceState<ImageBitmap?>(null, channel.name) {
        value = withContext(Dispatchers.IO) { logos.bitmap(channel.name)?.asImageBitmap() }
    }
    val image = logo
    Box(
        Modifier
            .size(width = 64.dp, height = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (image != null) LogoTile else Palette.Raised),
        contentAlignment = Alignment.Center,
    ) {
        when {
            busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = if (image != null) Palette.Background else Palette.Text)
            image != null -> Image(image, null, Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp), contentScale = ContentScale.Fit)
            else -> Text("${channel.number}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Text)
        }
    }
}

private val LogoTile = Color(0xFFEDEDF0)

@Composable
private fun EmptyChannels() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Panel).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Add your channels", fontWeight = FontWeight.SemiBold, color = Palette.Text)
        Text(
            "Tap Add channel and enter the number and name of a channel; its logo appears by itself. Star favourites to keep them on top in your own order. Until then, type any number above to switch straight to it.",
            fontSize = 13.sp,
            color = Palette.Muted,
        )
    }
}

@Composable
private fun ChannelDialog(
    initial: TvChannel?,
    onDismiss: () -> Unit,
    onSave: (TvChannel) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var number by remember { mutableStateOf(initial?.number?.toString().orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category ?: CHANNEL_CATEGORIES.first()) }
    val parsed = number.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add channel" else "Edit channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { typed -> number = typed.filter(Char::isDigit).take(4) },
                    label = { Text("Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Category", fontSize = 12.sp, color = Palette.Muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CHANNEL_CATEGORIES) { option -> Chip(option, selected = option == category) { category = option } }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (parsed != null) onSave(TvChannel(parsed, name.trim(), category)) },
                enabled = parsed != null && name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = Palette.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (selected) Color.White else Palette.Text,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .then(if (selected) Modifier.background(Palette.accent()) else Modifier.background(Palette.Raised))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted, modifier = modifier.padding(top = 4.dp))
}
