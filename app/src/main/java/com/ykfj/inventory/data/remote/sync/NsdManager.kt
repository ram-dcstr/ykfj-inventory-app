package com.ykfj.inventory.data.remote.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the tablet as an NSD (mDNS/DNS-SD) service on the local WiFi network
 * so phones can discover it without manual IP configuration.
 *
 * Service type: [SERVICE_TYPE] (_ykfj._tcp.)
 * Service name: YKFJ-{first 8 chars of deviceId}
 * Port: whichever port the Ktor server is bound to (always [SyncServerManager.SERVER_PORT])
 *
 * Holds a [WifiManager.MulticastLock] while registered. The WiFi chip filters
 * inbound multicast in power-save mode by default — without the lock the tablet
 * can't reliably receive PTR queries from new phones joining the network, so
 * phones may never discover an already-running tablet.
 */
@Singleton
class NsdRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    /** Register the tablet's sync server. Call after the Ktor server has bound its port. */
    fun register(deviceId: String, port: Int) {
        if (registrationListener != null) return

        acquireMulticastLock()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "YKFJ-${deviceId.take(8)}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD registered as '${info.serviceName}' on port $port")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD registration failed: errorCode=$errorCode")
                registrationListener = null
                releaseMulticastLock()
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD service unregistered")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD unregistration failed: errorCode=$errorCode")
            }
        }

        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    /** Unregister the NSD service. Safe to call even if not registered. */
    fun unregister() {
        registrationListener?.let {
            runCatching { nsdManager.unregisterService(it) }
            registrationListener = null
        }
        releaseMulticastLock()
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

    companion object {
        const val SERVICE_TYPE = "_ykfj._tcp."
        private const val TAG = "NsdRegistrar"
        private const val MULTICAST_LOCK_TAG = "ykfj-nsd-registrar"
    }
}
