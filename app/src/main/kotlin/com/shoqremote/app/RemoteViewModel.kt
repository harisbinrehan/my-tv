package com.shoqremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.shoqremote.atv.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The screens' handle on the shared TvLink; it holds the connection open while a screen is visible. */
class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val link = (app as MyTvApp).link
    private val updater = (app as MyTvApp).updater
    val logos: ChannelLogos = (app as MyTvApp).logos
    val state: StateFlow<UiState> = link.state
    val update: StateFlow<UpdateState> = updater.state

    fun checkForUpdate(manual: Boolean = false) = updater.check(manual)

    fun installUpdate(context: android.content.Context) = updater.install(context)

    fun dismissUpdate() = updater.dismiss()

    fun announceUpdate() = updater.announceIfUpdated()
    val messages: Flow<String> = link.messages

    fun start() = link.acquire(HOLDER)

    fun stop() = link.release(HOLDER)

    fun key(code: Int, direction: Int = Direction.SHORT) = link.key(code, direction)

    fun power() = link.power()

    fun turnOn() = link.turnOn()

    val onTv: StateFlow<String> = link.onTv
    val imeRequests: Flow<Unit> = link.imeRequests
    val typedText: String get() = link.typedText

    fun typeLive(value: String) = link.typeLive(value)

    fun clearTyped() = link.clearTyped()

    fun submitTyped() = link.submitTyped()

    fun forgetTyped() = link.forgetTyped()

    fun searchYouTube(query: String) = link.searchYouTube(query)

    fun notify(text: String) = link.notify(text)

    fun openApp(packageName: String) = link.openApp(packageName)

    fun openTvSettings() = link.openTvSettings()

    fun watchChannel(number: Int) = link.watchChannel(number)

    fun toggleVoiceSearch() = link.toggleVoiceSearch()

    fun toggleFavourite(packageName: String) = link.toggleFavourite(packageName)

    fun removeApp(packageName: String) = link.removeApp(packageName)

    fun saveChannel(channel: TvChannel, replacing: Int? = null) = link.saveChannel(channel, replacing)

    fun deleteChannel(number: Int) = link.deleteChannel(number)

    fun togglePinChannel(number: Int) = link.togglePinChannel(number)

    fun movePinnedChannel(number: Int, by: Int) = link.movePinnedChannel(number, by)

    fun setNotificationControls(on: Boolean) = link.setNotificationControls(on)

    fun forgetPairing() = link.forgetPairing()

    fun startPairing() = link.startPairing()

    fun submitCode(code: String) = link.submitCode(code)

    fun cancelPairing() = link.cancelPairing()

    override fun onCleared() {
        link.release(HOLDER)
    }

    private companion object {
        const val HOLDER = "screen"
    }
}
