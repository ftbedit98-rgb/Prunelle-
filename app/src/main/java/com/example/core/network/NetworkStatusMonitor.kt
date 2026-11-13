package com.example.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkStatus(
    val isOnline: Boolean = true,
    val isMetered: Boolean = false,
    val isValidated: Boolean = true,
    val transportLabel: String = "Wi-Fi"
)

class NetworkStatusMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(readCurrentStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkStatus.value = readCurrentStatus()
        }

        override fun onLost(network: Network) {
            _networkStatus.value = readCurrentStatus()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _networkStatus.value = evaluateCapabilities(networkCapabilities)
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        }
    }

    fun refresh() {
        _networkStatus.value = readCurrentStatus()
    }

    private fun readCurrentStatus(): NetworkStatus {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatus(
            isOnline = false,
            isMetered = false,
            isValidated = false,
            transportLabel = "Hors ligne"
        )
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkStatus(
                isOnline = false,
                isMetered = false,
                isValidated = false,
                transportLabel = "Hors ligne"
            )
        return evaluateCapabilities(caps)
    }

    private fun evaluateCapabilities(caps: NetworkCapabilities): NetworkStatus {
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val effectiveOnline = hasInternet && isValidated
        val label = when {
            !hasInternet -> "Hors ligne"
            !isValidated -> "Connexion limitée"
            isWifi -> "Wi-Fi"
            isCellular -> "Mobile"
            isEthernet -> "Ethernet"
            else -> "Réseau"
        }
        return NetworkStatus(
            isOnline = effectiveOnline,
            isMetered = isMetered,
            isValidated = isValidated,
            transportLabel = label
        )
    }

    fun unregister() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
