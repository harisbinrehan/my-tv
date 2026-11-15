package com.shoqremote.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the permanent TV notification back after the phone restarts or My Tv updates itself. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val storage = Storage(context)
        if (storage.paired && storage.notificationControls) TvLinkService.start(context)
    }
}
