package com.cibertec.proyecto_movil_canchas.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.cibertec.proyecto_movil_canchas.model.AlumnoAsistenciaItem
import com.cibertec.proyecto_movil_canchas.model.ClaseAcademia
import com.cibertec.proyecto_movil_canchas.model.FiltroEstadoClase
import com.cibertec.proyecto_movil_canchas.model.ProfesorItem
import com.cibertec.proyecto_movil_canchas.network.AsistenciaDTO
import com.cibertec.proyecto_movil_canchas.network.ClaseDTO
import com.cibertec.proyecto_movil_canchas.network.RetrofitClient

class AcademiaStore(context: Context) {

    private val dbHelper = CanchitasDbHelper.getInstance(context)

    fun obtenerProfesoresLocales(): List<ProfesorItem> {
        val db = dbHelper.readableDatabase
        val lista = mutableListOf<ProfesorItem>()
        db.query(
            UsuarioProfesorContrato.TABLA,
            arrayOf(UsuarioProfesorContrato.COLUMNA_ID, UsuarioProfesorContrato.COLUMNA_NOMBRE),
            "${UsuarioProfesorContrato.COLUMNA_ACTIVO} = 1",
            null, null, null,
            "${UsuarioProfesorContrato.COLUMNA_NOMBRE} ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                lista.add(
                    ProfesorItem(
                        idUsuario = cursor.getInt(0),
                        nombre = cursor.getString(1)
                    )
                )
            }
        }
        return lista
    }

    fun obtenerClases(
        idProfesorFiltro: Int? = null,
        filtroEstado: FiltroEstadoClase = FiltroEstadoClase.TODAS
    ): List<ClaseAcademia> {
        val db = dbHelper.readableDatabase
        val condiciones = mutableListOf("${ClaseContrato.COLUMNA_ACTIVO} = 1")
        val args = mutableListOf<String>()

        if (idProfesorFiltro != null && idProfesorFiltro > 0) {
            condiciones.add("${ClaseContrato.COLUMNA_ID_USUARIO} = ?")
            args.add(idProfesorFiltro.toString())
        }

        when (filtroEstado) {
            FiltroEstadoClase.PROGRAMADAS -> {
                condiciones.add("${ClaseContrato.COLUMNA_ESTADO} IN ('Programada', 'En curso')")
            }
            FiltroEstadoClase.COMPLETADAS -> {
                condiciones.add("${ClaseContrato.COLUMNA_ESTADO} = 'Completada'")
            }
            FiltroEstadoClase.CANCELADAS -> {
                condiciones.add("${ClaseContrato.COLUMNA_ESTADO} = 'Cancelada'")
            }
            FiltroEstadoClase.TODAS -> {}
        }

        val sql = """
            SELECT 
                c.${ClaseContrato.COLUMNA_ID},
                c.${ClaseContrato.COLUMNA_ID_USUARIO},
                c.${ClaseContrato.COLUMNA_NOMBRE_PROFESOR},
                c.${ClaseContrato.COLUMNA_ID_CANCHA},
                c.${ClaseContrato.COLUMNA_NOMBRE_CANCHA},
                c.${ClaseContrato.COLUMNA_FECHA},
                c.${ClaseContrato.COLUMNA_HORA_INICIO},
                c.${ClaseContrato.COLUMNA_HORA_FIN},
                c.${ClaseContrato.COLUMNA_ESTADO},
                c.${ClaseContrato.COLUMNA_ACTIVO},
                c.${ClaseContrato.COLUMNA_SYNCED},
                (SELECT COUNT(*) FROM ${AlumnoContrato.TABLA} a 
                 WHERE a.${AlumnoContrato.COLUMNA_ID_USUARIO} = c.${ClaseContrato.COLUMNA_ID_USUARIO} 
                   AND a.${AlumnoContrato.COLUMNA_ACTIVO} = 1) AS total_alumnos,
                (SELECT COUNT(*) FROM ${AsistenciaContrato.TABLA} s 
                 WHERE s.${AsistenciaContrato.COLUMNA_ID_CLASE} = c.${ClaseContrato.COLUMNA_ID}) AS total_asistencias
            FROM ${ClaseContrato.TABLA} c
            WHERE ${condiciones.joinToString(" AND ")}
            ORDER BY c.${ClaseContrato.COLUMNA_FECHA} DESC, c.${ClaseContrato.COLUMNA_HORA_INICIO} ASC
        """.trimIndent()

        val resultado = mutableListOf<ClaseAcademia>()
        db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                resultado.add(
                    ClaseAcademia(
                        idClase = cursor.getInt(0),
                        idUsuario = cursor.getInt(1),
                        nombreProfesor = cursor.getString(2),
                        idCancha = cursor.getInt(3),
                        nombreCancha = cursor.getString(4),
                        fechaClase = cursor.getString(5),
                        horaInicio = cursor.getString(6),
                        horaFin = cursor.getString(7),
                        estadoClase = cursor.getString(8),
                        activoClase = cursor.getInt(9) == 1,
                        synced = cursor.getInt(10) == 1,
                        totalAlumnos = cursor.getInt(11),
                        totalAsistenciasMarcadas = cursor.getInt(12)
                    )
                )
            }
        }
        return resultado
    }

    fun obtenerAlumnosParaAsistencia(idClase: Int, idProfesor: Int): List<AlumnoAsistenciaItem> {
        val db = dbHelper.readableDatabase
        val sql = """
            SELECT 
                a.${AlumnoContrato.COLUMNA_ID},
                a.${AlumnoContrato.COLUMNA_NOMBRE},
                a.${AlumnoContrato.COLUMNA_TELEFONO_APODERADO},
                COALESCE(s.${AsistenciaContrato.COLUMNA_ID_SERVIDOR}, 0),
                COALESCE(s.${AsistenciaContrato.COLUMNA_ESTADO}, 'Presente')
            FROM ${AlumnoContrato.TABLA} a
            LEFT JOIN ${AsistenciaContrato.TABLA} s
                ON s.${AsistenciaContrato.COLUMNA_ID_ALUMNO} = a.${AlumnoContrato.COLUMNA_ID}
               AND s.${AsistenciaContrato.COLUMNA_ID_CLASE} = ?
            WHERE a.${AlumnoContrato.COLUMNA_ID_USUARIO} = ?
              AND a.${AlumnoContrato.COLUMNA_ACTIVO} = 1
            ORDER BY a.${AlumnoContrato.COLUMNA_NOMBRE} ASC
        """.trimIndent()

        val lista = mutableListOf<AlumnoAsistenciaItem>()
        db.rawQuery(sql, arrayOf(idClase.toString(), idProfesor.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                lista.add(
                    AlumnoAsistenciaItem(
                        idAlumno = cursor.getInt(0),
                        nombreAlumno = cursor.getString(1),
                        telefonoApoderado = cursor.getString(2),
                        idAsistenciaServidor = cursor.getInt(3),
                        estadoAsistencia = cursor.getString(4)
                    )
                )
            }
        }
        return lista
    }

    fun guardarAsistenciasLocal(
        idClase: Int,
        alumnos: List<AlumnoAsistenciaItem>,
        marcarCompletada: Boolean
    ): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            for (item in alumnos) {
                val valores = ContentValues().apply {
                    put(AsistenciaContrato.COLUMNA_ID_SERVIDOR, item.idAsistenciaServidor)
                    put(AsistenciaContrato.COLUMNA_ID_CLASE, idClase)
                    put(AsistenciaContrato.COLUMNA_ID_ALUMNO, item.idAlumno)
                    put(AsistenciaContrato.COLUMNA_ESTADO, item.estadoAsistencia)
                    put(AsistenciaContrato.COLUMNA_SYNCED, 0)
                }

                val filas = db.update(
                    AsistenciaContrato.TABLA,
                    valores,
                    "${AsistenciaContrato.COLUMNA_ID_CLASE} = ? AND ${AsistenciaContrato.COLUMNA_ID_ALUMNO} = ?",
                    arrayOf(idClase.toString(), item.idAlumno.toString())
                )
                if (filas == 0) {
                    db.insertWithOnConflict(
                        AsistenciaContrato.TABLA,
                        null,
                        valores,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            }

            if (marcarCompletada) {
                val valClase = ContentValues().apply {
                    put(ClaseContrato.COLUMNA_ESTADO, "Completada")
                    put(ClaseContrato.COLUMNA_SYNCED, 0)
                }
                db.update(
                    ClaseContrato.TABLA,
                    valClase,
                    "${ClaseContrato.COLUMNA_ID} = ?",
                    arrayOf(idClase.toString())
                )
            }

            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            false
        } finally {
            db.endTransaction()
        }
    }

    fun sincronizarConWeb(): Result<Int> = runCatching {
        val api = RetrofitClient.api

        // 1. Subir asistencias y clases pendientes del celular a Azure
        subirPendientesAAzure()

        // 2. Descargar listas desde Azure
        val respClases = api.obtenerClases().execute()
        if (!respClases.isSuccessful) {
            throw IllegalStateException("Error al obtener clases de Azure (${respClases.code()})")
        }
        val clasesRemotas = respClases.body().orEmpty()
        val usuariosRemotos = api.obtenerUsuarios().execute().body().orEmpty()
        val canchasRemotas = api.obtenerCanchas().execute().body().orEmpty()
        val alumnosRemotos = api.obtenerAlumnos().execute().body().orEmpty()
        val asistenciasRemotas = api.obtenerAsistencias().execute().body().orEmpty()

        val mapaProfesores = usuariosRemotos.associateBy({ it.idUsuario }, { it.nombreUsuario })
        val mapaCanchas = canchasRemotas.associateBy({ it.idCancha }, { it.nombreCancha })

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (cancha in canchasRemotas) {
                val cvCancha = ContentValues().apply {
                    put(CanchaContrato.COLUMNA_ID, cancha.idCancha)
                    put(CanchaContrato.COLUMNA_NOMBRE, cancha.nombreCancha)
                    put(CanchaContrato.COLUMNA_PRECIO, cancha.precioAlquiler)
                    put(CanchaContrato.COLUMNA_ACTIVO, if (cancha.activoCancha) 1 else 0)
                }
                val actualizadas = db.update(
                    CanchaContrato.TABLA,
                    cvCancha,
                    "${CanchaContrato.COLUMNA_ID} = ?",
                    arrayOf(cancha.idCancha.toString())
                )
                if (actualizadas == 0) {
                    db.insert(CanchaContrato.TABLA, null, cvCancha)
                }
            }

            db.delete(UsuarioProfesorContrato.TABLA, null, null)
            for (u in usuariosRemotos.filter {
                it.rol.equals("Profesor", ignoreCase = true) || it.rol.equals("Entrenador", ignoreCase = true)
            }) {
                val cvProf = ContentValues().apply {
                    put(UsuarioProfesorContrato.COLUMNA_ID, u.idUsuario)
                    put(UsuarioProfesorContrato.COLUMNA_NOMBRE, u.nombreUsuario)
                    put(UsuarioProfesorContrato.COLUMNA_ROL, u.rol)
                    put(UsuarioProfesorContrato.COLUMNA_ACTIVO, if (u.activoUsuario) 1 else 0)
                }
                db.insertWithOnConflict(
                    UsuarioProfesorContrato.TABLA,
                    null,
                    cvProf,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            db.delete(AlumnoContrato.TABLA, null, null)
            for (al in alumnosRemotos) {
                val cvAlumno = ContentValues().apply {
                    put(AlumnoContrato.COLUMNA_ID, al.idAlumno)
                    put(AlumnoContrato.COLUMNA_NOMBRE, al.nombreAlumno)
                    put(AlumnoContrato.COLUMNA_TELEFONO_APODERADO, al.telefonoApoderado)
                    put(AlumnoContrato.COLUMNA_ACTIVO, if (al.activoAlumno) 1 else 0)
                    put(AlumnoContrato.COLUMNA_ID_USUARIO, al.idUsuario)
                }
                db.insertWithOnConflict(
                    AlumnoContrato.TABLA,
                    null,
                    cvAlumno,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            db.delete(ClaseContrato.TABLA, "${ClaseContrato.COLUMNA_SYNCED} = 1", null)
            for (cl in clasesRemotas) {
                val cvClase = ContentValues().apply {
                    put(ClaseContrato.COLUMNA_ID, cl.idClase)
                    put(ClaseContrato.COLUMNA_ID_USUARIO, cl.idUsuario)
                    put(
                        ClaseContrato.COLUMNA_NOMBRE_PROFESOR,
                        mapaProfesores[cl.idUsuario] ?: "Profesor #${cl.idUsuario}"
                    )
                    put(ClaseContrato.COLUMNA_ID_CANCHA, cl.idCancha)
                    put(
                        ClaseContrato.COLUMNA_NOMBRE_CANCHA,
                        mapaCanchas[cl.idCancha] ?: "Cancha #${cl.idCancha}"
                    )
                    put(ClaseContrato.COLUMNA_FECHA, cl.fechaClase.take(10))
                    put(ClaseContrato.COLUMNA_HORA_INICIO, cl.horaInicio.take(5))
                    put(ClaseContrato.COLUMNA_HORA_FIN, cl.horaFin.take(5))
                    put(ClaseContrato.COLUMNA_ESTADO, cl.estadoClase)
                    put(ClaseContrato.COLUMNA_ACTIVO, if (cl.activoClase) 1 else 0)
                    put(ClaseContrato.COLUMNA_SYNCED, 1)
                }
                db.insertWithOnConflict(
                    ClaseContrato.TABLA,
                    null,
                    cvClase,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            db.delete(AsistenciaContrato.TABLA, "${AsistenciaContrato.COLUMNA_SYNCED} = 1", null)
            for (asist in asistenciasRemotas) {
                val cvAsist = ContentValues().apply {
                    put(AsistenciaContrato.COLUMNA_ID_SERVIDOR, asist.idAsistencia)
                    put(AsistenciaContrato.COLUMNA_ID_CLASE, asist.idClase)
                    put(AsistenciaContrato.COLUMNA_ID_ALUMNO, asist.idAlumno)
                    put(AsistenciaContrato.COLUMNA_ESTADO, asist.estadoAsistencia)
                    put(AsistenciaContrato.COLUMNA_SYNCED, 1)
                }
                db.insertWithOnConflict(
                    AsistenciaContrato.TABLA,
                    null,
                    cvAsist,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        clasesRemotas.size
    }

    fun subirPendientesAAzure() {
        val api = RetrofitClient.api
        val db = dbHelper.writableDatabase

        val asistenciasRemotas = runCatching {
            api.obtenerAsistencias().execute().body().orEmpty()
        }.getOrDefault(emptyList())

        val pendientesAsist = mutableListOf<Triple<Int, Int, AsistenciaDTO>>()
        db.query(
            AsistenciaContrato.TABLA,
            arrayOf(
                AsistenciaContrato.COLUMNA_ID_LOCAL,
                AsistenciaContrato.COLUMNA_ID_SERVIDOR,
                AsistenciaContrato.COLUMNA_ID_CLASE,
                AsistenciaContrato.COLUMNA_ID_ALUMNO,
                AsistenciaContrato.COLUMNA_ESTADO
            ),
            "${AsistenciaContrato.COLUMNA_SYNCED} = 0",
            null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                pendientesAsist.add(
                    Triple(
                        cursor.getInt(0),
                        cursor.getInt(1),
                        AsistenciaDTO(
                            idAsistencia = cursor.getInt(1),
                            idClase = cursor.getInt(2),
                            idAlumno = cursor.getInt(3),
                            estadoAsistencia = cursor.getString(4)
                        )
                    )
                )
            }
        }

        for ((idLocal, idServGuardado, dto) in pendientesAsist) {
            val existenteRemoto = asistenciasRemotas.firstOrNull {
                it.idClase == dto.idClase && it.idAlumno == dto.idAlumno
            }
            val idReal = existenteRemoto?.idAsistencia ?: idServGuardado

            val respuesta = if (idReal > 0) {
                api.actualizarAsistencia(dto.copy(idAsistencia = idReal)).execute()
            } else {
                api.registrarAsistencia(dto.copy(idAsistencia = 0)).execute()
            }

            if (respuesta.isSuccessful) {
                val cv = ContentValues().apply {
                    put(AsistenciaContrato.COLUMNA_SYNCED, 1)
                }
                db.update(
                    AsistenciaContrato.TABLA,
                    cv,
                    "${AsistenciaContrato.COLUMNA_ID_LOCAL} = ?",
                    arrayOf(idLocal.toString())
                )
            }
        }

        val clasesPendientes = mutableListOf<ClaseDTO>()
        db.query(
            ClaseContrato.TABLA,
            arrayOf(
                ClaseContrato.COLUMNA_ID,
                ClaseContrato.COLUMNA_ID_USUARIO,
                ClaseContrato.COLUMNA_ID_CANCHA,
                ClaseContrato.COLUMNA_FECHA,
                ClaseContrato.COLUMNA_HORA_INICIO,
                ClaseContrato.COLUMNA_HORA_FIN,
                ClaseContrato.COLUMNA_ESTADO,
                ClaseContrato.COLUMNA_ACTIVO
            ),
            "${ClaseContrato.COLUMNA_SYNCED} = 0",
            null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val hIni = cursor.getString(4)
                val hFin = cursor.getString(5)
                clasesPendientes.add(
                    ClaseDTO(
                        idClase = cursor.getInt(0),
                        idUsuario = cursor.getInt(1),
                        idCancha = cursor.getInt(2),
                        fechaClase = cursor.getString(3),
                        horaInicio = if (hIni.length == 5) "$hIni:00" else hIni,
                        horaFin = if (hFin.length == 5) "$hFin:00" else hFin,
                        estadoClase = cursor.getString(6),
                        activoClase = cursor.getInt(7) == 1
                    )
                )
            }
        }

        for (claseDto in clasesPendientes) {
            val resp = api.actualizarClase(claseDto).execute()
            if (resp.isSuccessful) {
                val cv = ContentValues().apply {
                    put(ClaseContrato.COLUMNA_SYNCED, 1)
                }
                db.update(
                    ClaseContrato.TABLA,
                    cv,
                    "${ClaseContrato.COLUMNA_ID} = ?",
                    arrayOf(claseDto.idClase.toString())
                )
            }
        }
    }
}