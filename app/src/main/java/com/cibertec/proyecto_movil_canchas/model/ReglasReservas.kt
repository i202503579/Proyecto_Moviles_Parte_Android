package com.cibertec.proyecto_movil_canchas.model

import java.util.Calendar

object ReglasReservas {
    val HORAS_PERMITIDAS = listOf(1,2)
    const val MS_POR_HORA = 3_600_000L

    fun inicioMinimoReserva(): Long = Calendar.getInstance().apply {
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}