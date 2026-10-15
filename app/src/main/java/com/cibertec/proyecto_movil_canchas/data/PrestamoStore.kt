package com.cibertec.proyecto_movil_canchas.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.cibertec.proyecto_movil_canchas.model.ResultadoPrestamo

class PrestamoStore(context: Context) {
        private val dbHelper = CanchitasDbHelper.getInstance(context)
    fun prestar(idReserva: Int): ResultadoPrestamo{
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            var estado = ""
            var tipoCliente = ""
            val encontrada = db.rawQuery(
                """
                    SELECT r.${ReservaContrato.COLUMNA_ESTADO}, cl.${ClienteContrato.COLUMNA_TIPO}
                    FROM ${ReservaContrato.TABLA_RESERVAS} r
                    JOIN ${ClienteContrato.TABLA} cl
                    ON cl.${ClienteContrato.COLUMNA_ID} = r.${ReservaContrato.COLUMNA_ID_CLIENTE}
                    WHERE r.${ReservaContrato.COLUMNA_ID} = ?
                """.trimIndent(),
                arrayOf(idReserva.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()){
                    estado = cursor.getString(0)
                    tipoCliente = cursor.getString(1)
                    true
                } else false
            }
            if (!encontrada) return ResultadoPrestamo.ERROR
            if (estado == "Cancelada") return ResultadoPrestamo.NO_PERMITIDO
            if (tipoCliente != "Institucional") return ResultadoPrestamo.NO_INSTITUCIONAL
            if (existePrestamo(db, idReserva)) return ResultadoPrestamo.YA_PRESTADO
            val valores = ContentValues().apply {
                put(PrestamoContrato.COLUMNA_ID_RESERVA, idReserva)
                put(PrestamoContrato.COLUMNA_FECHA_ENTREGA, System.currentTimeMillis())
                put(PrestamoContrato.COLUMNA_ESTADO, "Pendiente")
                put(PrestamoContrato.COLUMNA_SYNCED, 0)
            }
            if (db.insert(PrestamoContrato.TABLA, null, valores) == -1L) return ResultadoPrestamo.ERROR
            db.setTransactionSuccessful()
            return ResultadoPrestamo.EXITO
        } catch (e: Exception){
            return ResultadoPrestamo.ERROR
        } finally {
            db.endTransaction()
        }
    }
    fun registrarDevolucion(idReserva: Int, estadoFinal: String): ResultadoPrestamo{
        if (estadoFinal !in ESTADOS_CIERRE) return ResultadoPrestamo.ESTADO_INVALIDO
        val valores = ContentValues().apply {
            put(PrestamoContrato.COLUMNA_ESTADO, estadoFinal)
            if (estadoFinal == "Perdido"){
                putNull(PrestamoContrato.COLUMNA_FECHA_DEVOLUCION)
            }else{
                put(PrestamoContrato.COLUMNA_FECHA_DEVOLUCION, System.currentTimeMillis())
            }
            put(PrestamoContrato.COLUMNA_SYNCED, 0)
        }
        val filas = dbHelper.writableDatabase.update(
            PrestamoContrato.TABLA,
            valores,
            "${PrestamoContrato.COLUMNA_ID_RESERVA} = ? AND ${PrestamoContrato.COLUMNA_ESTADO} = 'Pendiente'",
            arrayOf(idReserva.toString())
        )
        return if (filas == 1) ResultadoPrestamo.EXITO else ResultadoPrestamo.SIN_PRESTAMO_PENDIENTE
    }
    private fun existePrestamo(db: SQLiteDatabase, idReserva: Int): Boolean{
        db.query(
            PrestamoContrato.TABLA,
            arrayOf(PrestamoContrato.COLUMNA_ID),
            "${PrestamoContrato.COLUMNA_ID_RESERVA} = ?",
            arrayOf(idReserva.toString()),
            null, null, null
        ).use { cursor -> return cursor.count > 0 }
    }
    companion object{
        private val ESTADOS_CIERRE = setOf("Devuelto", "Dañado", "Perdido")
    }
}