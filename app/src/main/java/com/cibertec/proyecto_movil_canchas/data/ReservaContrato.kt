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