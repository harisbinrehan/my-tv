package com.shoqremote.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Battery-optimisation exemption, so Samsung and other phones don't put the app to sleep and drop the TV connection. */
object Background {
    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    @SuppressLint("BatteryLife")
    fun requestUnrestricted(context: Context) {
        val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (runCatching { context.startActivity(ask) }.isFailure) {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    /** Asks once, the first time the app is connected with the permanent notification on. */
    fun askOnce(context: Context) {
        val storage = Storage(context)
        if (storage.askedBackground || isUnrestricted(context)) return
        storage.askedBackground = true
        requestUnrestricted(context)
    }
}
