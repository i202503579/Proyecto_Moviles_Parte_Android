@file:Suppress("UseSQLiteDatabaseTransaction")

package com.cibertec.proyecto_movil_canchas.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.cibertec.proyecto_movil_canchas.model.Cancha
import com.cibertec.proyecto_movil_canchas.model.Cliente
import com.cibertec.proyecto_movil_canchas.model.ResultadoPago
import com.cibertec.proyecto_movil_canchas.model.ResultadoReserva
import com.cibertec.proyecto_movil_canchas.network.ClienteDTO
import com.cibertec.proyecto_movil_canchas.network.PrestamoBalonDTO
import com.cibertec.proyecto_movil_canchas.network.ReservaDTO
import com.cibertec.proyecto_movil_canchas.network.RetrofitClient
import java.text.SimpleDateFormat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.Locale

class SyncRepository(context: Context) {

    private val dbHelper = CanchitasDbHelper.getInstance(context)
    private val reservaStore = ReservaStore(context)
    private val canchaStore = CanchaStore(context)
    private val clienteStore = ClienteStore(context)

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    // ==========================================
    // 1. SINCRONIZAR CANCHAS DESDE AZURE
    // ==========================================
    suspend fun sincronizarCanchas(): List<Cancha> {
        try {
            val canchasApi = RetrofitClient.apiService.getCanchas()
            val db = dbHelper.writableDatabase

            db.beginTransaction()
            try {
                for (c in canchasApi) {
                    val valores = ContentValues().apply {
                        put(CanchaContrato.COLUMNA_ID, c.idCancha)
                        put(CanchaContrato.COLUMNA_NOMBRE, c.nombreCancha)
                        put(CanchaContrato.COLUMNA_PRECIO, c.precioAlquiler)
                        put(CanchaContrato.COLUMNA_ACTIVO, if (c.activoCancha) 1 else 0)
                    }
                    db.insertWithOnConflict(
                        CanchaContrato.TABLA,
                        null,
                        valores,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Modo offline para canchas: ${e.message}")
        }
        return canchaStore.obtenerActivas()
    }

    // ==========================================
    // 2. SINCRONIZAR CLIENTES DESDE AZURE
    // ==========================================
    suspend fun sincronizarClientes() {
        try {
            val clientesApi = RetrofitClient.apiService.getClientes()
            val db = dbHelper.writableDatabase

            db.beginTransaction()
            try {
                for (c in clientesApi) {
                    val valores = ContentValues().apply {
                        put(ClienteContrato.COLUMNA_ID_SERVIDOR, c.idCliente)
                        put(ClienteContrato.COLUMNA_NOMBRE, c.nombreCliente)
                        put(ClienteContrato.COLUMNA_DOCUMENTO, c.numeroDocumento)
                        put(ClienteContrato.COLUMNA_TELEFONO, c.telefonoCliente)
                        put(ClienteContrato.COLUMNA_TIPO, c.tipoCliente)
                        put(ClienteContrato.COLUMNA_SYNCED, 1)
                    }
                    val actualizados = db.update(
                        ClienteContrato.TABLA,
                        valores,
                        "${ClienteContrato.COLUMNA_DOCUMENTO} = ?",
                        arrayOf(c.numeroDocumento)
                    )
                    if (actualizados == 0) {
                        db.insert(ClienteContrato.TABLA, null, valores)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error sincronizando clientes: ${e.message}")
        }
    }

    // ==========================================
    // 3. BUSCAR CLIENTE (LOCAL + AZURE)
    // ==========================================
    suspend fun buscarCliente(documento: String): Cliente? {
        val local = clienteStore.buscarPorDocumento(documento)
        if (local != null) return local

        return try {
            val lista = RetrofitClient.apiService.getClientes()
            val remoto = lista.find { it.numeroDocumento == documento } ?: return null

            val db = dbHelper.writableDatabase
            val valores = ContentValues().apply {
                put(ClienteContrato.COLUMNA_ID_SERVIDOR, remoto.idCliente)
                put(ClienteContrato.COLUMNA_NOMBRE, remoto.nombreCliente)
                put(ClienteContrato.COLUMNA_DOCUMENTO, remoto.numeroDocumento)
                put(ClienteContrato.COLUMNA_TELEFONO, remoto.telefonoCliente)
                put(ClienteContrato.COLUMNA_TIPO, remoto.tipoCliente)
                put(ClienteContrato.COLUMNA_SYNCED, 1)
            }
            val idNuevo = db.insert(ClienteContrato.TABLA, null, valores)

            Cliente(
                id = idNuevo.toInt(),
                nombre = remoto.nombreCliente,
                numeroDocumento = remoto.numeroDocumento,
                telefono = remoto.telefonoCliente,
                tipoCliente = remoto.tipoCliente
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error buscando cliente en Azure: ${e.message}")
            null
        }
    }

    // ==========================================
    // 4. SINCRONIZAR RESERVAS DESDE AZURE
    // ==========================================
    private fun obtenerReservasOcultas(db: SQLiteDatabase): Set<Int> {
        val ids = mutableSetOf<Int>()
        db.rawQuery("SELECT id_servidor FROM reservas_ocultas", null).use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getInt(0))
        }
        return ids
    }
    suspend fun eliminarReservaGlobal(idReservaLocal: Int): Boolean {
        return try {
            val idServidor = obtenerIdReservaServidor(idReservaLocal)

            if (idServidor != null && idServidor > 0) {
                // 1. Borrar primero los préstamos de esa reserva en Azure
                val prestamos = RetrofitClient.apiService.getPrestamos()
                    .filter { it.idReserva == idServidor }

                for (p in prestamos) {
                    val r = RetrofitClient.apiService.deletePrestamo(p.idPrestamo)
                    if (!r.isSuccessful && esFalloReal(r, "DELETE préstamo ${p.idPrestamo}")) return false
                }

                // 2. Borrar la reserva
                val resp = RetrofitClient.apiService.deleteReserva(idServidor)
                if (!resp.isSuccessful && esFalloReal(resp, "DELETE reserva $idServidor")) return false
            }

            // 3. Borrar local (préstamo y reserva)
            reservaStore.eliminacionFisicamenteLocal(idReservaLocal)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando reserva globalmente: ${e.message}", e)
            false
        }
    }

    // true = error de verdad; false = el registro ya no existía en Azure (se considera ok)
    private fun esFalloReal(resp: retrofit2.Response<*>, contexto: String): Boolean {
        val cuerpo = resp.errorBody()?.string().orEmpty()
        if (resp.code() == 400 && cuerpo.contains("no existente", ignoreCase = true)) return false
        Log.e(TAG, "$contexto: HTTP ${resp.code()} - $cuerpo")
        return true
    }
    suspend fun sincronizarReservas(): Boolean {
        return try {
            sincronizarReservasPendientes()
            sincronizarClientesPendientes()
            sincronizarCanchas()
            sincronizarClientes()

            val reservasApi = RetrofitClient.apiService.getReservas()
            val db = dbHelper.writableDatabase

            db.beginTransaction()
            try {
                //val ocultas = obtenerReservasOcultas(db)
                for (r in reservasApi) {
                  //  if (r.idReserva in ocultas) continue
                    val inicioMillis = try { isoFormat.parse(r.horaInicio)?.time ?: 0L } catch (_: Exception) { 0L }
                    val finMillis = try { isoFormat.parse(r.horaFin)?.time ?: 0L } catch (_: Exception) { 0L }

                    val idClienteLocal = resolverClienteLocalPorServidor(db, r.idCliente) ?: continue

                    val valores = ContentValues().apply {
                        put(ReservaContrato.COLUMNA_ID_SERVIDOR, r.idReserva)
                        put(ReservaContrato.COLUMNA_ID_CANCHA, r.idCancha)
                        put(ReservaContrato.COLUMNA_ID_CLIENTE, idClienteLocal)
                        put(ReservaContrato.COLUMNA_ID_USUARIO, r.idUsuario)
                        put(ReservaContrato.COLUMNA_HORA_INICIO, inicioMillis)
                        put(ReservaContrato.COLUMNA_HORA_FIN, finMillis)
                        put(ReservaContrato.COLUMNA_PRECIO, r.precioReserva)
                        put(ReservaContrato.COLUMNA_MONTO_RECIBIDO, r.montoRecibido)
                        put(ReservaContrato.COLUMNA_ESTADO, r.estadoReserva)
                        put(ReservaContrato.COLUMNA_SYNCED, 1)
                    }

                    // 1. Intentar actualizar por id_servidor
                    var filas = db.update(
                        ReservaContrato.TABLA_RESERVAS,
                        valores,
                        "${ReservaContrato.COLUMNA_ID_SERVIDOR} = ?",
                        arrayOf(r.idReserva.toString())
                    )

                    // 2. Si no existía por id_servidor, vincular a reserva local que tenga la misma cancha y hora de inicio
                    if (filas == 0) {
                        filas = db.update(
                            ReservaContrato.TABLA_RESERVAS,
                            valores,
                            "${ReservaContrato.COLUMNA_ID_CANCHA} = ? AND ABS(${ReservaContrato.COLUMNA_HORA_INICIO} - ?) < 60000",
                            arrayOf(r.idCancha.toString(), inicioMillis.toString())
                        )
                    }

                    // 3. Solo si no existe ni por servidor ni por horario local, insertar
                    if (filas == 0) {
                        db.insert(ReservaContrato.TABLA_RESERVAS, null, valores)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            try {
                sincronizarPrestamosPendientes()
                sincronizarPrestamosDesdeAzure()
            } catch (e: Exception) {
                Log.w(TAG, "Error sincronizando préstamos: ${e.message}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando reservas desde Azure: ${e.message}", e)
            false
        }
    }

    // ==========================================
    // 5. REGISTRAR Y ENVIAR RESERVA A AZURE
    // ==========================================
    suspend fun registrarYEnviarReserva(
        cancha: Cancha,
        cliente: Cliente,
        horaInicioMillis: Long,
        duracionHoras: Int = 1
    ): Pair<ResultadoReserva, Boolean> =
        reservaMutex.withLock {
            registrarYEnviarReservaInterno(cancha, cliente, horaInicioMillis, duracionHoras)
        }

    suspend fun registrarYEnviarReservaInterno(
        cancha: Cancha,
        cliente: Cliente,
        horaInicioMillis: Long,
        duracionHoras: Int = 1
    ): Pair<ResultadoReserva, Boolean> {
        val resultadoLocal = reservaStore.agregar(cancha, cliente, horaInicioMillis, duracionHoras)
        if (resultadoLocal !is ResultadoReserva.EXITO) {
            return Pair(resultadoLocal, false)
        }

        val idReservaLocal = resultadoLocal.idReserva
        val horaFinMillis = horaInicioMillis + (duracionHoras * 3_600_000L)
        val precioTotal = cancha.precioAlquiler * duracionHoras

        var enviadaAzure = false
        try {
            val idClienteServidor = asegurarClienteEnServidor(cliente)

            val reservaDto = ReservaDTO(
                idReserva = 0,
                horaInicio = isoFormat.format(Date(horaInicioMillis)),
                horaFin = isoFormat.format(Date(horaFinMillis)),
                precioReserva = precioTotal,
                estadoReserva = "Pendiente",
                montoRecibido = 0.0,
                idCliente = idClienteServidor,
                idCancha = cancha.id,
                idUsuario = 1
            )

            val resp = RetrofitClient.apiService.postReserva(reservaDto)

            if (resp.isSuccessful) {
                val reservaAzure = resp.body()
                val idAsignado = reservaAzure?.idReserva

                if (idAsignado != null && idAsignado > 0) {
                    marcarReservaSincronizada(idReservaLocal, idAsignado)
                    enviadaAzure = true
                } else {
                    Log.e(TAG, "Azure creó la reserva, pero no devolvió un IdReserva válido")
                    marcarReservaSincronizada(idReservaLocal, null)
                }
            } else {
                Log.e(
                    TAG,
                    "Error enviando reserva: HTTP ${resp.code()} - ${resp.errorBody()?.string()}"
                )
                marcarReservaSincronizada(idReservaLocal, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando reserva a Azure: ${e.message}", e)
            enviadaAzure = false
        }

        return Pair(resultadoLocal, enviadaAzure)
    }

    // ==========================================
    // 6. OPERACIONES UNIFICADAS DE PAGO Y GESTIÓN
    // ==========================================
    suspend fun registrarPago(idReservaLocal: Int, monto: Double): ResultadoPago {
        val resultado = reservaStore.registrarPago(idReservaLocal, monto)
        if (resultado == ResultadoPago.EXITO) {
            sincronizarActualizacionReserva(idReservaLocal)
        }
        return resultado
    }

    suspend fun reversarPago(idReservaLocal: Int, monto: Double): ResultadoPago {
        val resultado = reservaStore.reversarPago(idReservaLocal, monto)
        if (resultado == ResultadoPago.EXITO) {
            sincronizarActualizacionReserva(idReservaLocal)
        }
        return resultado
    }

    suspend fun cancelar(idReservaLocal: Int): Int {
        val filas = reservaStore.cancelar(idReservaLocal)
        if (filas > 0) {
            sincronizarActualizacionReserva(idReservaLocal)
        }
        return filas
    }

    suspend fun actualizarCliente(idReservaLocal: Int, nuevoNombre: String): Int {
        val db = dbHelper.writableDatabase

        // 1. Obtener datos actuales del cliente vinculado a esta reserva
        var idClienteServidor = 0
        var documento = ""
        var telefono: String? = null
        var tipoCliente = "Particular"

        val sqlCliente = """
            SELECT cl.${ClienteContrato.COLUMNA_ID_SERVIDOR},
                   cl.${ClienteContrato.COLUMNA_DOCUMENTO},
                   cl.${ClienteContrato.COLUMNA_TELEFONO},
                   cl.${ClienteContrato.COLUMNA_TIPO}
            FROM ${ReservaContrato.TABLA_RESERVAS} r
            JOIN ${ClienteContrato.TABLA} cl
              ON (cl.${ClienteContrato.COLUMNA_ID} = r.${ReservaContrato.COLUMNA_ID_CLIENTE})
            WHERE r.${ReservaContrato.COLUMNA_ID} = ?
        """.trimIndent()

        db.rawQuery(sqlCliente, arrayOf(idReservaLocal.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                idClienteServidor = if (!cursor.isNull(0)) cursor.getInt(0) else 0
                documento = cursor.getString(1)
                telefono = if (!cursor.isNull(2)) cursor.getString(2) else null
                tipoCliente = cursor.getString(3)
            }
        }

        // 2. Actualizar en SQLite local
        val filas = reservaStore.actualizarCliente(idReservaLocal, nuevoNombre)

        // 3. Sincronizar el nuevo nombre con Azure
        if (filas > 0) {
            try {
                if (idClienteServidor <= 0 && documento.isNotBlank()) {
                    val listaAzure = RetrofitClient.apiService.getClientes()
                    val match = listaAzure.find { it.numeroDocumento == documento }
                    if (match != null) {
                        idClienteServidor = match.idCliente
                        val valId = ContentValues().apply {
                            put(ClienteContrato.COLUMNA_ID_SERVIDOR, idClienteServidor)
                        }
                        db.update(
                            ClienteContrato.TABLA,
                            valId,
                            "${ClienteContrato.COLUMNA_DOCUMENTO} = ?",
                            arrayOf(documento)
                        )
                    }
                }

                if (idClienteServidor > 0) {
                    val dto = ClienteDTO(
                        idCliente = idClienteServidor,
                        nombreCliente = nuevoNombre,
                        numeroDocumento = documento,
                        telefonoCliente = telefono,
                        tipoCliente = tipoCliente
                    )
                    val resp = RetrofitClient.apiService.putCliente(dto)
                    if (resp.isSuccessful) {
                        Log.i(TAG, "Cliente $idClienteServidor actualizado con éxito en Azure a: $nuevoNombre")
                        val valSync = ContentValues().apply {
                            put(ClienteContrato.COLUMNA_SYNCED, 1)
                        }
                        db.update(
                            ClienteContrato.TABLA,
                            valSync,
                            "${ClienteContrato.COLUMNA_ID_SERVIDOR} = ?",
                            arrayOf(idClienteServidor.toString())
                        )
                    } else {
                        Log.e(TAG, "Error en PUT Cliente $idClienteServidor: ${resp.errorBody()?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo actualizar el cliente en Azure (modo offline): ${e.message}")
            }

            sincronizarActualizacionReserva(idReservaLocal)
        }
        return filas
    }

    // ==========================================
    // 7. ENVIAR ACTUALIZACIÓN DE ESTADO/PAGO A AZURE (PUT)
    // ==========================================
    suspend fun sincronizarActualizacionReserva(idReservaLocal: Int): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            var idServidor = 0
            var idCancha = 1
            var idClienteLocal = 1
            var horaInicio = 0L
            var horaFin = 0L
            var precio = 0.0
            var montoRecibido = 0.0
            var estado = "Pendiente"
            var idUsuario = 1

            val sql = """
                SELECT id_servidor, id_cancha, id_cliente, hora_inicio, hora_fin, precio_reserva, monto_recibido, estado, id_usuario
                FROM ${ReservaContrato.TABLA_RESERVAS}
                WHERE ${ReservaContrato.COLUMNA_ID} = ?
            """.trimIndent()

            db.rawQuery(sql, arrayOf(idReservaLocal.toString())).use { cursor ->
                if (cursor.moveToFirst()) {
                    idServidor = if (!cursor.isNull(0)) cursor.getInt(0) else 0
                    idCancha = cursor.getInt(1)
                    idClienteLocal = cursor.getInt(2)
                    horaInicio = cursor.getLong(3)
                    horaFin = cursor.getLong(4)
                    precio = cursor.getDouble(5)
                    montoRecibido = cursor.getDouble(6)
                    estado = cursor.getString(7)
                    idUsuario = if (!cursor.isNull(8)) cursor.getInt(8) else 1
                }
            }

            // Si aún no tenía el idServidor guardado, buscarlo en Azure por horario y cancha
            if (idServidor <= 0) {
                try {
                    val listaAzure = RetrofitClient.apiService.getReservas()
                    val match = listaAzure.lastOrNull { r ->
                        r.idCancha == idCancha &&
                        Math.abs((isoFormat.parse(r.horaInicio)?.time ?: 0L) - horaInicio) < 60_000L
                    }
                    if (match != null) {
                        idServidor = match.idReserva
                        val valId = ContentValues().apply {
                            put(ReservaContrato.COLUMNA_ID_SERVIDOR, idServidor)
                        }
                        db.update(
                            ReservaContrato.TABLA_RESERVAS,
                            valId,
                            "${ReservaContrato.COLUMNA_ID} = ?",
                            arrayOf(idReservaLocal.toString())
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error buscando idServidor en Azure: ${e.message}")
                }
            }

            if (idServidor > 0) {
                val idClienteServidor = resolverClienteServidorPorLocal(db, idClienteLocal) ?: idClienteLocal
                val dto = ReservaDTO(
                    idReserva = idServidor,
                    horaInicio = isoFormat.format(Date(horaInicio)),
                    horaFin = isoFormat.format(Date(horaFin)),
                    precioReserva = precio,
                    estadoReserva = estado,
                    montoRecibido = montoRecibido,
                    idCliente = idClienteServidor,
                    idCancha = idCancha,
                    idUsuario = idUsuario
                )
                val resp = RetrofitClient.apiService.putReserva(dto)
                if (resp.isSuccessful) {
                    marcarReservaSincronizada(idReservaLocal.toLong(), idServidor)
                    Log.i(TAG, "Reserva $idServidor actualizada con éxito en Azure")
                    true
                } else {
                    Log.e(TAG, "Error en PUT Reserva $idServidor: ${resp.errorBody()?.string()}")
                    false
                }
            } else {
                Log.w(TAG, "No se pudo vincular la reserva local $idReservaLocal con Azure")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en sincronizarActualizacionReserva: ${e.message}", e)
            false
        }
    }
    private suspend fun asegurarClienteEnServidor(cliente: Cliente): Int {
        val lista = RetrofitClient.apiService.getClientes()

        val existente = lista.find {
            it.numeroDocumento == cliente.numeroDocumento
        }

        if (existente != null) {
            return existente.idCliente
        }

        val nuevoDto = ClienteDTO(
            nombreCliente = cliente.nombre,
            numeroDocumento = cliente.numeroDocumento,
            telefonoCliente = cliente.telefono,
            tipoCliente = cliente.tipoCliente
        )

        val respuesta = RetrofitClient.apiService.postCliente(nuevoDto)

        if (!respuesta.isSuccessful) {
            throw Exception("No se pudo registrar el cliente en Azure: HTTP ${respuesta.code()}")
        }

        val listaActualizada = RetrofitClient.apiService.getClientes()

        val recienCreado = listaActualizada.find {
            it.numeroDocumento == cliente.numeroDocumento
        }

        return recienCreado?.idCliente
            ?: throw Exception("El cliente se registró, pero no se encontró su ID en Azure")
    }

    private fun marcarReservaSincronizada(
        idLocal: Long,
        idServidor: Int? = null
    ) {
        val db = dbHelper.writableDatabase

        val valores = ContentValues().apply {
            if (idServidor != null && idServidor > 0) {
                put(ReservaContrato.COLUMNA_SYNCED, 1)
                put(ReservaContrato.COLUMNA_ID_SERVIDOR, idServidor)
            } else {
                put(ReservaContrato.COLUMNA_SYNCED, 0)
            }
        }

        db.update(
            ReservaContrato.TABLA_RESERVAS,
            valores,
            "${ReservaContrato.COLUMNA_ID} = ?",
            arrayOf(idLocal.toString())
        )
    }

    private fun resolverClienteLocalPorServidor(db: SQLiteDatabase, idServidor: Int): Int? {
        db.query(
            ClienteContrato.TABLA,
            arrayOf(ClienteContrato.COLUMNA_ID),
            "${ClienteContrato.COLUMNA_ID_SERVIDOR} = ?",
            arrayOf(idServidor.toString()),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        return null
    }

    private fun resolverClienteServidorPorLocal(db: SQLiteDatabase, idLocal: Int): Int? {
        db.query(
            ClienteContrato.TABLA,
            arrayOf(ClienteContrato.COLUMNA_ID_SERVIDOR),
            "${ClienteContrato.COLUMNA_ID} = ?",
            arrayOf(idLocal.toString()),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getInt(0)
            }
        }
        return null
    }

    private fun obtenerIdReservaServidor(idReservaLocal: Int): Int? {
        val db = dbHelper.readableDatabase

        db.rawQuery(
            """
        SELECT ${ReservaContrato.COLUMNA_ID_SERVIDOR}
        FROM ${ReservaContrato.TABLA_RESERVAS}
        WHERE ${ReservaContrato.COLUMNA_ID} = ?
        """.trimIndent(),
            arrayOf(idReservaLocal.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return null
            }

            return cursor.getInt(0)
        }
    }

    // --- SINCRONIZAR PRÉSTAMOS DE BALONES A AZURE ---
    suspend fun sincronizarPrestamo(idReservaLocal: Int): Boolean =
        prestamoMutex.withLock { sincronizarPrestamoInterno(idReservaLocal) }

    private suspend fun sincronizarPrestamoInterno(idReservaLocal: Int): Boolean {
        val db = dbHelper.writableDatabase

        return try {
            android.util.Log.d("SyncPrestamo", "Iniciando sincronización. Reserva local: $idReservaLocal")

            var idPrestamoServidor = 0
            var fechaEntregaStr = ""
            var fechaDevolucionStr: String? = null
            var estadoPrestamo = "Pendiente"

            // 1. Buscar el préstamo local.
            db.rawQuery(
                """
            SELECT ${PrestamoContrato.COLUMNA_ID_SERVIDOR},
                   ${PrestamoContrato.COLUMNA_FECHA_ENTREGA},
                   ${PrestamoContrato.COLUMNA_FECHA_DEVOLUCION},
                   ${PrestamoContrato.COLUMNA_ESTADO}
            FROM ${PrestamoContrato.TABLA}
            WHERE ${PrestamoContrato.COLUMNA_ID_RESERVA} = ?
            """.trimIndent(),
                arrayOf(idReservaLocal.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    android.util.Log.e("SyncPrestamo", "No existe préstamo local para reserva $idReservaLocal")
                    return false
                }

                idPrestamoServidor =
                    if (cursor.isNull(0)) 0 else cursor.getInt(0)

                fechaEntregaStr =
                    isoFormat.format(java.util.Date(cursor.getLong(1)))

                fechaDevolucionStr =
                    if (cursor.isNull(2)) null
                    else isoFormat.format(java.util.Date(cursor.getLong(2)))

                estadoPrestamo = cursor.getString(3)
            }

            // 2. Obtener el ID de la reserva en Azure (si falta, intentar vincularla primero).
            var idReservaServidor = obtenerIdReservaServidor(idReservaLocal)

            if (idReservaServidor == null || idReservaServidor <= 0) {
                Log.w("SyncPrestamo", "Reserva $idReservaLocal sin ID de Azure, intentando vincularla...")
                sincronizarActualizacionReserva(idReservaLocal)
                idReservaServidor = obtenerIdReservaServidor(idReservaLocal)
            }

            if (idReservaServidor == null || idReservaServidor <= 0) {
                Log.e("SyncPrestamo", "La reserva local $idReservaLocal sigue sin ID válido de Azure. Se reintentará luego.")
                return false
            }
            // 2b. Evitar duplicados: si Azure ya tiene un préstamo para esta reserva, vincularlo
            if (idPrestamoServidor <= 0) {
                try {
                    val existente = RetrofitClient.apiService.getPrestamos()
                        .filter { it.idReserva == idReservaServidor }
                        .minByOrNull { it.idPrestamo }
                    if (existente != null) {
                        idPrestamoServidor = existente.idPrestamo
                        Log.w(
                            "SyncPrestamo",
                            "Ya existía el préstamo $idPrestamoServidor en Azure para la reserva $idReservaServidor; se vincula en vez de crear otro"
                        )
                    }
                } catch (e: Exception) {
                    Log.w("SyncPrestamo", "No se pudo verificar duplicados: ${e.message}")
                    return false   // mejor no arriesgar un POST duplicado sin poder verificar
                }
            }
            // 3. Preparar los datos del préstamo.
            val dto = PrestamoBalonDTO(
                idPrestamo = idPrestamoServidor,
                idReserva = idReservaServidor,
                idClase = null,
                fechaEntrega = fechaEntregaStr,
                fechaDevolucion = fechaDevolucionStr,
                estadoPrestamo = estadoPrestamo
            )


            // 4. Crear o actualizar el préstamo en Azure.
            if (idPrestamoServidor <= 0) {
                val response = RetrofitClient.apiService.postPrestamo(dto)

                if (!response.isSuccessful) {
                    Log.e("SyncPrestamo", "POST rechazado. HTTP ${response.code()}: ${response.errorBody()?.string()}")
                    return false
                }

                idPrestamoServidor = response.body()?.idPrestamo ?: 0
                if (idPrestamoServidor <= 0) {
                    Log.e("SyncPrestamo", "Azure no devolvió un IdPrestamo válido")
                    return false
                }
            } else {
                val response = RetrofitClient.apiService.putPrestamo(dto)

                if (!response.isSuccessful) {
                    val error = response.errorBody()?.string()
                    android.util.Log.e(
                        "SyncPrestamo",
                        "PUT rechazado. HTTP ${response.code()}: $error"
                    )
                    return false
                }
            }


            // 5. Guardar el ID de Azure y marcar como sincronizado.
            // 5a. Guardar siempre el ID de Azure.
            val valId = ContentValues().apply {
                put(PrestamoContrato.COLUMNA_ID_SERVIDOR, idPrestamoServidor)
            }
            val filas = db.update(
                PrestamoContrato.TABLA,
                valId,
                "${PrestamoContrato.COLUMNA_ID_RESERVA} = ?",
                arrayOf(idReservaLocal.toString())
            )
            if (filas != 1) {
                android.util.Log.e(
                    "SyncPrestamo",
                    "Azure recibió el préstamo, pero SQLite actualizó $filas filas."
                )
                return false
            }

            // 5b. Marcar como sincronizado solo si el estado no cambió mientras se enviaba.
            val valSync = ContentValues().apply {
                put(PrestamoContrato.COLUMNA_SYNCED, 1)
            }
            val filasSync = db.update(
                PrestamoContrato.TABLA,
                valSync,
                "${PrestamoContrato.COLUMNA_ID_RESERVA} = ? AND ${PrestamoContrato.COLUMNA_ESTADO} = ?",
                arrayOf(idReservaLocal.toString(), estadoPrestamo)
            )
            if (filasSync == 0) {
                android.util.Log.w(
                    "SyncPrestamo",
                    "El préstamo cambió durante el envío; queda pendiente para el siguiente ciclo."
                )
                return false
            }
            true

        } catch (e: Exception) {
            android.util.Log.e(
                "SyncPrestamo",
                "Error sincronizando el préstamo de la reserva $idReservaLocal",
                e
            )
            false
        }
    }
    suspend fun sincronizarPrestamosPendientes() {
        val db = dbHelper.readableDatabase
        val pendientes = mutableListOf<Int>()

        db.rawQuery(
            """
        SELECT ${PrestamoContrato.COLUMNA_ID_RESERVA}
        FROM ${PrestamoContrato.TABLA}
        WHERE ${PrestamoContrato.COLUMNA_SYNCED} = 0
        """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                pendientes.add(cursor.getInt(0))
            }
        }

        for (idReservaLocal in pendientes) {
            sincronizarPrestamo(idReservaLocal)
        }
    }
    suspend fun sincronizarPrestamosDesdeAzure() = prestamoMutex.withLock {
        sincronizarPrestamosDesdeAzureInterno()
    }
    suspend fun sincronizarPrestamosDesdeAzureInterno() {
        try {
            val prestamosApi = RetrofitClient.apiService.getPrestamos()
            val db = dbHelper.writableDatabase

            db.beginTransaction()
            try {
                for (p in prestamosApi) {
                    // Solo los préstamos ligados a una reserva (los de clase se ignoran por ahora)
                    val idReservaServidor = p.idReserva ?: continue
                    val idReservaLocal = resolverReservaLocalPorServidor(db, idReservaServidor) ?: continue

                    val entregaMillis = try { isoFormat.parse(p.fechaEntrega)?.time ?: 0L } catch (_: Exception) { 0L }
                    val devolucionMillis = p.fechaDevolucion?.let {
                        try { isoFormat.parse(it)?.time } catch (_: Exception) { null }
                    }

                    val valores = ContentValues().apply {
                        put(PrestamoContrato.COLUMNA_ID_SERVIDOR, p.idPrestamo)
                        put(PrestamoContrato.COLUMNA_ID_RESERVA, idReservaLocal)
                        put(PrestamoContrato.COLUMNA_FECHA_ENTREGA, entregaMillis)
                        if (devolucionMillis != null) {
                            put(PrestamoContrato.COLUMNA_FECHA_DEVOLUCION, devolucionMillis)
                        } else {
                            putNull(PrestamoContrato.COLUMNA_FECHA_DEVOLUCION)
                        }
                        put(PrestamoContrato.COLUMNA_ESTADO, p.estadoPrestamo)
                        put(PrestamoContrato.COLUMNA_SYNCED, 1)
                    }

                    // 1. Actualizar por id_servidor (solo si no hay cambios locales pendientes)
                    var filas = db.update(
                        PrestamoContrato.TABLA,
                        valores,
                        "${PrestamoContrato.COLUMNA_ID_SERVIDOR} = ? AND ${PrestamoContrato.COLUMNA_SYNCED} = 1",
                        arrayOf(p.idPrestamo.toString())
                    )

                    // 2. Si ya existe localmente por id_servidor (con cambios pendientes), no tocar
                    if (filas == 0 && existePrestamoPorServidor(db, p.idPrestamo)) continue

                    // 3. Vincular un préstamo local de la misma reserva que aún no tenga id_servidor
                    if (filas == 0) {
                        filas = db.update(
                            PrestamoContrato.TABLA,
                            valores,
                            "${PrestamoContrato.COLUMNA_ID_RESERVA} = ? AND (${PrestamoContrato.COLUMNA_ID_SERVIDOR} IS NULL OR ${PrestamoContrato.COLUMNA_ID_SERVIDOR} = 0)",
                            arrayOf(idReservaLocal.toString())
                        )
                    }
                    // Si ya hay un préstamo local para esta reserva, es un duplicado de Azure: ignorar
                    if (filas == 0 && existePrestamoPorReserva(db, idReservaLocal)) {
                        Log.w(TAG, "Préstamo duplicado en Azure para reserva local $idReservaLocal, se ignora")
                        continue
                    }
                    // 4. Si no existe de ninguna forma, insertar
                    if (filas == 0) {
                        db.insert(PrestamoContrato.TABLA, null, valores)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error sincronizando préstamos desde Azure: ${e.message}")
        }
    }
    private fun existePrestamoPorReserva(db: SQLiteDatabase, idReservaLocal: Int): Boolean {
        db.query(
            PrestamoContrato.TABLA,
            arrayOf(PrestamoContrato.COLUMNA_ID_RESERVA),
            "${PrestamoContrato.COLUMNA_ID_RESERVA} = ?",
            arrayOf(idReservaLocal.toString()),
            null, null, null
        ).use { cursor -> return cursor.moveToFirst() }
    }
    private fun resolverReservaLocalPorServidor(db: SQLiteDatabase, idServidor: Int): Int? {
        db.query(
            ReservaContrato.TABLA_RESERVAS,
            arrayOf(ReservaContrato.COLUMNA_ID),
            "${ReservaContrato.COLUMNA_ID_SERVIDOR} = ?",
            arrayOf(idServidor.toString()),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0)
        }
        return null
    }

    private fun existePrestamoPorServidor(db: SQLiteDatabase, idServidor: Int): Boolean {
        db.query(
            PrestamoContrato.TABLA,
            arrayOf(PrestamoContrato.COLUMNA_ID_SERVIDOR),
            "${PrestamoContrato.COLUMNA_ID_SERVIDOR} = ?",
            arrayOf(idServidor.toString()),
            null, null, null
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }
    ///
    ////
    //// cosa por si se cae la conexion y evitar enviar la informacion de mala manera
    private class ReservaPendiente(
        val idLocal: Int,
        val idServidor: Int,
        val idCancha: Int,
        val horaInicio: Long,
        val horaFin: Long,
        val precio: Double,
        val montoRecibido: Double,
        val estado: String,
        val idUsuario: Int,
        val cliente: Cliente
    )

    private fun obtenerReservasPendientes(): List<ReservaPendiente> {
        val db = dbHelper.readableDatabase
        val lista = mutableListOf<ReservaPendiente>()

        db.rawQuery(
            """
        SELECT r.${ReservaContrato.COLUMNA_ID},
               r.${ReservaContrato.COLUMNA_ID_SERVIDOR},
               r.${ReservaContrato.COLUMNA_ID_CANCHA},
               r.${ReservaContrato.COLUMNA_HORA_INICIO},
               r.${ReservaContrato.COLUMNA_HORA_FIN},
               r.${ReservaContrato.COLUMNA_PRECIO},
               r.${ReservaContrato.COLUMNA_MONTO_RECIBIDO},
               r.${ReservaContrato.COLUMNA_ESTADO},
               r.${ReservaContrato.COLUMNA_ID_USUARIO},
               cl.${ClienteContrato.COLUMNA_ID},
               cl.${ClienteContrato.COLUMNA_NOMBRE},
               cl.${ClienteContrato.COLUMNA_DOCUMENTO},
               cl.${ClienteContrato.COLUMNA_TELEFONO},
               cl.${ClienteContrato.COLUMNA_TIPO}
        FROM ${ReservaContrato.TABLA_RESERVAS} r
        JOIN ${ClienteContrato.TABLA} cl
          ON cl.${ClienteContrato.COLUMNA_ID} = r.${ReservaContrato.COLUMNA_ID_CLIENTE}
        WHERE r.${ReservaContrato.COLUMNA_SYNCED} = 0
        """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                lista.add(
                    ReservaPendiente(
                        idLocal = c.getInt(0),
                        idServidor = if (c.isNull(1)) 0 else c.getInt(1),
                        idCancha = c.getInt(2),
                        horaInicio = c.getLong(3),
                        horaFin = c.getLong(4),
                        precio = c.getDouble(5),
                        montoRecibido = c.getDouble(6),
                        estado = c.getString(7),
                        idUsuario = if (c.isNull(8)) 1 else c.getInt(8),
                        cliente = Cliente(
                            id = c.getInt(9),
                            nombre = c.getString(10),
                            numeroDocumento = c.getString(11),
                            telefono = if (c.isNull(12)) null else c.getString(12),
                            tipoCliente = c.getString(13)
                        )
                    )
                )
            }
        }
        return lista
    }

    suspend fun sincronizarReservasPendientes() = reservaMutex.withLock {
        for (p in obtenerReservasPendientes()) {
            try {
                if (p.idServidor > 0) {
                    // Ya existe en Azure: solo enviar los cambios locales (pago, cancelación, etc.)
                    sincronizarActualizacionReserva(p.idLocal)
                } else {
                    enviarReservaPendiente(p)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reserva local ${p.idLocal} sigue pendiente: ${e.message}")
            }
        }
    }

    private suspend fun enviarReservaPendiente(p: ReservaPendiente) {
        val db = dbHelper.writableDatabase
        val idClienteServidor = asegurarClienteEnServidor(p.cliente)

        // Evitar duplicados: ¿llegó ya a Azure y solo se perdió la respuesta?
        val existente = RetrofitClient.apiService.getReservas().firstOrNull { r ->
            r.idCancha == p.idCancha &&
                    r.idCliente == idClienteServidor &&
                    Math.abs((isoFormat.parse(r.horaInicio)?.time ?: 0L) - p.horaInicio) < 60_000L &&
                    !existeReservaLocalConServidor(db, r.idReserva)   // no robarle la reserva a otra fila local
        }

        if (existente != null) {
            Log.w(TAG, "Reserva local ${p.idLocal} ya existía en Azure (${existente.idReserva}); se vincula")
            val valId = ContentValues().apply {
                put(ReservaContrato.COLUMNA_ID_SERVIDOR, existente.idReserva)
            }
            db.update(
                ReservaContrato.TABLA_RESERVAS,
                valId,
                "${ReservaContrato.COLUMNA_ID} = ?",
                arrayOf(p.idLocal.toString())
            )
            // Enviar el estado local actual (pagos, cancelación) y marcar synced si funciona
            sincronizarActualizacionReserva(p.idLocal)
            return
        }

        val dto = ReservaDTO(
            idReserva = 0,
            horaInicio = isoFormat.format(Date(p.horaInicio)),
            horaFin = isoFormat.format(Date(p.horaFin)),
            precioReserva = p.precio,
            estadoReserva = p.estado,
            montoRecibido = p.montoRecibido,
            idCliente = idClienteServidor,
            idCancha = p.idCancha,
            idUsuario = p.idUsuario
        )

        val resp = RetrofitClient.apiService.postReserva(dto)
        if (resp.isSuccessful) {
            val idAsignado = resp.body()?.idReserva
            if (idAsignado != null && idAsignado > 0) {
                marcarReservaSincronizada(p.idLocal.toLong(), idAsignado)
                Log.i(TAG, "Reserva local ${p.idLocal} enviada a Azure con ID $idAsignado")
            } else {
                Log.e(TAG, "Azure creó la reserva ${p.idLocal} pero no devolvió un IdReserva válido")
            }
        } else {
            Log.e(TAG, "POST reserva ${p.idLocal} rechazado: HTTP ${resp.code()} - ${resp.errorBody()?.string()}")
        }
    }

    private fun existeReservaLocalConServidor(db: SQLiteDatabase, idServidor: Int): Boolean {
        db.query(
            ReservaContrato.TABLA_RESERVAS,
            arrayOf(ReservaContrato.COLUMNA_ID),
            "${ReservaContrato.COLUMNA_ID_SERVIDOR} = ?",
            arrayOf(idServidor.toString()),
            null, null, null
        ).use { cursor -> return cursor.moveToFirst() }
    }
    ////
    ////
    /// Para clientes
    suspend fun sincronizarClientesPendientes() {
        class Pendiente(
            val idServidor: Int,
            val nombre: String,
            val documento: String,
            val telefono: String?,
            val tipo: String
        )

        val db = dbHelper.writableDatabase
        val pendientes = mutableListOf<Pendiente>()

        db.rawQuery(
            """
        SELECT ${ClienteContrato.COLUMNA_ID_SERVIDOR},
               ${ClienteContrato.COLUMNA_NOMBRE},
               ${ClienteContrato.COLUMNA_DOCUMENTO},
               ${ClienteContrato.COLUMNA_TELEFONO},
               ${ClienteContrato.COLUMNA_TIPO}
        FROM ${ClienteContrato.TABLA}
        WHERE ${ClienteContrato.COLUMNA_SYNCED} = 0
          AND ${ClienteContrato.COLUMNA_ID_SERVIDOR} IS NOT NULL
          AND ${ClienteContrato.COLUMNA_ID_SERVIDOR} > 0
        """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                pendientes.add(
                    Pendiente(
                        idServidor = c.getInt(0),
                        nombre = c.getString(1),
                        documento = c.getString(2),
                        telefono = if (c.isNull(3)) null else c.getString(3),
                        tipo = c.getString(4)
                    )
                )
            }
        }

        for (p in pendientes) {
            try {
                val resp = RetrofitClient.apiService.putCliente(
                    ClienteDTO(
                        idCliente = p.idServidor,
                        nombreCliente = p.nombre,
                        numeroDocumento = p.documento,
                        telefonoCliente = p.telefono,
                        tipoCliente = p.tipo
                    )
                )
                if (resp.isSuccessful) {
                    val valores = ContentValues().apply { put(ClienteContrato.COLUMNA_SYNCED, 1) }
                    db.update(
                        ClienteContrato.TABLA,
                        valores,
                        "${ClienteContrato.COLUMNA_ID_SERVIDOR} = ?",
                        arrayOf(p.idServidor.toString())
                    )
                } else {
                    Log.e(TAG, "PUT cliente ${p.idServidor}: HTTP ${resp.code()} - ${resp.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cliente ${p.idServidor} sigue pendiente: ${e.message}")
            }
        }
    }
    companion object {
        private const val TAG = "SyncRepository"
        private val prestamoMutex = Mutex()
        private val reservaMutex = Mutex()
    }
}
