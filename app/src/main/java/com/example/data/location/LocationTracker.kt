package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class DeviceCoordinates(
    val latitude: Double,
    val longitude: Double
)

class LocationTracker(private val context: Context) {

    fun hasLocationPermission(): Boolean =
        hasPermission(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentCoordinates(): DeviceCoordinates? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val lastKnown = bestLastKnownLocation(manager)
        if (lastKnown != null) {
            return DeviceCoordinates(lastKnown.latitude, lastKnown.longitude)
        }

        val provider = when {
            isProviderEnabledSafe(manager, LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            isProviderEnabledSafe(manager, LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }

        val freshLocation = withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        manager,
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context)
                    ) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } ?: return null

        return DeviceCoordinates(freshLocation.latitude, freshLocation.longitude)
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(manager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun isProviderEnabledSafe(manager: LocationManager, provider: String): Boolean =
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)

    companion object {
        fun hasPermission(context: Context): Boolean {
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return coarse || fine
        }
    }
}
