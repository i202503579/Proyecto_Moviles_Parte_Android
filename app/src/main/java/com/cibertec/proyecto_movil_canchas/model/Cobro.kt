package com.cibertec.proyecto_movil_canchas.model
data class Cobro(val aplicado: Double, val vuelto: Double)

fun calcularCobro(saldo: Double, montoEntregado: Double): Cobro {
    val aplicado = minOf(montoEntregado, saldo)
    return Cobro(
        aplicado = redondear(aplicado),
        vuelto = redondear(montoEntregado - aplicado)
    )
}

// A centavos: sumar pagos con decimales deja residuos tipo 0.0000001 en el saldo.
private fun redondear(valor: Double): Double = Math.round(valor * 100) / 100.0
