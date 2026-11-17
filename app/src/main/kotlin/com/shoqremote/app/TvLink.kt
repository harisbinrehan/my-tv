package com.shoqremote.app

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.shoqremote.atv.BadCodeException
import com.shoqremote.atv.ClientIdentity
import com.shoqremote.atv.DeviceInfo
import com.shoqremote.atv.Direction
import com.shoqremote.atv.Keys
import com.shoqremote.atv.NotPairedException
import com.shoqremote.atv.PairingSession
import com.shoqremote.atv.RemoteClient
import com.shoqremote.atv.RemoteListener
import com.shoqremote.atv.ShoqBox
import com.shoqremote.atv.TextMirror
import com.shoqremote.atv.WakeOnLan
import com.shoqremote.atv.WrongDeviceException
import com.shoqremote.atv.causeOf
import com.shoqremote.atv.openTls
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class Link { NOT_PAIRED, CONNECTING, CONNECTED, OFFLINE }

enum class PairingStep { IDLE, STARTING, AWAITING_CODE, CHECKING }

data class Volume(val level: Int, val max: Int, val muted: Boolean)

data class TvChannel(val number: Int, val name: String, val category: String)

data class UiState(
    val link: Link,
    val detail: String = "",
    val device: DeviceInfo? = null,
    val power: Boolean? = null,
    val volume: Volume? = null,
    val currentApp: String? = null,
    val apps: List<String> = emptyList(),
    val favourites: List<String> = emptyList(),
    val meta: Map<String, AppMeta> = emptyMap(),
    val channels: List<TvChannel> = emptyList(),
    val pinnedChannels: List<Int> = emptyList(),
    val opening: String? = null,
    val listening: Boolean = false,
    val notificationControls: Boolean = true,
    val pairing: PairingStep = PairingStep.IDLE,
    val pairingMessage: String? = null,
)

const val SETTINGS_PACKAGE = "com.android.tv.settings"
const val HOME_PACKAGE = "com.launcher.ptcl"
const val LIVE_TV_PACKAGE = "com.zte.CTBrowser"
const val CHANNEL_PREFIX = "channel:"
const val POWER_TARGET = "power"
const val YOUTUBE_PACKAGE = "com.google.android.youtube.tv"
private const val YOUTUBE_SEARCH = "https://www.youtube.com/results?search_query="
private const val PLAY_STORE = "com.android.vending"
private const val ASSISTANT = "com.google.android.katniss"
private const val NOT_FOUND = "Can't find the Shoq box. Is it on, and is this phone on the same Wi-Fi?"
private const val NOT_CONNECTED = "Not connected to the box yet."
internal const val NO_WIFI ="This phone isn't on Wi-Fi. Join your home Wi-Fi to reach the TV."
private const val MAX_BACKOFF_MS = 30_000L
private const val WIFI_RECHECK_MS = 60_000L
private const val VOICE_RATE = 8_000
private const val VOICE_CHUNK = 16 * 1024
private const val VOICE_MAX_MS = 10_000L
private const val MAX_VOLUME_STEPS = 30


val CHANNEL_CATEGORIES = listOf("News", "Sports", "Entertainment", "Movies", "Kids", "Islamic", "Infotainment", "Music", "Regional", "Other")

/** Screens the box reports as apps that don't belong in the Apps grid; live TV has its own section. */
private val NOT_APPS = setOf(
    HOME_PACKAGE, LIVE_TV_PACKAGE, "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
    SETTINGS_PACKAGE, ASSISTANT, "com.android.systemui", "com.google.android.tungsten.setupwraith", "android",
)

/** Web addresses these apps claim; this box ignores market:// and intent: links, so other apps open via Play Store. */
private val DIRECT_LINKS = mapOf(
    "com.google.android.youtube.tv" to "https://www.youtube.com",
    "com.google.android.youtube.tvmusic" to "https://music.youtube.com",
    "com.netflix.ninja" to "https://www.netflix.com/title",
    "com.amazon.amazonvideo.livingroom" to "https://app.primevideo.com",
    "com.disney.disneyplus" to "https://www.disneyplus.com",
    // The store claims its own web addresses; this opens its home rather than its own listing page.
    PLAY_STORE to "https://play.google.com/store/apps",
)

/**
 * The one connection to the box, shared by the screens and the notification. It runs while either needs
 * it, keeps what the box reports (power, volume, the app on screen), and carries every command to the TV.
 */
class TvLink(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storage = Storage(app)
    private val finder = BoxFinder(app)
    private val catalog = AppCatalog(storage)
    private val sendDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    @Volatile private var client: RemoteClient? = null
    @Volatile private var mirror: TextMirror? = null
    @Volatile private var pairing: PairingSession? = null
    private var identity: ClientIdentity? = null
    private var linkJob: Job? = null
    private var openJob: Job? = null
    private var voiceJob: Job? = null
    private var volumeJob: Job? = null
    private val holders = HashSet<String>()
    private val fetching = HashSet<String>()
    private val listLock = Any()

    /** Cuts a reconnect wait short: Wi-Fi came back, the app opened, or Turn on was pressed. */
    private val kicks = Channel<Unit>(Channel.CONFLATED)
    private val wifiNetworks = HashSet<Network>()
    @Volatile private var wifiUp = true

    /** Fingerprint of the last device that answered with a certificate that isn't this box's. */
    @Volatile private var stranger: String? = null

    init {
        refreshMeta(_state.value.apps + _state.value.favourites)
        watchWifi()
        Trace.i("started: ${if (storage.paired) "paired" else "not paired"}, box at ${storage.host}, Wi-Fi ${if (wifiUp) "up" else "down"}")
        scope.launch { _state.map(Trace::state).distinctUntilChanged().collect { Trace.i("state $it") } }
    }

    @Synchronized
    fun acquire(holder: String) {
        holders += holder
        Trace.i("connection wanted by $holder (now ${holders.joinToString()})")
        ensureRunning()
        kicks.trySend(Unit)
    }

    /** Reconnects now instead of waiting out the backoff. */
    fun reconnectNow() {
        ensureRunning()
        kicks.trySend(Unit)
    }

    /** One button that never does the wrong thing: turns the TV off only when the box says it's on. */
    fun power() {
        val now = _state.value
        if (now.link == Link.CONNECTED && now.power == true) key(Keys.POWER) else turnOn()
    }

    /**
     * Wakes the box: Wake-on-LAN and an immediate reconnect if it's unreachable (it normally stays connected in
     * standby), then POWER if the box reported standby, or WAKEUP if it said nothing about power.
     */
    fun turnOn() {
        if (!storage.paired) return notify("Pair My Tv with the box first. Only a paired phone can turn the box on.")
        runOpening(POWER_TARGET) {
            if (client == null) {
                Trace.i("turn on: not connected, sending Wake-on-LAN and reconnecting")
                if (!wifiUp) notify(NO_WIFI)
                runCatching { WakeOnLan.send(ShoqBox.MAC_ADDRESSES, storage.host) }
                reconnectNow()
                if (withTimeoutOrNull(25_000) { _state.first { it.link == Link.CONNECTED } } == null) {
                    notify("Can't reach the TV box. Check it's plugged in and this phone is on the home Wi-Fi.")
                    return@runOpening
                }
            }
            val remote = client ?: return@runOpening
            val reported = withTimeoutOrNull(2_000) { _state.first { it.power != null } }?.power
            Trace.i("turn on: box reports power=${reported ?: "unknown"}")
            if (reported == true) return@runOpening
            // Tested on this box: it ignores WAKEUP, and POWER wakes it from standby in about a second. POWER is a
            // toggle, so it's sent only when the box itself said it's in standby; WAKEUP covers an unknown state.
            remote.sendKey(if (reported == false) Keys.POWER else Keys.WAKEUP)
            if (awaitPowerOn(5_000)) return@runOpening Trace.i("turn on: box confirmed it's on")
            notify("The box didn't say it woke up. If the TV is still dark, switch the TV to the Shoq box's input.")
        }
    }

    @Synchronized
    fun release(holder: String) {
        holders -= holder
        Trace.i("connection released by $holder (left: ${holders.joinToString().ifEmpty { "none" }})")
        if (holders.isEmpty()) stopLink()
    }

    fun notify(text: String) {
        Trace.i("message: $text")
        messageChannel.trySend(text)
    }

    fun key(code: Int, direction: Int = Direction.SHORT) {
        val name = Trace.key(code) + if (direction == Direction.SHORT) "" else if (direction == Direction.START_LONG) " (hold)" else " (release)"
        if (client == null) Trace.w("$name not sent: not connected") else Trace.i(name)
        withClient { it.sendKey(code, direction) }
    }

    /** What the phone's text field holds; the TV box is brought to it, so a burst of keystrokes costs one sync. */
    @Volatile private var desiredText = ""
    private val _onTv = MutableStateFlow("")

    /** The text this app has typed into the TV's text box, as confirmed by the sends that completed. */
    val onTv: StateFlow<String> = _onTv.asStateFlow()

    private val imeChannel = Channel<Unit>(Channel.CONFLATED)

    /** Fires when the TV asks for a keyboard, so the typing panel can open itself. */
    val imeRequests: Flow<Unit> = imeChannel.receiveAsFlow()

    /** The text typed so far into the TV box, to prefill the typing panel. */
    val typedText: String get() = desiredText

    /** Mirrors the phone's text field into the TV's focused text box as it changes. */
    fun typeLive(value: String) {
        desiredText = value
        syncTyped()
    }

    /** Deletes only what the phone typed; an empty TV box would treat extra Backspace as Back. */
    fun clearTyped() = typeLive("")

    /** Sends Enter after the text; the reset runs in the send queue so keystrokes still queued can't erase it first. */
    fun submitTyped() {
        if (client == null) return notify(NOT_CONNECTED)
        val sent = desiredText
        Trace.i("typing: Enter after ${sent.length} characters")
        withClient {
            mirror?.update(sent)
            it.sendKey(Keys.ENTER)
            finishTyping(sent)
        }
    }

    /** Starts the next text from an empty field without touching what the TV box holds. */
    fun forgetTyped() {
        if (client == null) {
            desiredText = ""
            forgetMirror()
        } else {
            withClient { finishTyping(desiredText) }
        }
    }

    /** YouTube draws its own keyboard but takes its search address, which shows results with no navigating. */
    fun searchYouTube(query: String) {
        if (client == null) return notify(NOT_CONNECTED)
        Trace.i("YouTube search (${query.trim().length} characters)")
        withClient {
            it.launchLink(YOUTUBE_SEARCH + URLEncoder.encode(query.trim(), "UTF-8"))
            finishTyping(query)
        }
    }

    private fun finishTyping(sent: String) {
        forgetMirror()
        if (desiredText == sent) desiredText = ""
    }

    private fun syncTyped() = withClient {
        mirror?.let { m ->
            m.update(desiredText)
            _onTv.value = m.text
            Trace.i("typing: TV box holds ${m.text.length} characters")
        }
    }

    private fun forgetMirror() {
        mirror?.forget()
        _onTv.value = ""
    }

    fun openApp(packageName: String) {
        if (packageName == SETTINGS_PACKAGE) return openTvSettings()
        val remote = client ?: return notify(NOT_CONNECTED)
        // Apps installed from outside the Play Store can't be opened through it; the box's Assistant opens them by
        // name. Generated speech proved unreliable (2026-11-16), so the owner says it.
        if (storage.noPlayPage(packageName)) {
            val name = nameOf(packageName)
            return notify("$name isn't on the Play Store. Tap the mic and say \"Open $name\".")
        }
        Trace.i("open app $packageName")
        runOpening(packageName) {
            if (!launch(remote, packageName)) notify("${nameOf(packageName)} didn't open. Check the TV.")
        }
    }

    /** The PTCL home screen keeps its Settings gear one step up from where Home lands. */
    fun openTvSettings() {
        val remote = client ?: return notify(NOT_CONNECTED)
        Trace.i("open TV settings")
        runOpening(SETTINGS_PACKAGE) {
            remote.sendKey(Keys.HOME)
            delay(1_500)
            remote.sendKey(Keys.DPAD_UP)
            delay(600)
            remote.sendKey(Keys.DPAD_CENTER)
            if (awaitApp(6_000) { it == SETTINGS_PACKAGE } == null) {
                notify("Settings didn't open. On the TV's home screen, press Up, then OK.")
            }
        }
    }

    /** Live TV is ZTE's player, reached with OK from the PTCL home screen. */
    fun openLiveTv() {
        val remote = client ?: return notify(NOT_CONNECTED)
        if (_state.value.currentApp == LIVE_TV_PACKAGE) return
        Trace.i("open live TV")
        runOpening(LIVE_TV_PACKAGE) {
            remote.sendKey(Keys.HOME)
            delay(1_500)
            remote.sendKey(Keys.DPAD_CENTER)
            if (awaitApp(6_000) { it == LIVE_TV_PACKAGE } == null) notify("Live TV didn't open. Press OK on the TV's home screen.")
        }
    }

    /**
     * The box can't jump to a volume: this presses up or down one step at a time until it reports [target], and
     * stops if it stops answering (for example while muted, where Up may only unmute).
     */
    fun setVolume(target: Int) {
        val remote = client ?: return notify(NOT_CONNECTED)
        volumeJob?.cancel()
        Trace.i("volume to $target")
        volumeJob = scope.launch {
            repeat(MAX_VOLUME_STEPS) {
                val before = _state.value.volume?.level ?: return@launch
                if (before == target) return@launch
                remote.sendKey(if (target > before) Keys.VOLUME_UP else Keys.VOLUME_DOWN)
                withTimeoutOrNull(900) { _state.first { it.volume?.level != before } } ?: return@launch
            }
        }
    }

    /** Live TV plays full screen in ZTE's player, reached with OK from the PTCL home screen; there it takes channel numbers. */
    fun watchChannel(number: Int) {
        val remote = client ?: return notify(NOT_CONNECTED)
        Trace.i("watch channel $number")
        runOpening("$CHANNEL_PREFIX$number") {
            if (_state.value.currentApp != LIVE_TV_PACKAGE) {
                remote.sendKey(Keys.HOME)
                delay(1_500)
                remote.sendKey(Keys.DPAD_CENTER)
                if (awaitApp(6_000) { it == LIVE_TV_PACKAGE } == null) {
                    notify("Live TV didn't open. Press OK on the TV's home screen, then try again.")
                    return@runOpening
                }
                delay(1_200)
            }
            for (digit in number.toString()) {
                remote.sendKey(Keys.digit(digit - '0'))
                delay(250)
            }
            delay(1_500)
        }
    }

    fun toggleVoiceSearch() {
        if (voiceJob?.isActive == true) {
            voiceJob?.cancel()
            return
        }
        val remote = client ?: return notify(NOT_CONNECTED)
        Trace.i("voice search started")
        voiceJob = scope.launch { streamVoice(remote) }
    }

    fun toggleFavourite(packageName: String) = synchronized(listLock) {
        val favourites = storage.favourites
        val updated = if (packageName in favourites) favourites - packageName else favourites + packageName
        storage.favourites = updated
        _state.update { it.copy(favourites = updated) }
    }

    fun removeApp(packageName: String) = synchronized(listLock) {
        val apps = storage.apps - packageName
        val favourites = storage.favourites - packageName
        storage.apps = apps
        storage.favourites = favourites
        _state.update { it.copy(apps = apps, favourites = favourites) }
    }

    fun saveChannel(channel: TvChannel, replacing: Int? = null) = synchronized(listLock) {
        val channels = (storage.channels.filter { it.number != channel.number && it.number != replacing } + channel)
            .sortedBy { it.number }
        var pinned = storage.pinnedChannels
        if (replacing != null && replacing != channel.number) pinned = pinned.map { if (it == replacing) channel.number else it }
        storage.channels = channels
        storage.pinnedChannels = pinned
        _state.update { it.copy(channels = channels, pinnedChannels = pinned) }
    }

    fun deleteChannel(number: Int) = synchronized(listLock) {
        val channels = storage.channels.filter { it.number != number }
        val pinned = storage.pinnedChannels - number
        storage.channels = channels
        storage.pinnedChannels = pinned
        _state.update { it.copy(channels = channels, pinnedChannels = pinned) }
    }

    fun togglePinChannel(number: Int) = synchronized(listLock) {
        val pinned = storage.pinnedChannels
        val updated = if (number in pinned) pinned - number else pinned + number
        storage.pinnedChannels = updated
        _state.update { it.copy(pinnedChannels = updated) }
    }

    /** Moves a pinned channel one place up (negative) or down (positive) in the user's order. */
    fun movePinnedChannel(number: Int, by: Int) = synchronized(listLock) {
        val pinned = storage.pinnedChannels.toMutableList()
        val from = pinned.indexOf(number)
        val to = (from + by).coerceIn(0, pinned.lastIndex)
        if (from < 0 || from == to) return@synchronized
        pinned.add(to, pinned.removeAt(from))
        storage.pinnedChannels = pinned
        _state.update { it.copy(pinnedChannels = pinned) }
    }

    fun setNotificationControls(on: Boolean) {
        storage.notificationControls = on
        _state.update { it.copy(notificationControls = on) }
        if (on) TvLinkService.start(app) else TvLinkService.stop(app)
    }

    fun forgetPairing() {
        Trace.i("pairing forgotten")
        stopLink()
        scope.launch {
            resetIdentity()
            storage.forgetPairing()
            _state.update {
                it.copy(link = Link.NOT_PAIRED, device = null, power = null, currentApp = null, pairing = PairingStep.IDLE, pairingMessage = null)
            }
        }
    }

    fun startPairing() {
        if (_state.value.pairing == PairingStep.STARTING) return
        stopLink()
        _state.update { it.copy(pairing = PairingStep.STARTING, pairingMessage = null) }
        scope.launch {
            closePairing()
            try {
                stranger = null
                val host = (if (isShoqBox(storage.host)) storage.host else searchForBox(storage.host))
                    ?: throw stranger?.let(::WrongDeviceException) ?: IOException(NOT_FOUND)
                storage.host = host
                Trace.i("pairing: asking the box at $host for a code")
                pairing = PairingSession.start(host, identity(), "My Tv (${Build.MODEL})")
                Trace.i("pairing: the box is showing a code")
                _state.update { it.copy(pairing = PairingStep.AWAITING_CODE) }
            } catch (e: Exception) {
                Trace.w("pairing didn't start", e)
                _state.update { it.copy(pairing = PairingStep.IDLE, pairingMessage = describe(e)) }
            }
        }
    }

    fun submitCode(code: String) {
        val session = pairing ?: return
        _state.update { it.copy(pairing = PairingStep.CHECKING, pairingMessage = null) }
        scope.launch {
            try {
                session.finish(code)
                Trace.i("pairing: the box accepted the code, paired")
                closePairing()
                storage.paired = true
                _state.update { it.copy(link = Link.CONNECTING, pairing = PairingStep.IDLE) }
                ensureRunning()
            } catch (e: BadCodeException) {
                Trace.w(if (e.sent) "pairing: the box rejected the code" else "pairing: the code doesn't match its check digits")
                if (e.sent) {
                    closePairing()
                    _state.update {
                        it.copy(pairing = PairingStep.IDLE, pairingMessage = "The box rejected that code. Start again for a new one.")
                    }
                } else {
                    _state.update {
                        it.copy(pairing = PairingStep.AWAITING_CODE, pairingMessage = "That doesn't match the code on the TV. Check it and try again.")
                    }
                }
            } catch (e: Exception) {
                Trace.w("pairing failed", e)
                closePairing()
                _state.update { it.copy(pairing = PairingStep.IDLE, pairingMessage = describe(e)) }
            }
        }
    }

    fun cancelPairing() {
        _state.update { it.copy(pairing = PairingStep.IDLE, pairingMessage = null) }
        scope.launch {
            closePairing()
            ensureRunning()
        }
    }

    private fun initialState(): UiState {
        val apps = storage.apps
        val favourites = storage.favourites
        val meta = (apps + favourites).distinct().mapNotNull { pkg -> catalog.cached(pkg)?.let { pkg to it } }.toMap()
        return UiState(
            link = if (storage.paired) Link.CONNECTING else Link.NOT_PAIRED,
            apps = apps,
            favourites = favourites,
            meta = meta,
            channels = storage.channels,
            pinnedChannels = storage.pinnedChannels,
            notificationControls = storage.notificationControls,
        )
    }

    @Synchronized
    private fun ensureRunning() {
        if (holders.isNotEmpty() && storage.paired && pairing == null && linkJob?.isActive != true) {
            linkJob = scope.launch { keepConnected() }
        }
    }

    @Synchronized
    private fun stopLink() {
        linkJob?.cancel()
        linkJob = null
    }

    private fun withClient(action: (RemoteClient) -> Unit) {
        val current = client ?: return
        scope.launch(sendDispatcher) { runCatching { action(current) }.onFailure { Trace.w("send failed", it) } }
    }

    private fun runOpening(target: String, block: suspend () -> Unit) {
        openJob?.cancel()
        openJob = scope.launch {
            _state.update { it.copy(opening = target) }
            try {
                block()
            } catch (e: IOException) {
                Trace.w("$target stopped", e)
                notify("Lost the connection to the box. Try again in a moment.")
            } finally {
                _state.update { if (it.opening == target) it.copy(opening = null) else it }
            }
        }
    }

    /** Real apps only: not the home screen, Live TV, Settings, the Play Store's own pages or the Assistant. */
    fun canSave(packageName: String?): Boolean = packageName != null && packageName !in NOT_APPS

    /** Keeps the app on screen in Apps under the owner's name, optionally among favourites. */
    fun saveApp(packageName: String, name: String, favourite: Boolean) {
        if (!canSave(packageName)) return
        synchronized(listLock) {
            val apps = storage.apps
            if (packageName !in apps) {
                val updated = apps + packageName
                storage.apps = updated
                _state.update { it.copy(apps = updated) }
            }
            if (favourite != packageName in storage.favourites) toggleFavourite(packageName)
        }
        renameApp(packageName, name)
        Trace.i("saved $packageName as \"${name.trim()}\"${if (favourite) " (favourite)" else ""}")
        notify("${name.trim()} is in Apps.")
    }

    /** A name the owner chose for an app tile; it's also the name the mic hint suggests saying. */
    fun renameApp(packageName: String, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        storage.saveLabel(packageName, clean)
        _state.update { it.copy(meta = it.meta + (packageName to AppMeta(clean, it.meta[packageName]?.iconPath))) }
    }

    /** Uses the app's web address when it has one; otherwise opens its Play Store page and presses its Open button. */
    private suspend fun launch(remote: RemoteClient, packageName: String): Boolean {
        if (_state.value.currentApp == packageName) return true
        DIRECT_LINKS[packageName]?.let { link ->
            val before = _state.value.currentApp
            remote.launchLink(link)
            when (awaitApp(6_000) { it != before }) {
                packageName -> return true
                null -> Unit
                else -> {
                    remote.sendKey(Keys.BACK)
                    delay(800)
                }
            }
        }
        if (_state.value.currentApp == PLAY_STORE) {
            remote.sendKey(Keys.HOME)
            delay(1_500)
        }
        remote.launchLink("https://play.google.com/store/apps/details?id=$packageName")
        if (awaitApp(8_000) { it == PLAY_STORE } == null) return false
        delay(1_500)
        remote.sendKey(Keys.DPAD_CENTER)
        return awaitApp(12_000) { it == packageName } != null
    }

    /** Streams the phone microphone into the box's Google Assistant: 8 kHz 16-bit mono, about a second per piece. */
    @SuppressLint("MissingPermission")
    private suspend fun streamVoice(remote: RemoteClient) {
        _state.update { it.copy(listening = true) }
        var session: Int? = null
        var recorder: AudioRecord? = null
        try {
            val voiceSession = remote.startVoice()
            session = voiceSession
            val minBuffer = AudioRecord.getMinBufferSize(VOICE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val mic = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                VOICE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, VOICE_CHUNK * 2),
            )
            recorder = mic
            if (mic.state != AudioRecord.STATE_INITIALIZED) throw IOException("The phone's microphone is busy.")
            mic.startRecording()
            val chunk = ByteArray(VOICE_CHUNK)
            val deadline = System.currentTimeMillis() + VOICE_MAX_MS
            var assistantShown = false
            while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
                var filled = 0
                while (filled < chunk.size && currentCoroutineContext().isActive) {
                    val read = mic.read(chunk, filled, minOf(2_048, chunk.size - filled))
                    if (read <= 0) throw IOException("The phone's microphone stopped.")
                    filled += read
                }
                if (filled > 0) remote.sendVoice(voiceSession, chunk.copyOf(filled))
                if (_state.value.currentApp == ASSISTANT) assistantShown = true else if (assistantShown) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notify(e.message ?: "Voice search didn't start.")
        } finally {
            recorder?.let { mic ->
                runCatching { mic.stop() }
                mic.release()
            }
            session?.let { id -> runCatching { remote.endVoice(id) } }
            _state.update { it.copy(listening = false) }
        }
    }

    private suspend fun awaitPowerOn(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { _state.first { it.power == true } } != null

    private suspend fun awaitApp(timeoutMs: Long, predicate: (String) -> Boolean): String? =
        withTimeoutOrNull(timeoutMs) { _state.first { state -> state.currentApp?.let(predicate) == true }.currentApp }

    private fun nameOf(packageName: String): String = _state.value.meta[packageName]?.label ?: fallbackLabel(packageName)

    private fun onAppSeen(packageName: String) {
        val previous = _state.value.currentApp
        _state.update { it.copy(currentApp = packageName) }
        // A different app means a different text box: what was typed belongs to the old one.
        if (previous != null && previous != packageName) {
            desiredText = ""
            forgetMirror()
        }
        if (packageName in NOT_APPS) return
        val added = synchronized(listLock) {
            val apps = storage.apps
            if (packageName in apps) {
                false
            } else {
                val updated = apps + packageName
                storage.apps = updated
                _state.update { it.copy(apps = updated) }
                true
            }
        }
        if (added) refreshMeta(listOf(packageName))
    }

    private fun refreshMeta(packages: Collection<String>) {
        for (pkg in packages.distinct()) {
            if (pkg == SETTINGS_PACKAGE || _state.value.meta.containsKey(pkg)) continue
            val claimed = synchronized(fetching) { fetching.add(pkg) }
            if (!claimed) continue
            scope.launch {
                catalog.fetch(pkg)?.let { meta -> _state.update { it.copy(meta = it.meta + (pkg to meta)) } }
                synchronized(fetching) { fetching.remove(pkg) }
            }
        }
    }

    /**
     * Holds the connection for as long as a screen or the notification wants it. Retries back off to 30 s, pause
     * while the phone is off Wi-Fi, and restart at once on a kick. Retries keep the OFFLINE status so it doesn't flicker.
     */
    private suspend fun keepConnected() {
        var backoffMs = 1_000L
        while (true) {
            if (!wifiUp) {
                _state.update { it.copy(link = Link.OFFLINE, detail = NO_WIFI, power = null) }
                val kicked = withTimeoutOrNull(WIFI_RECHECK_MS) { kicks.receive() } != null
                if (!kicked && !wifiUp) continue
            }
            val host = storage.host
            _state.update { if (it.link == Link.OFFLINE && it.detail != NO_WIFI) it else it.copy(link = Link.CONNECTING, detail = "") }
            var reachedBox = false
            Trace.i("connecting to $host")
            val error = runSession(host) { reachedBox = true }
            Trace.w("connection to $host ended" + if (reachedBox) "" else " before it was ready", error)
            when {
                error is NotPairedException -> {
                    storage.paired = false
                    _state.update { it.copy(link = Link.NOT_PAIRED, detail = "", power = null) }
                    return
                }
                error is WrongDeviceException || isUnreachable(error) -> {
                    stranger = null
                    Trace.i("searching the network for the box")
                    val moved = searchForBox(host)
                    if (moved != null) {
                        Trace.i("found the box at $moved")
                        storage.host = moved
                        continue
                    }
                    val why = stranger?.let { describe(WrongDeviceException(it)) } ?: NOT_FOUND
                    _state.update { it.copy(link = Link.OFFLINE, detail = why, power = null) }
                }
                else -> _state.update { it.copy(link = Link.OFFLINE, detail = "Connection lost. Reconnecting.", power = null) }
            }
            if (reachedBox) backoffMs = 1_000L
            val kicked = withTimeoutOrNull(backoffMs) { kicks.receive() } != null
            if (kicked) Trace.i("retrying now")
            backoffMs = if (kicked) 1_000L else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /** Tracks Wi-Fi so retries pause away from home, and drops a dead connection the moment Wi-Fi goes. */
    private fun watchWifi() {
        val connectivity = app.getSystemService(ConnectivityManager::class.java) ?: return
        @Suppress("DEPRECATION")
        val current = connectivity.allNetworks.filter {
            connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        synchronized(wifiNetworks) {
            wifiNetworks += current
            wifiUp = wifiNetworks.isNotEmpty()
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivity.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val wasUp = synchronized(wifiNetworks) {
                        val before = wifiUp
                        wifiNetworks += network
                        wifiUp = true
                        before
                    }
                    if (!wasUp) Trace.i("Wi-Fi up")
                    if (!wasUp || client == null) kicks.trySend(Unit)
                }

                override fun onLost(network: Network) {
                    val nowUp = synchronized(wifiNetworks) {
                        wifiNetworks -= network
                        wifiUp = wifiNetworks.isNotEmpty()
                        wifiUp
                    }
                    if (!nowUp) Trace.i("Wi-Fi down")
                    if (!nowUp) client?.close()
                }
            })
        }
    }

    private suspend fun runSession(host: String, markReached: () -> Unit): Throwable? {
        val closed = CompletableDeferred<Throwable?>()
        val session = RemoteClient(host, identity(), object : RemoteListener {
            override fun onReady(info: DeviceInfo) {
                markReached()
                _state.update { it.copy(link = Link.CONNECTED, device = info, detail = "") }
                refreshMeta(_state.value.apps + _state.value.favourites)
                if (desiredText != mirror?.text) syncTyped()
            }
            override fun onPower(on: Boolean) = _state.update { it.copy(power = on) }
            override fun onVolume(level: Int, max: Int, muted: Boolean) =
                _state.update { it.copy(volume = Volume(level, max, muted)) }
            override fun onCurrentApp(packageName: String) = onAppSeen(packageName)
            override fun onImeShow() {
                Trace.i("the TV asked for a keyboard")
                imeChannel.trySend(Unit)
            }
            override fun onClosed(error: Throwable?) {
                closed.complete(error)
            }
        })
        try {
            session.connect()
        } catch (e: Exception) {
            return e
        }
        mirror = TextMirror(session, mirror?.text.orEmpty())
        client = session
        return try {
            closed.await()
        } finally {
            // The mirror outlives the connection: the next one carries on from what the TV box already holds.
            if (client === session) client = null
            session.close()
        }
    }

    private suspend fun searchForBox(exclude: String): String? {
        if (exclude != ShoqBox.DEFAULT_HOST && isShoqBox(ShoqBox.DEFAULT_HOST)) return ShoqBox.DEFAULT_HOST
        return finder.discover().firstOrNull { it != exclude && isShoqBox(it) }
    }

    /** True when the host answers as this box; a bare connection shows nothing on the TV. */
    private fun isShoqBox(host: String): Boolean = try {
        openTls(host, ShoqBox.PAIRING_PORT, identity(), ShoqBox.trust(), 5_000).close()
        true
    } catch (e: IOException) {
        e.causeOf<WrongDeviceException>()?.let { stranger = it.fingerprint }
        false
    }

    @Synchronized
    private fun identity(): ClientIdentity = identity ?: storage.identity(Build.MODEL).also { identity = it }

    @Synchronized
    private fun resetIdentity() {
        identity = null
    }

    private fun closePairing() {
        pairing?.close()
        pairing = null
    }

    private fun describe(e: Throwable): String = when {
        e.causeOf<WrongDeviceException>() != null -> "A different Shoq box answered (certificate " +
            "${e.causeOf<WrongDeviceException>()!!.fingerprint.take(12)}...). This copy of My Tv is built for another box."
        isUnreachable(e) -> NOT_FOUND
        else -> e.message ?: e::class.java.simpleName
    }

    private fun isUnreachable(e: Throwable?) =
        e is ConnectException || e is NoRouteToHostException || e is SocketTimeoutException
}
