package com.cibertec.proyecto_movil_canchas.model

data class Reserva(
    val id: Int = 0,
    val idServidor: Int? = null,   // id en SQL Server, para sincronizar
    val canchaId: Int,              // JOIN
    val canchaNombre: String,
    val clienteId: Int,             // JOIN
    val clienteNombre: String,
    val tipoCliente: String,
    val horaInicio: Long,
    val horaFin: Long,
    val precioReserva: Double,
    val montoRecibido: Double,
    val estado: String,
    val prestamoEstado: String? = null
){
    val saldo: Double get() = precioReserva - montoRecibido
}

