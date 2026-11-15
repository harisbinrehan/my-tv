package com.shoqremote.app

import android.app.Application

/** Holds the single TvLink, so the screens and the notification share one connection to the box. */
class MyTvApp : Application() {
    lateinit var link: TvLink
        private set
    lateinit var updater: Updater
        private set
    val logos: ChannelLogos by lazy { ChannelLogos(this) }

    override fun onCreate() {
        super.onCreate()
        link = TvLink(this)
        updater = Updater(this, link::notify)
    }
}
