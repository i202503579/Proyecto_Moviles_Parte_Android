package com.cibertec.proyecto_movil_canchas.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.cibertec.proyecto_movil_canchas.model.Cliente

class ClienteStore(context: Context) {
    private val dbHelper = CanchitasDbHelper.getInstance(context)
    fun buscarPorDocumento(documento: String): Cliente? =
        buscarPorDocumento(dbHelper.readableDatabase, documento)

    // se ejecuta dentro de ReservaStore (cliente nuevo + reserva se guardan juntos o no se guarda nada).
    companion object {
        fun buscarPorDocumento(db: SQLiteDatabase, documento: String): Cliente? {
            db.query(
                ClienteContrato.TABLA,
                arrayOf(
                    ClienteContrato.COLUMNA_ID,
                    ClienteContrato.COLUMNA_NOMBRE,
                    ClienteContrato.COLUMNA_DOCUMENTO,
                    ClienteContrato.COLUMNA_TELEFONO,
                    ClienteContrato.COLUMNA_TIPO
                ),
                "${ClienteContrato.COLUMNA_DOCUMENTO} = ?",
                arrayOf(documento),
                null, null, null
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                return Cliente(
                    id = cursor.getInt(0),
                    nombre = cursor.getString(1),
                    numeroDocumento = cursor.getString(2),
                    telefono = if (cursor.isNull(3)) null else cursor.getString(3),
                    tipoCliente = cursor.getString(4)
                )
            }
        }
        // if (!fallo) return idLocal else return -1
        fun insertar(db: SQLiteDatabase, cliente: Cliente): Long {
            val valores = ContentValues().apply {
                put(ClienteContrato.COLUMNA_NOMBRE, cliente.nombre)
                put(ClienteContrato.COLUMNA_DOCUMENTO, cliente.numeroDocumento)
                put(ClienteContrato.COLUMNA_TELEFONO, cliente.telefono)
                put(ClienteContrato.COLUMNA_TIPO, cliente.tipoCliente)
                put(ClienteContrato.COLUMNA_SYNCED, 0)
            }
            return db.insert(ClienteContrato.TABLA, null, valores)
        }
    }
}