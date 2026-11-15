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
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PushPin
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
import com.shoqremote.app.CHANNEL_CATEGORIES
import com.shoqremote.app.CHANNEL_PREFIX
import com.shoqremote.app.RemoteViewModel
import com.shoqremote.app.TvChannel
import com.shoqremote.app.UiState
import com.shoqremote.atv.Keys

private const val ALL = "All"

/** Live TV channels: dial a number, search, filter by category, and keep pinned favourites in your own order. */
@Composable
internal fun TvPage(state: UiState, vm: RemoteViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ALL) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TvChannel?>(null) }
    val pinned = state.pinnedChannels.mapNotNull { number -> state.channels.firstOrNull { it.number == number } }
    val categories = listOf(ALL) + CHANNEL_CATEGORIES.filter { name -> state.channels.any { it.category == name } }
    val needle = query.trim()
    val shown = state.channels.filter { channel ->
        (category == ALL || channel.category == category) &&
            (needle.isEmpty() || channel.name.contains(needle, ignoreCase = true) || channel.number.toString().startsWith(needle))
    }
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { QuickDial(state, vm) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search channels by name or number") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Clear search") }
                },
            )
        }
        if (categories.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { name -> Chip(name, selected = name == category) { category = name } }
                }
            }
        }
        if (pinned.isNotEmpty() && needle.isEmpty() && category == ALL) {
            item { SectionLabel("Pinned") }
            itemsIndexed(pinned, key = { _, channel -> "pinned-${channel.number}" }) { index, channel ->
                ChannelRow(channel, state, vm, pinned = true, order = index to pinned.size, onEdit = { editing = channel })
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
        } else if (shown.isEmpty()) {
            item { Text("No channels match.", color = Palette.Muted) }
        }
        items(shown, key = { "channel-${it.number}" }) { channel ->
            ChannelRow(channel, state, vm, pinned = channel.number in state.pinnedChannels, order = null, onEdit = { editing = channel })
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

@Composable
private fun QuickDial(state: UiState, vm: RemoteViewModel) {
    var number by rememberSaveable { mutableStateOf("") }
    val go: () -> Unit = {
        number.toIntOrNull()?.let {
            vm.watchChannel(it)
            number = ""
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LiveTv, null, tint = Palette.Cyan)
            Spacer(Modifier.width(8.dp))
            Text("Go to channel", fontWeight = FontWeight.SemiBold, color = Palette.Text, modifier = Modifier.weight(1f))
            if (state.opening?.startsWith(CHANNEL_PREFIX) == true) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Cyan)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = number,
                onValueChange = { typed -> number = typed.filter(Char::isDigit).take(4) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Channel number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = go, enabled = number.isNotEmpty()) { Text("Go") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelStep(vm, Keys.CHANNEL_DOWN, Icons.Rounded.KeyboardArrowDown, "Channel down")
            ChannelStep(vm, Keys.CHANNEL_UP, Icons.Rounded.KeyboardArrowUp, "Channel up")
            Text("Live TV opens by itself when you pick a channel.", fontSize = 11.sp, color = Palette.Muted)
        }
    }
}

@Composable
private fun ChannelStep(vm: RemoteViewModel, code: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) =
    KeyButton(vm::key, code, label, Modifier.size(width = 72.dp, height = 44.dp), shape = RoundedCornerShape(12.dp), color = Palette.Raised) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = Palette.Text)
            Text("CH", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: TvChannel,
    state: UiState,
    vm: RemoteViewModel,
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
                onClick = { vm.watchChannel(channel.number) },
            )
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 54.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Raised),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Text)
            } else {
                Text("${channel.number}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Text)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, fontWeight = FontWeight.Medium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel.category, fontSize = 12.sp, color = Palette.Muted)
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
                if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                if (pinned) "Unpin" else "Pin",
                tint = if (pinned) Palette.Gold else Palette.Muted,
            )
        }
    }
}

@Composable
private fun EmptyChannels() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Panel).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Add your channels", fontWeight = FontWeight.SemiBold, color = Palette.Text)
        Text(
            "Tap Add channel and enter the number, name and category of each channel you watch. Pin favourites to keep them on top in your own order. Until then, type any number above to switch straight to it.",
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
