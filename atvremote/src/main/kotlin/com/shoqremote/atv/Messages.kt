package com.shoqremote.atv

/** Pairing-port messages: Google's "polo" protocol, version 2, field numbers from polo.proto. */
internal object Polo {
    const val STATUS_OK = 200
    const val STATUS_BAD_SECRET = 402
    const val PAIRING_REQUEST_ACK = 11
    const val OPTIONS = 20
    const val CONFIGURATION_ACK = 31
    const val SECRET_ACK = 41

    private const val ENCODING_HEXADECIMAL = 3
    private const val ROLE_INPUT = 1
    private const val CODE_LENGTH = 6

    fun pairingRequest(clientName: String) = outer {
        message(10) {
            string(1, "atvremote")
            string(2, clientName)
        }
    }

    fun options() = outer {
        message(20) {
            message(1) {
                varint(1, ENCODING_HEXADECIMAL)
                varint(2, CODE_LENGTH)
            }
            varint(3, ROLE_INPUT)
        }
    }

    fun configuration() = outer {
        message(30) {
            message(1) {
                varint(1, ENCODING_HEXADECIMAL)
                varint(2, CODE_LENGTH)
            }
            varint(2, ROLE_INPUT)
        }
    }

    fun secret(secret: ByteArray) = outer { message(40) { bytes(1, secret) } }

    private fun outer(body: ProtoWriter.() -> Unit): ByteArray =
        ProtoWriter().varint(1, 2).varint(2, STATUS_OK).apply(body).toByteArray()
}

/** Remote-port messages; field numbers from remotemessage.proto. */
internal object Remote {
    const val CONFIGURE = 1
    const val SET_ACTIVE = 2
    const val PING_REQUEST = 8
    const val IME_KEY_INJECT = 20
    const val IME_BATCH_EDIT = 21
    const val IME_SHOW_REQUEST = 22
    const val VOICE_BEGIN = 30
    const val START = 40
    const val SET_VOLUME_LEVEL = 50

    private const val PING_RESPONSE = 9
    private const val KEY_INJECT = 10
    private const val VOICE_PAYLOAD = 31
    private const val VOICE_END = 32
    private const val APP_LINK_LAUNCH = 90

    private const val FEATURE_PING = 1
    private const val FEATURE_KEY = 2
    private const val FEATURE_IME = 4
    private const val FEATURE_VOICE = 8
    private const val FEATURE_POWER = 32
    private const val FEATURE_VOLUME = 64
    private const val FEATURE_APP_LINK = 512
    const val FEATURES =
        FEATURE_PING or FEATURE_KEY or FEATURE_IME or FEATURE_VOICE or FEATURE_POWER or FEATURE_VOLUME or FEATURE_APP_LINK

    fun voiceBegin(session: Int) = ProtoWriter().message(VOICE_BEGIN) { varint(1, session) }.toByteArray()

    fun voicePayload(session: Int, samples: ByteArray) = ProtoWriter().message(VOICE_PAYLOAD) {
        varint(1, session)
        bytes(2, samples)
    }.toByteArray()

    fun voiceEnd(session: Int) = ProtoWriter().message(VOICE_END) { varint(1, session) }.toByteArray()

    fun configure(features: Int) = ProtoWriter().message(CONFIGURE) {
        varint(1, features)
        message(2) {
            varint(3, 1)
            string(4, "1")
            string(5, "atvremote")
            string(6, "1.0.0")
        }
    }.toByteArray()

    fun setActive(features: Int) = ProtoWriter().message(SET_ACTIVE) { varint(1, features) }.toByteArray()

    fun pingResponse(value: Int) = ProtoWriter().message(PING_RESPONSE) { varint(1, value) }.toByteArray()

    fun key(code: Int, direction: Int) = ProtoWriter().message(KEY_INJECT) {
        varint(1, code)
        varint(2, direction)
    }.toByteArray()

    fun appLink(link: String) = ProtoWriter().message(APP_LINK_LAUNCH) { string(1, link) }.toByteArray()

    fun text(imeCounter: Int, fieldCounter: Int, text: String): ByteArray {
        val length = text.codePointCount(0, text.length)
        return ProtoWriter().message(IME_BATCH_EDIT) {
            varint(1, imeCounter)
            varint(2, fieldCounter)
            message(3) {
                varint(1, 1)
                message(2) {
                    varint(1, length)
                    varint(2, length)
                    string(3, text)
                }
            }
        }.toByteArray()
    }
}
