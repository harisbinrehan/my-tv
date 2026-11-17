package com.shoqremote.atv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CastMediaTest {
    // Shaped like what the owner's box sent on 2026-11-17 with YouTube playing (title shortened).
    private val receiver = """{"requestId":1,"status":{"applications":[{"appId":"2C6A6E3D","appType":"ANDROID_TV",
        "displayName":"YouTube","isIdleScreen":false,"namespaces":[{"name":"urn:x-cast:com.google.cast.media"}],
        "sessionId":"389ad5b0","statusText":"Youtube","transportId":"389ad5b0","universalAppId":"233637DE"}],
        "isActiveInput":true,"isStandBy":false,"volume":{"level":0.0666,"muted":false}},"type":"RECEIVER_STATUS"}"""
    private val media = """{"requestId":2,"type":"MEDIA_STATUS","status":[{"mediaSessionId":30,"playerState":"PLAYING",
        "playbackRate":1,"currentTime":61.418,"media":{"contentId":"","streamType":"BUFFERED","metadata":{"metadataType":0,
        "title":"Alfaaz 💜 | Hamza Malik × Zain","artist":"melancholy"},"duration":185.181}}]}"""

    @Test
    fun readsTheRunningAppFromReceiverStatus() {
        val json = MiniJson.parse(receiver) as Map<*, *>
        assertEquals("YouTube" to "389ad5b0", CastMedia.runningApp(json))
        assertNull(CastMedia.runningApp(MiniJson.parse("""{"status":{"applications":[{"displayName":"Backdrop","isIdleScreen":true,"transportId":"x"}]}}""") as Map<*, *>))
        assertNull(CastMedia.runningApp(MiniJson.parse("""{"status":{"isStandBy":true}}""") as Map<*, *>))
    }

    @Test
    fun readsTitleChannelStateAndPosition() {
        val status = ((MiniJson.parse(media) as Map<*, *>)["status"] as List<*>).first() as Map<*, *>
        val info = CastMedia.infoOf("YouTube", status, status["media"] as Map<*, *>, 1_000L)!!
        assertEquals("Alfaaz 💜 | Hamza Malik × Zain", info.title)
        assertEquals("melancholy", info.subtitle)
        assertEquals(true, info.playing)
        assertEquals(61.418, info.positionSec)
        assertEquals(185.181, info.durationSec)
        assertEquals(false, info.live)
        // An update without "media" keeps the title it had; paused is false; idle with no title is nothing.
        val paused = mapOf("playerState" to "PAUSED", "currentTime" to 70.0)
        assertEquals(false, CastMedia.infoOf("YouTube", paused, status["media"] as Map<*, *>, 2_000L)!!.playing)
        assertNull(CastMedia.infoOf("YouTube", mapOf("playerState" to "IDLE"), null, 0L))
        assertNull(CastMedia.infoOf("YouTube", null, null, 0L))
    }

    @Test
    fun parsesJsonValues() {
        assertEquals(listOf(1.0, -2.5, 1e3, true, false, null, "a\"b\n", mapOf<String, Any?>()), MiniJson.parse("""[1,-2.5,1e3,true,false,null,"a\"b\n",{}]"""))
        assertNull(MiniJson.parseOrNull("{\"a\":"))
    }
}
