package com.creativemedialabs.auraplay.feature.remote.client

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.creativemedialabs.auraplay.feature.remote.server.PlaybackRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class TvDevice(
    val name: String,
    val host: String,
    val port: Int
)

@Singleton
class NsdClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_http._tcp."
    private val serviceName = "AuraPlayTV"

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredDevices = MutableStateFlow<List<TvDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TvDevice>> = _discoveredDevices.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // --- TV Server Side: Register TV as Cast Target ---
    fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@NsdClient.serviceName
            this.serviceType = this@NsdClient.serviceType
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d("NsdClient", "TV service registered: ${serviceInfo.serviceName} on port ${serviceInfo.port}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdClient", "Service registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d("NsdClient", "Service unregistered successfully")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdClient", "Service unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e("NsdClient", "Exception during registerService", e)
        }
    }

    fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e("NsdClient", "Exception during unregisterService", e)
            }
            registrationListener = null
        }
    }

    // --- Mobile Side: Peer Discovery of Android TV Instances ---
    fun startDiscovery() {
        if (discoveryListener != null) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e("NsdClient", "Discovery failed to start: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e("NsdClient", "Discovery failed to stop: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d("NsdClient", "NSD Service discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d("NsdClient", "NSD Service discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d("NsdClient", "Service found: ${serviceInfo?.serviceName}")
                if (serviceInfo != null && serviceInfo.serviceName.contains("AuraPlay", ignoreCase = true)) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d("NsdClient", "Service lost: ${serviceInfo?.serviceName}")
                serviceInfo?.serviceName?.let { lostName ->
                    _discoveredDevices.value = _discoveredDevices.value.filter { it.name != lostName }
                }
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("NsdClient", "Exception during discoverServices", e)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e("NsdClient", "Resolve failed for ${serviceInfo?.serviceName}: $errorCode")
                }

                override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                    if (resolvedInfo == null) return
                    val host: InetAddress? = resolvedInfo.host
                    val port = resolvedInfo.port
                    val hostAddress = host?.hostAddress ?: return

                    Log.d("NsdClient", "Resolved TV: ${resolvedInfo.serviceName} at $hostAddress:$port")
                    val device = TvDevice(
                        name = resolvedInfo.serviceName,
                        host = hostAddress,
                        port = port
                    )

                    val currentList = _discoveredDevices.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.name == device.name }
                    if (existingIndex >= 0) {
                        currentList[existingIndex] = device
                    } else {
                        currentList.add(device)
                    }
                    _discoveredDevices.value = currentList
                }
            }
        )
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("NsdClient", "Exception during stopServiceDiscovery", e)
            }
            discoveryListener = null
        }
    }

    // --- Mobile Side: Push Stream Payload to Android TV Standalone Playback ---
    suspend fun pushStreamToTv(device: TvDevice, request: PlaybackRequest): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = json.encodeToString(request)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toRequestBody(mediaType)

            val httpRequest = Request.Builder()
                .url("http://${device.host}:${device.port}/play")
                .post(requestBody)
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("NsdClient", "Successfully pushed stream payload to TV ${device.name}")
                    Result.success(true)
                } else {
                    val msg = "TV responded with error code: ${response.code}"
                    Log.e("NsdClient", msg)
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Log.e("NsdClient", "Failed to push stream payload to TV", e)
            Result.failure(e)
        }
    }
}
