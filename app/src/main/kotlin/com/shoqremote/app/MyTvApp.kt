package com.shoqremote.app

import android.app.Application

/** Holds the single TvLink, so the screens and the notification share one connection to the box. */
class MyTvApp : Application() {
    lateinit var link: TvLink
        private set

    override fun onCreate() {
        super.onCreate()
        link = TvLink(this)
    }
}
