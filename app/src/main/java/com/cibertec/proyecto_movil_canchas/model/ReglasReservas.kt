package com.cibertec.proyecto_movil_canchas.model

import java.util.Calendar

object ReglasReservas {
    val HORAS_PERMITIDAS = listOf(1,2)
    const val MS_POR_HORA = 3_600_000L
    const val HORA_APERTURA = 8
    const val HORA_CIERRE = 23

    fun inicioMinimoReserva(): Long = Calendar.getInstance().apply {
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun dentroDeHorarioAtencion(horaInicio: Long, horas: Int): Boolean {
        val calendario = Calendar.getInstance().apply { timeInMillis = horaInicio }
        val minutosInicio = calendario.get(Calendar.HOUR_OF_DAY) * 60 + calendario.get(Calendar.MINUTE)
        val minutosFin = minutosInicio + horas * 60
        return minutosInicio >= HORA_APERTURA * 60 && minutosFin <= HORA_CIERRE * 60
    }
}