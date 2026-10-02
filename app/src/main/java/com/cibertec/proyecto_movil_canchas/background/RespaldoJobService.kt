package com.cibertec.proyecto_movil_canchas.background

import android.app.job.JobParameters
import android.app.job.JobService
import androidx.core.content.edit
import com.cibertec.proyecto_movil_canchas.data.ReservaStore
import java.io.File

class RespaldoJobService: JobService() {

    private var hiloRespaldo: Thread? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        hiloRespaldo = Thread {
            val resultado = runCatching {
                val reservas = ReservaStore(applicationContext).obtenerTodas()
                val archivo = File(filesDir, NOMBRE_RESPALDO)

                // buildString crea todo antes de escribirlo en el archivo private
                val contenido =buildString {
                    appendLine(CABECERA_CSV)
                    reservas.forEach { reserva ->
                        appendLine(
                            listOf(
                                reserva.id.toString(),
                                reserva.canchaNombre,
                                reserva.clienteNombre,
                                reserva.horaInicio.toString(),
                                reserva.horaFin.toString(),
                                reserva.estado,
                                reserva.tipoCliente,
                                reserva.adelanto.toString(),
                                reserva.saldo.toString()
                            ).joinToString(SEPARADOR_CSV) { valor -> escaparCsv(valor)}
                        )
                    }
                }
                archivo.writeText(contenido)
                // SharedPreferences guarda un dato sencillo de estado; SQLite conserva
                // las solicitudes y Files conserva el respaldo y las imágenes.
                getSharedPreferences(
                    RespaldoScheduler.PREFERENCIAS_RESPALDO,
                        MODE_PRIVATE
                ).edit {
                        putLong(RespaldoScheduler.CLAVE_ULTIMO_RESPALDO, System.currentTimeMillis())
                }
            }
            if (!Thread.currentThread().isInterrupted){
                jobFinished(params, resultado.isFailure)
            }
        }.apply {
            name = "respaldo-reservas"
            start()
        }
        // true informa que todavía existe trabajo ejecutándose después de onStartJob().
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        hiloRespaldo?.interrupt()
        // true solicita otro intento si el sistema interrumpió la tarea.
        return true
    }

    private fun escaparCsv(valor: String): String{
        return "\"${valor.replace("\"", "\"\"")}\""
    }
    companion object{
        private const val NOMBRE_RESPALDO = "respaldo_reserva.csv"
        private const val CABECERA_CSV = "id,canchaNombre,clienteNombre,horaInicio,horaFin,estado,tipoCliente,adelanto,saldo"
        private const val SEPARADOR_CSV = ","
    }
}