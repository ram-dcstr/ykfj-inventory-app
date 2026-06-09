package com.ykfj.inventory.data.remote.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Resolved NSD service — host IP + port of the tablet's Ktor server. */
data class ResolvedService(val host: String, val port: Int)

/**
 * Discovers YKFJ tablet services on the local WiFi network using NSD (mDNS/DNS-SD).
 * Publishes the first successfully resolved service to [discoveredService].
 *
 * Call [startDiscovery] when the phone comes online and [stopDiscovery] when done.
 * [ConnectionResolver] consumes [discoveredService] to pick the best connection target.
 *
 * Holds a [WifiManager.MulticastLock] while discovery is active. Without it,
 * the WiFi chip filters out incoming multicast packets in power-save mode and
 * the tablet's mDNS announcements never reach the app — discovery succeeds at
 * the API level but never produces any results.
 */
@Singleton
class NsdDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredService = MutableStateFlow<ResolvedService?>(null)
    val discoveredService: StateFlow<ResolvedService?> = _discoveredService.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Guard: NsdManager.resolveService() accepts one request at a time on API < 34.
    @Volatile private var isResolving = false

    /** Start listening for YKFJ tablet services. Idempotent. */
    fun startDiscovery() {
        if (discoveryListener != null) return

        acquireMulticastLock()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: errorCode=$errorCode")
                discoveryListener = null
                releaseMulticastLock()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: errorCode=$errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "NSD discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "NSD discovery stopped")
                discoveryListener = null
                releaseMulticastLock()
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "NSD service found: ${serviceInfo.serviceName}")
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "NSD service lost: ${serviceInfo.serviceName}")
                _discoveredService.value = null
            }
        }

        discoveryListener = listener
        nsdManager.discoverServices(NsdRegistrar.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    /** Stop discovery. Safe to call when not discovering. */
    fun stopDiscovery() {
        discoveryListener?.let {
            runCatching { nsdManager.stopServiceDiscovery(it) }
            discoveryListener = null
        }
        releaseMulticastLock()
        _discoveredService.value = null
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        runCatching {
            multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.i(TAG, "Multicast lock acquired")
        }.onFailure { Log.w(TAG, "Failed to acquire multicast lock", it) }
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        if (isResolving) return
        isResolving = true

        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD resolve failed: errorCode=$errorCode")
                isResolving = false
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress
                val port = info.port
                isResolving = false

                if (host.isNullOrBlank()) {
                    Log.w(TAG, "NSD resolved but host address is null")
                    return
                }

                Log.i(TAG, "NSD resolved: $host:$port (${info.serviceName})")
                _discoveredService.value = ResolvedService(host, port)
            }
        })
    }

    companion object {
        private const val TAG = "NsdDiscovery"
        private const val MULTICAST_LOCK_TAG = "ykfj-nsd-discovery"
    }
}
