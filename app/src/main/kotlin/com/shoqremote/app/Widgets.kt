package com.shoqremote.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shoqremote.atv.Keys

/**
 * Home screen widgets: TV power (1x1), Now playing (4x1) and Remote (3x4). Their buttons go to TvLinkService, which
 * holds the connection to the box (and waits for it if a tap comes first); the service refreshes them as the TV
 * changes. They show the same state as the notification.
 */
object Widgets {
    private data class Look(val on: Boolean, val connected: Boolean, val title: String, val subtitle: String, val muted: Boolean, val playing: Boolean)

    @Volatile private var shown: Look? = null

    /** Redraws every placed widget, only when something they show has changed. */
    fun refresh(context: Context, state: UiState, playing: Boolean = state.isPlaying(), force: Boolean = false) {
        val look = lookOf(state, playing)
        if (!force && look == shown) return
        shown = look
        val manager = AppWidgetManager.getInstance(context) ?: return
        update(context, manager, PowerWidget::class.java) { power(context, look) }
        update(context, manager, NowWidget::class.java) { now(context, look) }
        update(context, manager, RemoteWidget::class.java) { remote(context, look) }
    }

    /** Widgets the owner can add from the menu, with the names the home screen shows. */
    val choices = listOf(
        "TV power (small)" to PowerWidget::class.java,
        "Now playing (wide)" to NowWidget::class.java,
        "Remote (large)" to RemoteWidget::class.java,
    )

    /** Asks the home screen to add [provider]; Android shows its own Add dialog. False when the launcher can't. */
    fun requestPin(context: Context, provider: Class<*>): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, provider), null, null)
    }

    private fun lookOf(state: UiState, playing: Boolean): Look {
        val connected = state.link == Link.CONNECTED
        val on = connected && state.power == true
        val app = state.currentApp
        val title = when {
            state.link == Link.NOT_PAIRED -> "My Tv"
            !connected -> "My Tv"
            !on -> "TV is off"
            app == null || app == HOME_PACKAGE -> "Home screen"
            else -> state.meta[app]?.label ?: fallbackLabel(app)
        }
        val subtitle = when {
            state.link == Link.NOT_PAIRED -> "Open My Tv to pair"
            !connected -> "Connecting to your TV"
            !on -> "Tap power to turn it on"
            else -> "On your TV"
        }
        // A video's title and channel when the app on the TV reports them over Cast.
        val media = state.nowPlaying()?.takeIf { on && it.title != null }
        if (media != null) {
            return Look(on, connected, media.title.orEmpty(), listOfNotNull(title, media.subtitle).joinToString(" · "), state.volume?.muted == true, playing)
        }
        return Look(on, connected, title, subtitle, state.volume?.muted == true, playing)
    }

    private fun update(context: Context, manager: AppWidgetManager, provider: Class<*>, build: () -> RemoteViews) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isNotEmpty()) manager.updateAppWidget(ids, build())
    }

    private fun power(context: Context, look: Look) = RemoteViews(context.packageName, R.layout.widget_power).apply {
        powerButton(context, look)
    }

    private fun now(context: Context, look: Look) = RemoteViews(context.packageName, R.layout.widget_now).apply {
        setTextViewText(R.id.w_title, look.title)
        setTextViewText(R.id.w_subtitle, look.subtitle)
        setOnClickPendingIntent(R.id.w_open, openApp(context))
        mediaButtons(context, look)
        powerButton(context, look)
    }

    private fun remote(context: Context, look: Look) = RemoteViews(context.packageName, R.layout.widget_remote).apply {
        for ((view, key) in REMOTE_KEYS) setOnClickPendingIntent(view, keyIntent(context, key))
        mediaButtons(context, look)
        powerButton(context, look)
    }

    /** The icon shows the state: crossed speaker while muted, pause while playing. */
    private fun RemoteViews.mediaButtons(context: Context, look: Look) {
        setImageViewResource(R.id.w_mute, if (look.muted) R.drawable.ic_n_mute else R.drawable.ic_n_unmute)
        setContentDescription(R.id.w_mute, if (look.muted) "Unmute" else "Mute")
        setOnClickPendingIntent(R.id.w_mute, keyIntent(context, Keys.VOLUME_MUTE))
        setImageViewResource(R.id.w_play, if (look.playing) R.drawable.ic_n_pause else R.drawable.ic_n_play)
        setOnClickPendingIntent(R.id.w_play, keyIntent(context, Keys.MEDIA_PLAY_PAUSE))
    }

    /** Red to turn off while the box says it's on, green to turn on otherwise; it never sends a blind toggle. */
    private fun RemoteViews.powerButton(context: Context, look: Look) {
        setInt(R.id.w_power, "setBackgroundResource", if (look.on) R.drawable.bg_widget_power_on else R.drawable.bg_widget_power_off)
        setInt(R.id.w_power, "setColorFilter", if (look.on) 0xFFFF5B57.toInt() else 0xFF34D27B.toInt())
        setContentDescription(R.id.w_power, if (look.on) "Turn TV off" else "Turn TV on")
        setOnClickPendingIntent(R.id.w_power, serviceIntent(context, TvLinkService.ACTION_POWER, POWER_REQUEST))
    }

    private fun keyIntent(context: Context, key: Int): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            KEY_REQUEST + key,
            Intent(context, TvLinkService::class.java).setAction(TvLinkService.ACTION_KEY).putExtra(TvLinkService.EXTRA_KEY, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun serviceIntent(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            request,
            Intent(context, TvLinkService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            OPEN_REQUEST,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private val REMOTE_KEYS = listOf(
        R.id.w_back to Keys.BACK, R.id.w_up to Keys.DPAD_UP, R.id.w_home to Keys.HOME,
        R.id.w_left to Keys.DPAD_LEFT, R.id.w_ok to Keys.DPAD_CENTER, R.id.w_right to Keys.DPAD_RIGHT,
        R.id.w_vol_down to Keys.VOLUME_DOWN, R.id.w_down to Keys.DPAD_DOWN, R.id.w_vol_up to Keys.VOLUME_UP,
    )

    private const val KEY_REQUEST = 20_000
    private const val POWER_REQUEST = 21_000
    private const val OPEN_REQUEST = 21_001
}

/** Drawn when placed or resized; after that TvLinkService keeps them current. */
abstract class TvWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.refresh(context, (context.applicationContext as MyTvApp).link.state.value, force = true)
        TvLinkService.startIfWanted(context)
    }
}

class PowerWidget : TvWidget()

class NowWidget : TvWidget()

class RemoteWidget : TvWidget()
