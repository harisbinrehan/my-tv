package com.shoqremote.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import com.shoqremote.atv.Keys
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the TV connection open and shows a permanent notification with the app on the TV, its icon and
 * live controls. A media session gives it the player card in the notification panel and on the lock screen.
 * While the TV is on, the session also takes the phone's volume buttons for the TV (Android routes them to a
 * playing remote session, the way it does for a Chromecast); when the TV is off they are the phone's again.
 */
class TvLinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var link: TvLink
    private lateinit var session: MediaSession
    private lateinit var notifications: NotificationManager
    private var shown: Card? = null
    private var idleStop: Job? = null
    private var tvVolume: VolumeProvider? = null

    private enum class Mode { CONTROLS, POWER, NONE }

    private data class Card(
        val title: String,
        val text: String,
        val mode: Mode,
        val muted: Boolean,
        val playing: Boolean,
        /** The phone's volume buttons go to the TV (only while it's on and its volume is known). */
        val volumeKeys: Boolean = false,
        /** [playing] is what the app on the TV reported over Cast, not a guess. */
        val known: Boolean = false,
        val positionMs: Long = PlaybackState.PLAYBACK_POSITION_UNKNOWN,
        val durationMs: Long = 0,
        /** SystemClock.elapsedRealtime() when [positionMs] was read. */
        val readAt: Long = 0,
    )

    private val callback = object : MediaSession.Callback() {
        override fun onPlay() = press(Keys.MEDIA_PLAY_PAUSE)
        override fun onPause() = press(Keys.MEDIA_PLAY_PAUSE)
        override fun onSkipToNext() = press(Keys.MEDIA_FAST_FORWARD)
        override fun onSkipToPrevious() = press(Keys.MEDIA_REWIND)
        override fun onCustomAction(action: String, extras: Bundle?) {
            when (action) {
                CUSTOM_MUTE -> press(Keys.VOLUME_MUTE)
                // Off while the box says it's on, otherwise the full turn-on (never a blind POWER toggle).
                CUSTOM_POWER -> link.power()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        link = (application as MyTvApp).link
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "TV controls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for the app on your TV"
                setShowBadge(false)
            },
        )
        session = MediaSession(this, "My Tv").apply {
            setCallback(callback)
            setSessionActivity(openAppIntent())
            isActive = true
        }
        val card = cardOf(link.state.value)
        if (!goForeground(build(card))) {
            stopSelf()
            return
        }
        shown = card
        link.acquire(HOLDER)
        scope.launch {
            link.state.collect {
                syncVolumeKeys(it)
                render(cardOf(it))
                Widgets.refresh(this@TvLinkService, it)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A widget can start this even with notification controls off; then it stops again after a quiet minute.
        if (!Storage(this).notificationControls) {
            idleStop?.cancel()
            idleStop = scope.launch {
                delay(IDLE_STOP_MS)
                stopSelf()
            }
        }
        when (intent?.action) {
            ACTION_KEY -> press(intent.getIntExtra(EXTRA_KEY, 0))
            ACTION_TURN_ON -> link.turnOn()
            ACTION_POWER -> link.power()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        link.release(HOLDER)
        session.release()
        super.onDestroy()
    }

    /** From the notification or a widget; a tap that beats the connection waits for it (5 s) instead of being lost. */
    private fun press(key: Int) {
        if (key == 0) return
        if (link.state.value.link == Link.CONNECTED) {
            link.key(key)
        } else {
            link.reconnectNow()
            scope.launch {
                withTimeoutOrNull(CONNECT_WAIT_MS) { link.state.first { it.link == Link.CONNECTED } }
                link.key(key)
            }
        }
        render(cardOf(link.state.value))
        Widgets.refresh(this, link.state.value)
    }

    private fun cardOf(state: UiState): Card {
        val muted = state.volume?.muted == true
        val playing = state.isPlaying()
        return when {
            state.link == Link.NOT_PAIRED -> Card("My Tv", "Open My Tv to pair it with your Shoq box", Mode.NONE, muted, playing)
            state.opening == POWER_TARGET -> Card("Turning on your TV...", "Waking the Shoq box", Mode.NONE, muted, playing)
            state.link == Link.CONNECTING -> Card("My Tv", "Connecting to your TV...", Mode.NONE, muted, playing)
            // Unreachable usually means the box is in deep standby, so offer Turn on (it sends Wake-on-LAN first).
            state.link == Link.OFFLINE && state.detail == NO_WIFI -> Card("My Tv", "Waiting for your home Wi-Fi", Mode.NONE, muted, playing)
            state.link == Link.OFFLINE -> Card("Can't reach your TV", "Tap Turn on to wake it", Mode.POWER, muted, playing)
            state.power == false -> Card("TV is off", "Tap Turn on to wake it", Mode.POWER, muted, playing)
            else -> {
                val app = state.currentApp
                val title = when (app) {
                    null, HOME_PACKAGE -> "Home screen"
                    else -> state.meta[app]?.label ?: fallbackLabel(app)
                }
                val volumeKeys = state.volumeButtons && state.volume != null
                // A video's title and channel, its play state and position, when the app reports them over Cast.
                val media = state.nowPlaying()
                val video = media?.title
                if (media != null && video != null) {
                    Card(
                        video,
                        listOfNotNull(title, media.subtitle).joinToString(" · "),
                        Mode.CONTROLS,
                        muted,
                        playing,
                        volumeKeys = volumeKeys,
                        known = media.playing != null,
                        positionMs = media.positionSec?.let { (it * 1000).toLong() } ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                        durationMs = media.durationSec?.takeIf { !media.live }?.let { (it * 1000).toLong() } ?: 0,
                        readAt = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - media.readAtMs),
                    )
                } else {
                    Card(title, "On your TV", Mode.CONTROLS, muted, playing, volumeKeys = volumeKeys)
                }
            }
        }
    }

    /**
     * Hands the phone's volume buttons to the TV while it's on: the session plays "remotely" with the TV's level, so
     * a press steps the TV's volume and the phone's volume panel shows the TV's level. Otherwise the session is
     * local again and the buttons are the phone's. Music playing on the phone still wins: Android adjusts that.
     */
    private fun syncVolumeKeys(state: UiState) {
        val volume = state.volume
        if (cardOf(state).volumeKeys.not() || volume == null) {
            if (tvVolume != null) {
                tvVolume = null
                session.setPlaybackToLocal(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                Trace.i("phone volume buttons: phone")
            }
            return
        }
        val max = volume.max.coerceAtLeast(1)
        val level = volume.level.coerceIn(0, max)
        val provider = tvVolume?.takeIf { it.maxVolume == max } ?: object : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, max, level) {
            override fun onAdjustVolume(direction: Int) {
                when (direction) {
                    AudioManager.ADJUST_RAISE -> link.key(Keys.VOLUME_UP)
                    AudioManager.ADJUST_LOWER -> link.key(Keys.VOLUME_DOWN)
                    AudioManager.ADJUST_TOGGLE_MUTE -> link.key(Keys.VOLUME_MUTE)
                }
            }

            override fun onSetVolumeTo(volume: Int) = link.setVolume(volume)
        }.also {
            tvVolume = it
            session.setPlaybackToRemote(it)
            Trace.i("phone volume buttons: TV")
        }
        if (provider.currentVolume != level) provider.currentVolume = level
    }

    private fun render(card: Card) {
        if (card == shown) return
        shown = card
        notifications.notify(NOTIFICATION_ID, build(card))
    }

    private fun build(card: Card): Notification {
        // No artwork: the player card keeps its plain dark background instead of a blown-up app icon.
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, card.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, card.text)
        if (card.durationMs > 0) metadata.putLong(MediaMetadata.METADATA_KEY_DURATION, card.durationMs)
        session.setMetadata(metadata.build())
        session.setPlaybackState(playbackState(card))

        val style = Notification.MediaStyle().setMediaSession(session.sessionToken)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(card.title)
            .setContentText(card.text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
        when (card.mode) {
            Mode.CONTROLS -> {
                builder.addAction(action(R.drawable.ic_n_rewind, "Rewind", Keys.MEDIA_REWIND))
                builder.addAction(action(if (card.playing) R.drawable.ic_n_pause else R.drawable.ic_n_play, "Play or pause", Keys.MEDIA_PLAY_PAUSE))
                builder.addAction(action(R.drawable.ic_n_ff, "Fast forward", Keys.MEDIA_FAST_FORWARD))
                builder.addAction(action(if (card.muted) R.drawable.ic_n_mute else R.drawable.ic_n_unmute, if (card.muted) "Unmute" else "Mute", Keys.VOLUME_MUTE))
                builder.addAction(powerAction("Turn TV off", ACTION_POWER, POWER_REQUEST))
                style.setShowActionsInCompactView(0, 1, 2)
            }
            Mode.POWER -> {
                builder.addAction(powerAction("Turn on", ACTION_TURN_ON, TURN_ON_REQUEST))
                style.setShowActionsInCompactView(0)
            }
            Mode.NONE -> Unit
        }
        return builder.setStyle(style).build()
    }

    /** Android 13+ draws the panel's player card from these actions, so they mirror the notification buttons. */
    private fun playbackState(card: Card): PlaybackState {
        val state = PlaybackState.Builder()
        when (card.mode) {
            Mode.CONTROLS -> {
                state.setActions(
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                // Android only sends volume buttons to a session that is playing, so without a real play state from the
                // TV it stays "playing" while it has them. A video the TV reports as paused shows as paused (the
                // volume buttons are then the phone's until it plays again).
                val playingNow = if (card.known) card.playing else card.playing || card.volumeKeys
                state.setState(
                    if (playingNow) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    card.positionMs,
                    if (playingNow) 1f else 0f,
                    if (card.readAt > 0) card.readAt else SystemClock.elapsedRealtime(),
                )
                state.addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        CUSTOM_MUTE,
                        if (card.muted) "Unmute" else "Mute",
                        if (card.muted) R.drawable.ic_n_mute else R.drawable.ic_n_unmute,
                    ).build(),
                )
                state.addCustomAction(PlaybackState.CustomAction.Builder(CUSTOM_POWER, "Turn TV off", R.drawable.ic_n_power).build())
            }
            Mode.POWER -> {
                state.setState(PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                state.addCustomAction(PlaybackState.CustomAction.Builder(CUSTOM_POWER, "Turn on", R.drawable.ic_n_power).build())
            }
            Mode.NONE -> state.setState(PlaybackState.STATE_NONE, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
        }
        return state.build()
    }

    private fun powerAction(title: String, action: String, request: Int): Notification.Action {
        val intent = Intent(this, TvLinkService::class.java).setAction(action)
        val pending = PendingIntent.getService(this, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_n_power), title, pending).build()
    }

    private fun action(icon: Int, title: String, key: Int): Notification.Action {
        val intent = Intent(this, TvLinkService::class.java).setAction(ACTION_KEY).putExtra(EXTRA_KEY, key)
        val pending = PendingIntent.getService(this, key, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pending).build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun goForeground(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: Exception) {
        false
    }

    companion object {
        private const val NOTIFICATION_ID = 7
        private const val CHANNEL_ID = "tv_controls"
        private const val HOLDER = "notification"
        const val ACTION_KEY = "com.shoqremote.app.KEY"
        private const val ACTION_TURN_ON = "com.shoqremote.app.TURN_ON"
        const val ACTION_POWER = "com.shoqremote.app.POWER"
        private const val TURN_ON_REQUEST = 10_000
        private const val IDLE_STOP_MS = 60_000L
        private const val CONNECT_WAIT_MS = 5_000L
        private const val POWER_REQUEST = 10_001
        const val EXTRA_KEY = "key"
        private const val CUSTOM_MUTE = "mute"
        private const val CUSTOM_POWER = "power"

        /** Starts the connection when the owner wants it kept (paired, notification controls on). */
        fun startIfWanted(context: Context) {
            val storage = Storage(context)
            if (storage.paired && storage.notificationControls) start(context)
        }

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, TvLinkService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TvLinkService::class.java))
        }
    }
}
