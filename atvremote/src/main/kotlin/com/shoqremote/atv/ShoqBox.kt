package com.shoqremote.atv

/** The one box this remote talks to, identified by the SHA-256 of its remote-service TLS certificate. */
object ShoqBox {
    const val CERT_SHA256 = "e0b20037ad14ad6cd02cd70fae8111e89abc15e2802b874e94a9e50ca73ab202"

    /** The MAC in the certificate's "atvremote/<MAC>" name; it survives the box making itself a new certificate. */
    const val BOX_ID = "54:AE:BC:9A:69:82"

    /** Wake-on-LAN targets: the wired MAC the router sees for 192.168.1.5, and the Wi-Fi MAC from the certificate. */
    val MAC_ADDRESSES = listOf("54:AE:BC:9A:69:81", "54:AE:BC:9A:69:82")

    const val DEFAULT_HOST = "192.168.1.5"
    const val NAME = "PTCL Shoq TV"
    const val REMOTE_PORT = 6466
    const val PAIRING_PORT = 6467
    const val SERVICE_TYPE = "_androidtvremote2._tcp"

    fun trust() = PinnedTrustManager(CERT_SHA256, BOX_ID)
}

object Direction {
    const val START_LONG = 1
    const val END_LONG = 2
    const val SHORT = 3
}

/** Android key codes, which the remote protocol sends as they are. */
object Keys {
    const val HOME = 3
    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val ENTER = 66
    const val DEL = 67
    const val MENU = 82
    const val SEARCH = 84
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val VOLUME_MUTE = 164
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val SETTINGS = 176

    /** Only ever wakes: unlike POWER it can't put a running box to sleep. */
    const val WAKEUP = 224
    const val SLEEP = 223
    private const val DIGIT_0 = 7

    fun digit(n: Int): Int = DIGIT_0 + n

    val byName: Map<String, Int> = mapOf(
        "HOME" to HOME, "BACK" to BACK, "UP" to DPAD_UP, "DOWN" to DPAD_DOWN, "LEFT" to DPAD_LEFT,
        "RIGHT" to DPAD_RIGHT, "OK" to DPAD_CENTER, "VOLUME_UP" to VOLUME_UP, "VOLUME_DOWN" to VOLUME_DOWN,
        "MUTE" to VOLUME_MUTE, "POWER" to POWER, "MENU" to MENU, "PLAY_PAUSE" to MEDIA_PLAY_PAUSE,
        "WAKEUP" to WAKEUP, "SLEEP" to SLEEP,
    )
}
