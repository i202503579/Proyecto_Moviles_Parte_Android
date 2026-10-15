package com.cibertec.proyecto_movil_canchas.model

data class ProfesorItem(
    val idUsuario: Int,
    val nombre: String
)

data class ClaseAcademia(
    val idClase: Int,
    val idUsuario: Int,
    val nombreProfesor: String,
    val idCancha: Int,
    val nombreCancha: String,
    val fechaClase: String,
    val horaInicio: String,
    val horaFin: String,
    val estadoClase: String,
    val activoClase: Boolean,
    val totalAlumnos: Int = 0,
    val totalAsistenciasMarcadas: Int = 0,
    val synced: Boolean = true
)

data class AlumnoAsistenciaItem(
    val idAlumno: Int,
    val nombreAlumno: String,
    val telefonoApoderado: String?,
    val idAsistenciaServidor: Int = 0,
    var estadoAsistencia: String = "Presente"
)

enum class FiltroEstadoClase {
    TODAS, PROGRAMADAS, COMPLETADAS, CANCELADAS
}