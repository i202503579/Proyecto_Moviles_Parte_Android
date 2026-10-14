package com.cibertec.proyecto_movil_canchas.model

enum class RangoFecha { HOY, PROXIMAS, TODAS}
enum class FiltroEstado { ACTIVAS, PENDIENTES, CONFIRMADAS, COMPLETADAS, CANCELADAS }
data class FiltroReservas(
    val rango: RangoFecha = RangoFecha.PROXIMAS,
    val estado: FiltroEstado = FiltroEstado.ACTIVAS
)
