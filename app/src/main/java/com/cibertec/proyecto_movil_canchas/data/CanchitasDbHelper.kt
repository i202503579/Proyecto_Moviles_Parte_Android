package com.cibertec.proyecto_movil_canchas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CanchitasDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    ReservaContrato.NOMBRE_BASE_DATOS,
    null,
    ReservaContrato.VERSION_BASE_DATOS
) {

    override fun onCreate(db: SQLiteDatabase) {

        db.execSQL(ReservaContrato.SQL_CREAR_TABLA)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }
}