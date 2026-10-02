package com.cibertec.proyecto_movil_canchas.background

import android.app.job.JobScheduler
import android.content.Context

object RespaldoScheduler {
    fun programar(context: Context): Int{
        return JobScheduler.RESULT_FAILURE
    }
    const val PREFERENCIAS_RESPALDO = "estado_respaldo"
    const val CLAVE_ULTIMO_RESPALDO = "ultimo_respaldo"

}