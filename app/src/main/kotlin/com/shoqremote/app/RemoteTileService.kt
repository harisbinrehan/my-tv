package com.shoqremote.app

import android.app.Activity
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** The My Tv tile in the notification panel's quick settings; tapping it opens the remote. */
class RemoteTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        val paired = Storage(this).paired
        tile.state = if (paired) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (paired) "Remote" else "Not paired"
        tile.updateTile()
    }

    override fun onClick() {
        if (isLocked) unlockAndRun { openRemote() } else openRemote()
    }

    private fun openRemote() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Android 13+ can add the tile from inside the app; older phones add it by editing the panel. */
        fun requestAdd(activity: Activity, onResult: (String) -> Unit) {
            if (Build.VERSION.SDK_INT < 33) {
                onResult("Swipe down twice, tap the pencil, then drag My Tv into your tiles.")
                return
            }
            activity.getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(activity, RemoteTileService::class.java),
                activity.getString(R.string.app_name),
                Icon.createWithResource(activity, R.drawable.ic_tile),
                activity.mainExecutor,
            ) { result ->
                onResult(
                    when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "My Tv is now in your notification panel."
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "My Tv is already in your notification panel."
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Not added."
                        else -> "Couldn't add it from here. Swipe down twice, tap the pencil, then drag My Tv in."
                    },
                )
            }
        }
    }
}
