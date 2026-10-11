package com.cibertec.proyecto_movil_canchas.model

    sealed class ResultadoReserva{
        data class EXITO(val idReserva: Long) : ResultadoReserva()
        object HorarioOcupado : ResultadoReserva()
        object Error  : ResultadoReserva()
    }

enum class ResultadoPago {
    EXITO,
    MONTO_INVALIDO,   // monto <= 0
    YA_PAGADA,
    NO_PERMITIDO,     // reserva cancelada
    ERROR
}
