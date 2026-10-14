package com.cibertec.proyecto_movil_canchas.model

    sealed class ResultadoReserva{
        data class EXITO(val idReserva: Long) : ResultadoReserva()
        object HorarioOcupado : ResultadoReserva()
        object Error  : ResultadoReserva()
    }

enum class ResultadoPago {
    EXITO,
    MONTO_INVALIDO,   // monto <= 0
    EXCEDE_SALDO,
    EXCEDE_PAGADO,
    NO_PERMITIDO,     // reserva cancelada
    ERROR
}
enum class ResultadoRestauracion { EXITO, HORARIO_OCUPADO, NO_PERMITIDO, ERROR }
enum class ResultadoEliminacion { EXITO, NO_PERMINTIDO, CON_PAGOS, ERROR }