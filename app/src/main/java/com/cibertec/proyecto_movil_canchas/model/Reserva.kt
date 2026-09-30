package com.cibertec.proyecto_movil_canchas.model

data class Reserva(
    val id: Int = 0,
    val canchaNombre: String,
    val clienteNombre: String,
    val horaInicio: Long,
    val horaFin: Long,
    val estado: String,
    val tipoCliente: String,
    val adelanto: Double,
    val saldo: Double
)

