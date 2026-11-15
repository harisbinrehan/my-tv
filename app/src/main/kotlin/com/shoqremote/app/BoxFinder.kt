package com.shoqremote.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.shoqremote.atv.ShoqBox
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Finds devices advertising Google's TV remote service, for when the router gives the box a new address. */
class BoxFinder(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    suspend fun discover(timeoutMs: Long = 4_000): List<String> {
        val lock = wifi.createMulticastLock("shoq-remote").apply {
            setReferenceCounted(false)
            acquire()
        }
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                found.trySend(serviceInfo)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                found.close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        val services = mutableListOf<NsdServiceInfo>()
        try {
            nsd.discoverServices(ShoqBox.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            withTimeoutOrNull(timeoutMs) { for (service in found) services += service }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
            lock.release()
        }
        return services.mapNotNull { resolve(it) }.distinct()
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(service: NsdServiceInfo): String? = withTimeoutOrNull(3_000) {
        suspendCancellableCoroutine { continuation ->
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (continuation.isActive) continuation.resume(serviceInfo.host?.hostAddress)
                }
            })
        }
    }
}
