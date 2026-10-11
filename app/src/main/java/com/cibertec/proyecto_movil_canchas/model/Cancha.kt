package com.cibertec.proyecto_movil_canchas.model

data class Cancha(
    val id: Int,
    val nombre: String,
    val precioAlquiler: Double
){
    override fun toString(): String = nombre
}
