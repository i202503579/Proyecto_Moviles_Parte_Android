package com.cibertec.proyecto_movil_canchas.data

import android.content.ContentValues
import android.content.Context
import com.cibertec.proyecto_movil_canchas.model.Reserva

class ReservaStore(context: Context) {

    private val dbHelper = CanchitasDbHelper(context.applicationContext)

    fun agregar(
        canchaNombre: String,
        clienteNombre: String,
        horaInicio: Long,
        horaFin: Long,
        estado: String,
        tipoCliente: String,
        adelanto: Double,
        saldo: Double
    ): Long {
        val db = dbHelper.writableDatabase
        val valores = ContentValues().apply {
            put(ReservaContrato.COLUMNA_CANCHA_NOMBRE, canchaNombre)
            put(ReservaContrato.COLUMNA_CLIENTE_NOMBRE, clienteNombre)
            put(ReservaContrato.COLUMNA_HORA_INICIO, horaInicio)
            put(ReservaContrato.COLUMNA_HORA_FIN, horaFin)
            put(ReservaContrato.COLUMNA_ESTADO, estado)
            put(ReservaContrato.COLUMNA_TIPO_CLIENTE, tipoCliente)
            put(ReservaContrato.COLUMNA_ADELANTO, adelanto)
            put(ReservaContrato.COLUMNA_SALDO, saldo)
        }
        return db.insert(ReservaContrato.TABLA_RESERVAS, null, valores)
    }

    fun obtenerTodas(): List<Reserva> {
        val lista = mutableListOf<Reserva>()
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            ReservaContrato.TABLA_RESERVAS,
            null, null, null, null, null, null
        )

        while (cursor.moveToNext()) {
            val reserva = Reserva(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(ReservaContrato.COLUMNA_ID)),
                canchaNombre = cursor.getString(cursor.getColumnIndexOrThrow(ReservaContrato.COLUMNA_CANCHA_NOMBRE)),
                clienteNombre = cursor.getString(cursor.getColumnIndexOrThrow(ReservaContrato.COLUMNA_CLIENTE_NOMBRE)),
                horaInicio = cursor.getLong(cursor.getColumnIndexOrThrow(ReservaContrato.COLUMNA_HORA_INICIO)),
                horaFin = cursor.getLong(cursor.getColumnIndexOrThrow(ReservaContrato.COLUMNA_HORA_FIN)),
                estado = cursor.getString(cursor.getColumnIndexOrThrow(ReservaContrato.COLUMNA_ESTADO)),
                tipoCliente = cursor.getString(cursor.getColumnIndexOrThrow(ReservaContrato.COLUMNA_TIPO_CLIENTE)),
                adelanto = cursor.getDouble(cursor.getColumnIndexOrThrow(ReservaContrato.COLUMNA_ADELANTO)),
                saldo = cursor.getDouble(cursor.getColumnIndexOrThrow(ReservaContrato.COLUMNA_SALDO))
            )
            lista.add(reserva)
        }
        cursor.close()
        return lista
    }

    fun actualizar(reserva: Reserva): Int {
        val db = dbHelper.writableDatabase
        val valores = ContentValues().apply {
            put(ReservaContrato.COLUMNA_CANCHA_NOMBRE, reserva.canchaNombre)
            put(ReservaContrato.COLUMNA_CLIENTE_NOMBRE, reserva.clienteNombre)
            put(ReservaContrato.COLUMNA_HORA_INICIO, reserva.horaInicio)
            put(ReservaContrato.COLUMNA_HORA_FIN, reserva.horaFin)
            put(ReservaContrato.COLUMNA_ESTADO, reserva.estado)
            put(ReservaContrato.COLUMNA_TIPO_CLIENTE, reserva.tipoCliente)
            put(ReservaContrato.COLUMNA_ADELANTO, reserva.adelanto)
            put(ReservaContrato.COLUMNA_SALDO, reserva.saldo)
        }
        return db.update(
            ReservaContrato.TABLA_RESERVAS,
            valores,
            "${ReservaContrato.COLUMNA_ID} = ?",
            arrayOf(reserva.id.toString())
        )
    }

    fun eliminar(id: Int): Int {
        val db = dbHelper.writableDatabase
        return db.delete(
            ReservaContrato.TABLA_RESERVAS,
            "${ReservaContrato.COLUMNA_ID} = ?",
            arrayOf(id.toString())
        )
    }
}