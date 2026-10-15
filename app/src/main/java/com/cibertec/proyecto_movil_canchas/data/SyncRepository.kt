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
import com.cibertec.proyecto_movil_canchas.network.ReservaDTO
import com.cibertec.proyecto_movil_canchas.network.RetrofitClient
import java.text.SimpleDateFormat
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
    suspend fun sincronizarReservas(): Boolean {
        return try {
            sincronizarCanchas()
            sincronizarClientes()

            val reservasApi = RetrofitClient.apiService.getReservas()
            val db = dbHelper.writableDatabase

            db.beginTransaction()
            try {
                for (r in reservasApi) {
                    val inicioMillis = try { isoFormat.parse(r.horaInicio)?.time ?: 0L } catch (_: Exception) { 0L }
                    val finMillis = try { isoFormat.parse(r.horaFin)?.time ?: 0L } catch (_: Exception) { 0L }

                    val idClienteLocal = resolverClienteLocalPorServidor(db, r.idCliente) ?: r.idCliente

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
                enviadaAzure = true
                try {
                    val listaAzure = RetrofitClient.apiService.getReservas()
                    val match = listaAzure.lastOrNull { r ->
                        r.idCancha == cancha.id &&
                        Math.abs((isoFormat.parse(r.horaInicio)?.time ?: 0L) - horaInicioMillis) < 60_000L
                    }
                    val idAsignado = match?.idReserva
                    marcarReservaSincronizada(idReservaLocal, idAsignado)
                } catch (_: Exception) {
                    marcarReservaSincronizada(idReservaLocal, null)
                }
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
              ON (cl.${ClienteContrato.COLUMNA_ID} = r.${ReservaContrato.COLUMNA_ID_CLIENTE}
                  OR cl.${ClienteContrato.COLUMNA_ID_SERVIDOR} = r.${ReservaContrato.COLUMNA_ID_CLIENTE})
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
        try {
            val lista = RetrofitClient.apiService.getClientes()
            val existente = lista.find { it.numeroDocumento == cliente.numeroDocumento }
            if (existente != null) {
                return existente.idCliente
            }

            val nuevoDto = ClienteDTO(
                nombreCliente = cliente.nombre,
                numeroDocumento = cliente.numeroDocumento,
                telefonoCliente = cliente.telefono,
                tipoCliente = cliente.tipoCliente
            )
            RetrofitClient.apiService.postCliente(nuevoDto)

            val listaActualizada = RetrofitClient.apiService.getClientes()
            val recienCreado = listaActualizada.find { it.numeroDocumento == cliente.numeroDocumento }
            if (recienCreado != null) {
                return recienCreado.idCliente
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error asegurando cliente en servidor: ${e.message}")
        }
        return if (cliente.id > 0) cliente.id else 1
    }

    private fun marcarReservaSincronizada(idLocal: Long, idServidor: Int? = null) {
        val db = dbHelper.writableDatabase
        val valores = ContentValues().apply {
            put(ReservaContrato.COLUMNA_SYNCED, 1)
            if (idServidor != null && idServidor > 0) {
                put(ReservaContrato.COLUMNA_ID_SERVIDOR, idServidor)
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

    companion object {
        private const val TAG = "SyncRepository"
    }
}
