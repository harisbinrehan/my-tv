package com.shoqremote.app.ui

import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoqremote.app.HOME_PACKAGE
import com.shoqremote.app.LIVE_TV_PACKAGE
import com.shoqremote.app.RemoteViewModel
import com.shoqremote.app.SETTINGS_PACKAGE
import com.shoqremote.app.UiState
import com.shoqremote.app.fallbackLabel

@Composable
internal fun AppsPage(state: UiState, vm: RemoteViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier.fillMaxSize(),
        // The bottom gap keeps the floating remote button clear of the last row.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("Favourites", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                Spacer(Modifier.height(8.dp))
                if (state.favourites.isEmpty()) {
                    Text("Hold any app below and choose Add to favourites to keep it here.", fontSize = 13.sp, color = Palette.Muted)
                } else {
                    FavouritesRow(state, vm)
                }
                Spacer(Modifier.height(18.dp))
                Text("All apps", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                Spacer(Modifier.height(2.dp))
                Text("Tap an app to open it on the TV. Hold it for more options.", fontSize = 13.sp, color = Palette.Muted)
            }
        }
        items(state.apps, key = { it }) { pkg -> AppTile(pkg, state, vm, removable = true) }
        item(key = SETTINGS_PACKAGE) { AppTile(SETTINGS_PACKAGE, state, vm, removable = false) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "The box doesn't share its app list, so an app joins here the first time it is on the TV. With notification controls on, that includes apps you open with the physical remote.",
                fontSize = 12.sp,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(pkg: String, state: UiState, vm: RemoteViewModel, removable: Boolean) {
    val view = LocalView.current
    val favourite = pkg in state.favourites
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Panel)
                .combinedClickable(
                    onClickLabel = "Open on the TV",
                    onLongClickLabel = "More options",
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        menu = true
                    },
                    onClick = { vm.openApp(pkg) },
                )
                .padding(top = 16.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(pkg, state, 60.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                appName(pkg, state),
                fontSize = 12.sp,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (favourite) {
            Icon(Icons.Rounded.Star, "Favourite", Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp), tint = Palette.Gold)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (favourite) "Remove from favourites" else "Add to favourites") },
                leadingIcon = { Icon(if (favourite) Icons.Rounded.StarBorder else Icons.Rounded.Star, null) },
                onClick = {
                    menu = false
                    vm.toggleFavourite(pkg)
                },
            )
            if (removable) {
                DropdownMenuItem(
                    text = { Text("Remove from this list") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = {
                        menu = false
                        vm.removeApp(pkg)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavouritesRow(state: UiState, vm: RemoteViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.favourites, key = { it }) { pkg ->
            var menu by remember { mutableStateOf(false) }
            Box {
                Column(
                    Modifier
                        .width(66.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClickLabel = "Open on the TV",
                            onLongClick = { menu = true },
                            onClick = { vm.openApp(pkg) },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(pkg, state, 50.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(appName(pkg, state), fontSize = 11.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove from favourites") },
                        leadingIcon = { Icon(Icons.Rounded.StarBorder, null) },
                        onClick = {
                            menu = false
                            vm.toggleFavourite(pkg)
                        },
                    )
                }
            }
        }
    }
}

/** The app's official Play Store icon once fetched; until then a lettered tile, or a gear for Settings. */
@Composable
internal fun AppIcon(pkg: String, state: UiState, size: Dp) {
    val path = state.meta[pkg]?.iconPath
    val bitmap = remember(path) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    val shape = RoundedCornerShape(size * 0.26f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(bitmap, null, Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
            pkg in SYSTEM_ICONS -> Box(
                Modifier.size(size).clip(shape).background(if (pkg == LIVE_TV_PACKAGE) Palette.accent() else SolidColor(Palette.Raised)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SYSTEM_ICONS.getValue(pkg), null, Modifier.size(size * 0.5f), tint = Palette.Text)
            }
            else -> Box(
                Modifier.size(size).clip(shape).background(avatarColor(pkg)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    appName(pkg, state).take(1).uppercase(),
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        if (state.opening == pkg) {
            Box(
                Modifier.size(size).clip(shape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(size * 0.4f), color = Color.White, strokeWidth = 2.5.dp)
            }
        }
    }
}

internal fun appName(pkg: String, state: UiState): String = state.meta[pkg]?.label ?: fallbackLabel(pkg)

/** Built-in screens with no Play Store icon of their own. */
private val SYSTEM_ICONS = mapOf(
    SETTINGS_PACKAGE to Icons.Rounded.Settings,
    LIVE_TV_PACKAGE to Icons.Rounded.LiveTv,
    HOME_PACKAGE to Icons.Rounded.Home,
)

private val avatarColors = listOf(
    Color(0xFF6366F1),
    Color(0xFF0EA5E9),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFFEF4444),
    Color(0xFFA855F7),
)

private fun avatarColor(pkg: String): Color = avatarColors[Math.floorMod(pkg.hashCode(), avatarColors.size)]
