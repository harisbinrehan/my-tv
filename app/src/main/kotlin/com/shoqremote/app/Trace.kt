package com.shoqremote.app

import android.util.Log
import com.shoqremote.atv.Keys

/**
 * The app's activity log, read over wireless debugging with `adb logcat -s MyTv`. It records what the app does
 * (connection, power, keys, pairing steps, opening apps, errors) and never what's typed: text shows only as a
 * length, and pairing codes never appear.
 */
internal object Trace {
    const val TAG = "MyTv"

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        Log.w(TAG, if (error == null) message else "$message: ${error::class.java.simpleName}: ${error.message}")
    }

    fun key(code: Int): String = Keys.byName.entries.firstOrNull { it.value == code }?.key ?: "key $code"

    fun state(s: UiState): String = buildString {
        append("link=").append(s.link)
        if (s.detail.isNotEmpty()) append(" (").append(s.detail).append(')')
        append(" power=").append(s.power?.let { if (it) "on" else "standby" } ?: "?")
        append(" app=").append(s.currentApp ?: "-")
        s.volume?.let { append(" volume=").append(it.level).append('/').append(it.max).append(if (it.muted) " muted" else "") }
        if (s.pairing != PairingStep.IDLE) append(" pairing=").append(s.pairing)
        s.opening?.let { append(" busy=").append(it) }
    }
}
