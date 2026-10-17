package com.cibertec.proyecto_movil_canchas.data

object ReservaContrato {
    const val TABLA_RESERVAS = "reserva"

    const val COLUMNA_ID = "id_reserva"         // id local (autoincremental)
    const val COLUMNA_ID_SERVIDOR = "id_servidor"
    const val COLUMNA_ID_CANCHA = "id_cancha"
    const val COLUMNA_ID_CLIENTE = "id_cliente"
    const val COLUMNA_ID_USUARIO = "id_usuario" // se llena cuando exista el login
    const val COLUMNA_HORA_INICIO = "hora_inicio"
    const val COLUMNA_HORA_FIN = "hora_fin"
    const val COLUMNA_PRECIO = "precio_reserva"
    const val COLUMNA_MONTO_RECIBIDO = "monto_recibido"
    const val COLUMNA_ESTADO = "estado"
    const val COLUMNA_SYNCED = "synced"
}

object CanchaContrato {
    const val TABLA = "cancha"
    const val COLUMNA_ID = "id_cancha"
    const val COLUMNA_NOMBRE = "nombre"
    const val COLUMNA_PRECIO = "precio_alquiler"
    const val COLUMNA_ACTIVO = "activo"
}

object ClienteContrato {
    const val TABLA = "cliente"
    const val COLUMNA_ID = "id_cliente"
    const val COLUMNA_ID_SERVIDOR = "id_servidor"
    const val COLUMNA_NOMBRE = "nombre"
    const val COLUMNA_DOCUMENTO = "numero_documento"
    const val COLUMNA_TELEFONO = "telefono"
    const val COLUMNA_TIPO = "tipo_cliente"
    const val COLUMNA_SYNCED = "synced"
}

object PrestamoContrato {
    const val TABLA = "prestamo_balon"
    const val COLUMNA_ID = "id_prestamo"
    const val COLUMNA_ID_SERVIDOR = "id_servidor"
    const val COLUMNA_ID_RESERVA = "id_reserva"
    const val COLUMNA_FECHA_ENTREGA = "fecha_entrega"
    const val COLUMNA_FECHA_DEVOLUCION = "fecha_devolucion"  //null hasta q lo devuelban
    const val COLUMNA_ESTADO = "estado"           // Pendiente | Devuelto | Dañado | Perdido
    const val COLUMNA_SYNCED = "synced"
}

object UsuarioProfesorContrato {
    const val TABLA = "usuario_profesor"
    const val COLUMNA_ID = "id_usuario"
    const val COLUMNA_NOMBRE = "nombre_usuario"
    const val COLUMNA_ROL = "rol"
    const val COLUMNA_ACTIVO = "activo"
}

object ClaseContrato {
    const val TABLA = "clase"
    const val COLUMNA_ID = "id_clase"
    const val COLUMNA_ID_USUARIO = "id_usuario"
    const val COLUMNA_NOMBRE_PROFESOR = "nombre_profesor"
    const val COLUMNA_ID_CANCHA = "id_cancha"
    const val COLUMNA_NOMBRE_CANCHA = "nombre_cancha"
    const val COLUMNA_FECHA = "fecha_clase"
    const val COLUMNA_HORA_INICIO = "hora_inicio"
    const val COLUMNA_HORA_FIN = "hora_fin"
    const val COLUMNA_ESTADO = "estado_clase"
    const val COLUMNA_ACTIVO = "activo_clase"
    const val COLUMNA_SYNCED = "synced"
}

object AlumnoContrato {
    const val TABLA = "alumno"
    const val COLUMNA_ID = "id_alumno"
    const val COLUMNA_NOMBRE = "nombre_alumno"
    const val COLUMNA_TELEFONO_APODERADO = "telefono_apoderado"
    const val COLUMNA_ACTIVO = "activo_alumno"
    const val COLUMNA_ID_USUARIO = "id_usuario"
}

object AsistenciaContrato {
    const val TABLA = "asistencia"
    const val COLUMNA_ID_LOCAL = "id_asistencia_local"
    const val COLUMNA_ID_SERVIDOR = "id_asistencia_servidor"
    const val COLUMNA_ID_CLASE = "id_clase"
    const val COLUMNA_ID_ALUMNO = "id_alumno"
    const val COLUMNA_ESTADO = "estado_asistencia" // Presente | Falta | Justificado
    const val COLUMNA_SYNCED = "synced"
}