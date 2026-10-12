package com.cibertec.proyecto_movil_canchas.model


fun redondear(valor: Double): Double = Math.round(valor * 100) / 100.0
const val TOLERANCIA_PAGO = 0.005

fun estadoSegunPago(montoRecibido: Double, precio: Double): String = when {
    montoRecibido <= 0.0 -> "Pendiente"
    montoRecibido >= precio - TOLERANCIA_PAGO -> "Completada"
    else -> "Confirmada"
}