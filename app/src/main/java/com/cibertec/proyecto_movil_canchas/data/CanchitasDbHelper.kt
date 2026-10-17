package com.cibertec.proyecto_movil_canchas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CanchitasDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE ${CanchaContrato.TABLA} (
                ${CanchaContrato.COLUMNA_ID} INTEGER PRIMARY KEY,
                ${CanchaContrato.COLUMNA_NOMBRE} TEXT NOT NULL,
                ${CanchaContrato.COLUMNA_PRECIO} REAL NOT NULL,
                ${CanchaContrato.COLUMNA_ACTIVO} INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE ${ClienteContrato.TABLA} (
                ${ClienteContrato.COLUMNA_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${ClienteContrato.COLUMNA_ID_SERVIDOR} INTEGER,
                ${ClienteContrato.COLUMNA_NOMBRE} TEXT NOT NULL,
                ${ClienteContrato.COLUMNA_DOCUMENTO} TEXT NOT NULL UNIQUE,
                ${ClienteContrato.COLUMNA_TELEFONO} TEXT,
                ${ClienteContrato.COLUMNA_TIPO} TEXT NOT NULL,
                ${ClienteContrato.COLUMNA_SYNCED} INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE ${ReservaContrato.TABLA_RESERVAS} (
                ${ReservaContrato.COLUMNA_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${ReservaContrato.COLUMNA_ID_SERVIDOR} INTEGER,
                ${ReservaContrato.COLUMNA_ID_CANCHA} INTEGER NOT NULL
                    REFERENCES ${CanchaContrato.TABLA}(${CanchaContrato.COLUMNA_ID}),
                ${ReservaContrato.COLUMNA_ID_CLIENTE} INTEGER NOT NULL
                    REFERENCES ${ClienteContrato.TABLA}(${ClienteContrato.COLUMNA_ID}),
                ${ReservaContrato.COLUMNA_ID_USUARIO} INTEGER,
                ${ReservaContrato.COLUMNA_HORA_INICIO} INTEGER NOT NULL,
                ${ReservaContrato.COLUMNA_HORA_FIN} INTEGER NOT NULL,
                ${ReservaContrato.COLUMNA_PRECIO} REAL NOT NULL,
                ${ReservaContrato.COLUMNA_MONTO_RECIBIDO} REAL NOT NULL DEFAULT 0,
                ${ReservaContrato.COLUMNA_ESTADO} TEXT NOT NULL DEFAULT 'Pendiente',
                ${ReservaContrato.COLUMNA_SYNCED} INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE ${PrestamoContrato.TABLA} (
                ${PrestamoContrato.COLUMNA_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${PrestamoContrato.COLUMNA_ID_SERVIDOR} INTEGER,
                ${PrestamoContrato.COLUMNA_ID_RESERVA} INTEGER NOT NULL
                    REFERENCES ${ReservaContrato.TABLA_RESERVAS}(${ReservaContrato.COLUMNA_ID}),
                ${PrestamoContrato.COLUMNA_FECHA_ENTREGA} INTEGER NOT NULL,
                ${PrestamoContrato.COLUMNA_FECHA_DEVOLUCION} INTEGER,
                ${PrestamoContrato.COLUMNA_ESTADO} TEXT NOT NULL DEFAULT 'Pendiente',
                ${PrestamoContrato.COLUMNA_SYNCED} INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // --- TABLAS DE ACADEMIA / PROFESOR ---
        db.execSQL(
            """
            CREATE TABLE ${UsuarioProfesorContrato.TABLA} (
                ${UsuarioProfesorContrato.COLUMNA_ID} INTEGER PRIMARY KEY,
                ${UsuarioProfesorContrato.COLUMNA_NOMBRE} TEXT NOT NULL,
                ${UsuarioProfesorContrato.COLUMNA_ROL} TEXT NOT NULL,
                ${UsuarioProfesorContrato.COLUMNA_ACTIVO} INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE ${ClaseContrato.TABLA} (
                ${ClaseContrato.COLUMNA_ID} INTEGER PRIMARY KEY,
                ${ClaseContrato.COLUMNA_ID_USUARIO} INTEGER NOT NULL,
                ${ClaseContrato.COLUMNA_NOMBRE_PROFESOR} TEXT NOT NULL,
                ${ClaseContrato.COLUMNA_ID_CANCHA} INTEGER NOT NULL,
                ${ClaseContrato.COLUMNA_NOMBRE_CANCHA} TEXT NOT NULL,
                ${ClaseContrato.COLUMNA_FECHA} TEXT NOT NULL,
                ${ClaseContrato.COLUMNA_HORA_INICIO} TEXT NOT NULL,
                ${ClaseContrato.COLUMNA_HORA_FIN} TEXT NOT NULL,
                ${ClaseContrato.COLUMNA_ESTADO} TEXT NOT NULL DEFAULT 'Programada',
                ${ClaseContrato.COLUMNA_ACTIVO} INTEGER NOT NULL DEFAULT 1,
                ${ClaseContrato.COLUMNA_SYNCED} INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE ${AlumnoContrato.TABLA} (
                ${AlumnoContrato.COLUMNA_ID} INTEGER PRIMARY KEY,
                ${AlumnoContrato.COLUMNA_NOMBRE} TEXT NOT NULL,
                ${AlumnoContrato.COLUMNA_TELEFONO_APODERADO} TEXT,
                ${AlumnoContrato.COLUMNA_ACTIVO} INTEGER NOT NULL DEFAULT 1,
                ${AlumnoContrato.COLUMNA_ID_USUARIO} INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE ${AsistenciaContrato.TABLA} (
                ${AsistenciaContrato.COLUMNA_ID_LOCAL} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${AsistenciaContrato.COLUMNA_ID_SERVIDOR} INTEGER NOT NULL DEFAULT 0,
                ${AsistenciaContrato.COLUMNA_ID_CLASE} INTEGER NOT NULL,
                ${AsistenciaContrato.COLUMNA_ID_ALUMNO} INTEGER NOT NULL,
                ${AsistenciaContrato.COLUMNA_ESTADO} TEXT NOT NULL DEFAULT 'Presente',
                ${AsistenciaContrato.COLUMNA_SYNCED} INTEGER NOT NULL DEFAULT 0,
                UNIQUE(${AsistenciaContrato.COLUMNA_ID_CLASE}, ${AsistenciaContrato.COLUMNA_ID_ALUMNO})
            )
            """.trimIndent()
        )
    }
     //borrando datos locales
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${AsistenciaContrato.TABLA}")
        db.execSQL("DROP TABLE IF EXISTS ${AlumnoContrato.TABLA}")
        db.execSQL("DROP TABLE IF EXISTS ${ClaseContrato.TABLA}")
        db.execSQL("DROP TABLE IF EXISTS ${UsuarioProfesorContrato.TABLA}")
        db.execSQL("DROP TABLE IF EXISTS ${PrestamoContrato.TABLA}")
        db.execSQL("DROP TABLE IF EXISTS ${ReservaContrato.TABLA_RESERVAS}")
        db.execSQL("DROP TABLE IF EXISTS ${ClienteContrato.TABLA}")
        db.execSQL("DROP TABLE IF EXISTS ${CanchaContrato.TABLA}")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "canchitas_local.db"
        private const val DATABASE_VERSION = 5

        @Volatile
        private var instancia: CanchitasDbHelper? = null

        fun getInstance(context: Context): CanchitasDbHelper =
            instancia ?: synchronized(this) {
                instancia ?: CanchitasDbHelper(context.applicationContext).also { instancia = it }
            }
    }
}