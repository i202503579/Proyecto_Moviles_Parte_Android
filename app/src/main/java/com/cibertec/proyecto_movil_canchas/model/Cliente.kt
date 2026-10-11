package com.cibertec.proyecto_movil_canchas.model

data class Cliente(
    val id: Int = 0,
    val nombre: String,
    val numeroDocumento: String,
    val telefono: String? = null,
    val tipoCliente: String
)
