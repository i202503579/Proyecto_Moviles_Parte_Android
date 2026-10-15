package com.cibertec.proyecto_movil_canchas.model

    sealed class ResultadoReserva{
        data class EXITO(val idReserva: Long) : ResultadoReserva()
        object HorarioOcupado : ResultadoReserva()
        object Error  : ResultadoReserva()
    }

enum class ResultadoPago { EXITO, MONTO_INVALIDO, EXCEDE_SALDO, EXCEDE_PAGADO, NO_PERMITIDO, ERROR }
enum class ResultadoPrestamo { EXITO, NO_PERMITIDO, NO_INSTITUCIONAL, YA_PRESTADO, SIN_PRESTAMO_PENDIENTE, ESTADO_INVALIDO, ERROR }
enum class ResultadoRestauracion { EXITO, HORARIO_OCUPADO, NO_PERMITIDO, ERROR }