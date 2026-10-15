package com.cibertec.proyecto_movil_canchas.network

data class UsuarioDTO(
    val idUsuario: Int = 0,
    val usernameUsuario: String = "",
    val nombreUsuario: String = "",
    val telefono: String = "",
    val activoUsuario: Boolean = true,
    val rol: String = ""
)

data class CanchaDTO(
    val idCancha: Int = 0,
    val nombreCancha: String = "",
    val descripcionCancha: String = "",
    val precioAlquiler: Double = 0.0,
    val activoCancha: Boolean = true
)

data class ClaseDTO(
    val idClase: Int = 0,
    val idUsuario: Int = 0,
    val idCancha: Int = 0,
    val fechaClase: String = "",
    val horaInicio: String = "",
    val horaFin: String = "",
    val estadoClase: String = "Programada",
    val activoClase: Boolean = true
)

data class AlumnoDTO(
    val idAlumno: Int = 0,
    val nombreAlumno: String = "",
    val telefonoApoderado: String? = null,
    val fechaIngreso: String? = null,
    val activoAlumno: Boolean = true,
    val idUsuario: Int = 0
)

data class AsistenciaDTO(
    val idAsistencia: Int = 0,
    val idClase: Int = 0,
    val idAlumno: Int = 0,
    val estadoAsistencia: String = "Presente"
)

data class ReservaDTO(
    val idReserva: Int = 0,
    val horaInicio: String = "",
    val horaFin: String = "",
    val precioReserva: Double = 0.0,
    val estadoReserva: String = "Pendiente",
    val montoRecibido: Double = 0.0,
    val idCliente: Int = 0,
    val idCancha: Int = 0,
    val idUsuario: Int = 1
)

data class ClienteDTO(
    val idCliente: Int = 0,
    val nombreCliente: String = "",
    val numeroDocumento: String = "",
    val telefonoCliente: String? = null,
    val tipoCliente: String = "Particular"
)