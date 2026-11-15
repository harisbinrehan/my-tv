package com.shoqremote.atv

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Wake-on-LAN for a box whose network slept with it; a box that ignores these packets is unaffected. */
object WakeOnLan {
    /** Six 0xFF bytes, then the MAC sixteen times. */
    fun packet(mac: String): ByteArray {
        val bytes = mac.split(':', '-').map { it.toInt(16).toByte() }
        require(bytes.size == 6) { "not a MAC address: $mac" }
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(96) { bytes[it % 6] }
    }

    /** Sends to the all-networks and /24 broadcast addresses and to [host], on ports 9 and 7. */
    fun send(macs: List<String>, host: String) {
        val targets = listOfNotNull("255.255.255.255", host.substringBeforeLast('.', "").takeIf { it.isNotEmpty() }?.let { "$it.255" }, host)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (mac in macs) {
                val data = packet(mac)
                for (target in targets) for (port in intArrayOf(9, 7)) {
                    runCatching { socket.send(DatagramPacket(data, data.size, InetAddress.getByName(target), port)) }
                }
            }
        }
    }
}
