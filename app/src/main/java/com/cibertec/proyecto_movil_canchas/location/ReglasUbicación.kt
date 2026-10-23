package com.cibertec.proyecto_movil_canchas.location

import android.location.Location
import android.os.Build
import androidx.collection.objectFloatMap
import okhttp3.Cookie


enum class ResultadoZona {DENTRO, FUERA, IMPRECISA, UBICACION_FALSA}

data class EvaluacionZona(
    val resultado: ResultadoZona,
    val distanciaMetros: Float,
    val precisionMetros: Float
)
object ReglasUbicación {
    //Coordenadas de centro de lima
    const val LATITUD_COMPLEJO = -12.117756
    const val LONGITUD_COMPLEJO = -77.032416
    const val RADIO_PERMITIDO_M = 150f
    const val PRECISION_MAXIMA_M = 100f

    const val EDAD_MAXIMA_UBICACION_MS = 30_000L
    const val TIEMPO_MAXIMO_ESPERA_MS = 20_000L

    fun evaluar(ubicacion: Location): EvaluacionZona{
        val precision = ubicacion.accuracy

        if (esUbicacionFalsa(ubicacion)){
            return EvaluacionZona(ResultadoZona.UBICACION_FALSA, 0f , precision)
        }
        if (precision > PRECISION_MAXIMA_M){
            return EvaluacionZona(ResultadoZona.IMPRECISA, 0f, precision)
        }
        val resultado = FloatArray(1)
        Location.distanceBetween(
            ubicacion.latitude, ubicacion.longitude,
            LATITUD_COMPLEJO, LONGITUD_COMPLEJO,
            resultado
        )
        val distancia = resultado[0]
        val zona = if (distancia <= RADIO_PERMITIDO_M) ResultadoZona.DENTRO else ResultadoZona.FUERA
        return EvaluacionZona(zona, distancia, precision)
    }
    private fun esUbicacionFalsa(ubicacion: Location): Boolean {
        if (esEmulador()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            ubicacion.isMock
        }else{
            @Suppress("DEPRECATION")
            ubicacion.isFromMockProvider
        }
    }

    private fun esEmulador(): Boolean =
        Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("sdk_gphone")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("vbox86p")
}