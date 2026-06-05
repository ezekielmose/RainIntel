package com.valentinerutto.farmvision.util.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class CurrentLocation(
    val latitude: Double,
    val longitude: Double
)

class DeviceLocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): CurrentLocation? {
        if (!hasLocationPermission()) return null

        val lastLocation = fusedLocationClient.lastLocation.awaitLocation()
        val location = lastLocation ?: getFreshLocation()

        return location?.let {
            CurrentLocation(
                latitude = it.latitude,
                longitude = it.longitude
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocation(): Location? {
        val cancellationTokenSource = CancellationTokenSource()
        return fusedLocationClient
            .getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            )
            .awaitLocation {
                cancellationTokenSource.cancel()
            }
    }
}

private suspend fun Task<Location>.awaitLocation(
    onCancel: () -> Unit = {}
): Location? {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { location ->
            if (continuation.isActive) {
                continuation.resume(location)
            }
        }
        addOnFailureListener {
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
        continuation.invokeOnCancellation {
            onCancel()
        }
    }
}
