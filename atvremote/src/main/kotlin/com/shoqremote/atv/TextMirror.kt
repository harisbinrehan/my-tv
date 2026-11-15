package com.shoqremote.atv

/**
 * Keeps the focused TV text box in step with a phone text field. The box inserts sent text at its cursor
 * and honours Backspace, so each change deletes back to where the texts differ and types the rest.
 * It only ever deletes what it typed: on an empty TV box, apps such as YouTube treat Backspace as Back.
 * [initial] carries what an earlier connection typed, so a reconnect doesn't type it twice.
 */
class TextMirror(private val remote: RemoteClient, initial: String = "") {
    @Volatile private var mirrored = initial

    val text: String get() = mirrored

    @Synchronized
    fun update(text: String) {
        val (deletes, insert) = diff(mirrored, text)
        repeat(deletes) { remote.sendKey(Keys.DEL) }
        // Record each half as it lands, so a send that fails midway leaves an accurate picture of the TV box.
        mirrored = mirrored.commonPrefixWith(text)
        if (insert.isNotEmpty()) remote.sendText(insert)
        mirrored = text
    }

    /** Removes everything this mirror typed, and nothing more. */
    fun clearTyped() = update("")

    /** Starts mirroring from an empty phone field without touching what the TV box holds. */
    @Synchronized
    fun forget() {
        mirrored = ""
    }

    companion object {
        /**
         * Backspaces needed after the shared start of the two texts, then the text to type. Backspaces count
         * code points, not UTF-16 units: one Backspace removes a whole emoji, and an extra one could act as Back.
         */
        fun diff(from: String, to: String): Pair<Int, String> {
            val common = from.commonPrefixWith(to).length
            return from.codePointCount(common, from.length) to to.substring(common)
        }
    }
}
