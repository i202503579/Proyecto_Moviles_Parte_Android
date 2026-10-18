package com.cibertec.proyecto_movil_canchas.location

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.content.Context
import android.os.SystemClock
import androidx.core.content.getSystemService
import android.os.Handler

class LocationCliente(context: Context) {
    private val locationManager: LocationManager? = context.getSystemService()
    private var activeListener: LocationListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tareaTiempoAgotado: Runnable? = null

    fun isLocationEnabled(): Boolean{
        val manager = locationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(
        onSuccess: (Location) -> Unit,
        onError: () -> Unit
    ){
        val manager = locationManager ?: run { onError(); return }
         val proveedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
             .filter { manager.isProviderEnabled(it) }
        if (proveedores.isEmpty()){ onError(); return}
        val reciente = proveedores
            .mapNotNull { manager.getLastKnownLocation(it) }
            .filter { edadMS(it) <= ReglasUbicación.EDAD_MAXIMA_UBICACION_MS }
            .filter { it.accuracy <= ReglasUbicación.PRECISION_MAXIMA_M }
            .maxByOrNull { it.elapsedRealtimeNanos }
        if (reciente != null){
            onSuccess(reciente)
            return
        }
        stopUpdates()
        var mejor: Location? = null
        val listener = object : LocationListener{
            override fun onLocationChanged(location: Location) {
                val actual = mejor
                if (actual == null || location.accuracy < actual.accuracy) mejor = location
                if (location.accuracy <= ReglasUbicación.PRECISION_MAXIMA_M){
                    stopUpdates()
                    onSuccess(location)
                }
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        activeListener = listener

        proveedores.forEach { manager.requestLocationUpdates(it, 1000L, 0f, listener) }
        val tarea = Runnable{
            stopUpdates()
            val respaldo = mejor
            if (respaldo != null) onSuccess(respaldo) else onError()
        }
        tareaTiempoAgotado = tarea
        handler.postDelayed(tarea, ReglasUbicación.TIEMPO_MAXIMO_ESPERA_MS)
    }
    fun stopUpdates(){
        tareaTiempoAgotado?.let { handler.removeCallbacks(it) }
        tareaTiempoAgotado = null
        val manager = locationManager ?: return
        val listener = activeListener ?: return
        manager.removeUpdates(listener)
        activeListener = null
    }
    private fun edadMS(ubicacion: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - ubicacion.elapsedRealtimeNanos) / 1_000_000L
}