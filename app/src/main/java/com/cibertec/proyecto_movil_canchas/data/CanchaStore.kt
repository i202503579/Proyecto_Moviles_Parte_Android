package com.cibertec.proyecto_movil_canchas.data

import android.content.Context
import com.cibertec.proyecto_movil_canchas.model.Cancha

class CanchaStore(context: Context) {
    private val dbHelper = CanchitasDbHelper.getInstance(context)

    fun obtenerActivas(): List<Cancha>{
        val canchas = mutableListOf<Cancha>()
        dbHelper.readableDatabase.query(
            CanchaContrato.TABLA,
            arrayOf(CanchaContrato.COLUMNA_ID, CanchaContrato.COLUMNA_NOMBRE, CanchaContrato.COLUMNA_PRECIO),
            "${CanchaContrato.COLUMNA_ACTIVO} = 1",
            null, null, null,
            "${CanchaContrato.COLUMNA_NOMBRE} ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                canchas.add(
                    Cancha(
                        id = cursor.getInt(0),
                        nombre = cursor.getString(1),
                        precioAlquiler = cursor.getDouble(2)
                    )
                )
            }
        }
        return canchas
    }
}