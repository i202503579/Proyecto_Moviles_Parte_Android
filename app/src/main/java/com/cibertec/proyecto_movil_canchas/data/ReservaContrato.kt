package com.cibertec.proyecto_movil_canchas.data

object ReservaContrato {
    const val NOMBRE_BASE_DATOS = "canchitas_local.db"
    const val VERSION_BASE_DATOS = 1

    const val TABLA_RESERVAS = "reservas"
    const val COLUMNA_ID = "id"
    const val COLUMNA_CANCHA_NOMBRE = "cancha_nombre"
    const val COLUMNA_CLIENTE_NOMBRE = "cliente_nombre"
    const val COLUMNA_HORA_INICIO = "hora_inicio"
    const val COLUMNA_HORA_FIN = "hora_fin"
    const val COLUMNA_ESTADO = "estado"
    const val COLUMNA_TIPO_CLIENTE = "tipo_cliente"
    const val COLUMNA_ADELANTO = "adelanto"
    const val COLUMNA_SALDO = "saldo"

    const val SQL_CREAR_TABLA = """
        CREATE TABLE $TABLA_RESERVAS (
            $COLUMNA_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMNA_CANCHA_NOMBRE TEXT NOT NULL,
            $COLUMNA_CLIENTE_NOMBRE TEXT NOT NULL,
            $COLUMNA_HORA_INICIO INTEGER NOT NULL,
            $COLUMNA_HORA_FIN INTEGER NOT NULL,
            $COLUMNA_ESTADO TEXT NOT NULL,
            $COLUMNA_TIPO_CLIENTE TEXT NOT NULL,
            $COLUMNA_ADELANTO REAL NOT NULL,
            $COLUMNA_SALDO REAL NOT NULL
        )
    """
}