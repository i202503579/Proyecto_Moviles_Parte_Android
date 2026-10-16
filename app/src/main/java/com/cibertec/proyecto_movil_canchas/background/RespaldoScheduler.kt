package com.cibertec.proyecto_movil_canchas.background

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

object RespaldoScheduler {
    private const val JOB_ID = 1001
    const val PREFERENCIAS_RESPALDO = "estado_respaldo"
    const val CLAVE_ULTIMO_RESPALDO = "ultimo_respaldo"

    fun programar(context: Context): Int {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val info = JobInfo.Builder(JOB_ID, ComponentName(context, RespaldoJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(15 * 60 * 1000L)
            .build()
        return scheduler.schedule(info)
    }

}