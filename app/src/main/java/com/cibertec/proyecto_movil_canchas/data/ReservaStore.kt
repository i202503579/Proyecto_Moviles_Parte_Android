package com.cibertec.proyecto_movil_canchas.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.cibertec.proyecto_movil_canchas.model.Cancha
import com.cibertec.proyecto_movil_canchas.model.Cliente
import com.cibertec.proyecto_movil_canchas.model.Reserva
import com.cibertec.proyecto_movil_canchas.model.ResultadoPago
import com.cibertec.proyecto_movil_canchas.model.ResultadoReserva
import com.cibertec.proyecto_movil_canchas.model.estadoSegunPago
import com.cibertec.proyecto_movil_canchas.model.redondear

class ReservaStore(context: Context) {
    private val dbHelper = CanchitasDbHelper.getInstance(context)
    fun agregar(cancha: Cancha, cliente: Cliente, horaInicio: Long): ResultadoReserva {
        val horaFin = horaInicio + DURACION_RESERVA_MS
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            if (hayChoqueDeHorario(db, cancha.id, horaInicio, horaFin)) {
                return ResultadoReserva.HorarioOcupado
            }
            val idCliente = resolverCliente(db, cliente) ?: return ResultadoReserva.Error

            val valores = ContentValues().apply {
                put(ReservaContrato.COLUMNA_ID_CANCHA, cancha.id)
                put(ReservaContrato.COLUMNA_ID_CLIENTE, idCliente)
                put(ReservaContrato.COLUMNA_HORA_INICIO, horaInicio)
                put(ReservaContrato.COLUMNA_HORA_FIN, horaFin)
                put(ReservaContrato.COLUMNA_PRECIO, cancha.precioAlquiler)
                put(ReservaContrato.COLUMNA_MONTO_RECIBIDO, 0.0)
                put(ReservaContrato.COLUMNA_ESTADO, ESTADO_INICIAL)
                put(ReservaContrato.COLUMNA_SYNCED, 0)
            }
            val id = db.insert(ReservaContrato.TABLA_RESERVAS, null, valores)
            if (id == -1L) return ResultadoReserva.Error

            db.setTransactionSuccessful()
            return ResultadoReserva.EXITO(id)
        } catch (e: Exception) {
            return ResultadoReserva.Error
        } finally {
            db.endTransaction() // si no hubo setTransactionSuccessful(), hace rollback
        }
    }

    fun obtenerTodas(): List<Reserva> {
        val reservas = mutableListOf<Reserva>()
        // Orden de columnas (se leen por posición porque "nombre" aparece dos veces):
        // 0 id, 1 canchaId, 2 canchaNombre, 3 clienteId, 4 clienteNombre, 5 tipoCliente,
        // 6 horaInicio, 7 horaFin, 8 precio, 9 montoRecibido, 10 estado
        val sql = """
            SELECT r.${ReservaContrato.COLUMNA_ID},
                   r.${ReservaContrato.COLUMNA_ID_CANCHA},
                   c.${CanchaContrato.COLUMNA_NOMBRE},
                   r.${ReservaContrato.COLUMNA_ID_CLIENTE},
                   cl.${ClienteContrato.COLUMNA_NOMBRE},
                   cl.${ClienteContrato.COLUMNA_TIPO},
                   r.${ReservaContrato.COLUMNA_HORA_INICIO},
                   r.${ReservaContrato.COLUMNA_HORA_FIN},
                   r.${ReservaContrato.COLUMNA_PRECIO},
                   r.${ReservaContrato.COLUMNA_MONTO_RECIBIDO},
                   r.${ReservaContrato.COLUMNA_ESTADO}
            FROM ${ReservaContrato.TABLA_RESERVAS} r
            JOIN ${CanchaContrato.TABLA} c
                ON c.${CanchaContrato.COLUMNA_ID} = r.${ReservaContrato.COLUMNA_ID_CANCHA}
            JOIN ${ClienteContrato.TABLA} cl
                ON cl.${ClienteContrato.COLUMNA_ID} = r.${ReservaContrato.COLUMNA_ID_CLIENTE}
            ORDER BY r.${ReservaContrato.COLUMNA_HORA_INICIO} ASC
        """.trimIndent()

        dbHelper.readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                reservas.add(
                    Reserva(
                        id = cursor.getInt(0),
                        canchaId = cursor.getInt(1),
                        canchaNombre = cursor.getString(2),
                        clienteId = cursor.getInt(3),
                        clienteNombre = cursor.getString(4),
                        tipoCliente = cursor.getString(5),
                        horaInicio = cursor.getLong(6),
                        horaFin = cursor.getLong(7),
                        precioReserva = cursor.getDouble(8),
                        montoRecibido = cursor.getDouble(9),
                        estado = cursor.getString(10)
                    )
                )
            }
        }
        return reservas
    }

    fun registrarPago(idReserva: Int, monto: Double): ResultadoPago {
        if (monto <= 0.0) return ResultadoPago.MONTO_INVALIDO

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            var precio = 0.0
            var recibido = 0.0
            var estado = ""
            val encontrada = db.query(
                ReservaContrato.TABLA_RESERVAS,
                arrayOf(
                    ReservaContrato.COLUMNA_PRECIO,
                    ReservaContrato.COLUMNA_MONTO_RECIBIDO,
                    ReservaContrato.COLUMNA_ESTADO
                ),
                "${ReservaContrato.COLUMNA_ID} = ?",
                arrayOf(idReserva.toString()),
                null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    precio = cursor.getDouble(0)
                    recibido = cursor.getDouble(1)
                    estado = cursor.getString(2)
                    true
                } else false
            }
            if (!encontrada) return ResultadoPago.ERROR

            if (estado == "Cancelada" || estado == "Completada") return ResultadoPago.NO_PERMITIDO

            val nuevoMonto = recibido + monto
            if (nuevoMonto > precio + TOLERANCIA_DECIMALES) return ResultadoPago.EXCEDE_SALDO

            val nuevoRecibidoRedondeado = redondear(nuevoMonto)

            val valores = ContentValues().apply {
                put(ReservaContrato.COLUMNA_MONTO_RECIBIDO, nuevoRecibidoRedondeado)
                put(ReservaContrato.COLUMNA_ESTADO, estadoSegunPago(nuevoRecibidoRedondeado, precio))
                put(ReservaContrato.COLUMNA_SYNCED, 0)
            }
            val filas = db.update(
                ReservaContrato.TABLA_RESERVAS,
                valores,
                "${ReservaContrato.COLUMNA_ID} = ?",
                arrayOf(idReserva.toString())
            )
            if (filas != 1) return ResultadoPago.ERROR

            db.setTransactionSuccessful()
            return ResultadoPago.EXITO
        } catch (e: Exception) {
            return ResultadoPago.ERROR
        } finally {
            db.endTransaction()
        }
    }

    // aun viendo que hacer con los cancelados, si dejarlos en eliminacion fisica o logica
    fun cancelar(idReserva: Int): Int {
        val valores = ContentValues().apply {
            put(ReservaContrato.COLUMNA_ESTADO, "Cancelada")
            put(ReservaContrato.COLUMNA_SYNCED, 0)
        }
        return dbHelper.writableDatabase.update(
            ReservaContrato.TABLA_RESERVAS,
            valores,
            "${ReservaContrato.COLUMNA_ID} = ? AND ${ReservaContrato.COLUMNA_ESTADO} != 'Cancelada'",
            arrayOf(idReserva.toString())
        )
    }

    fun actualizarEstado(idReserva: Int, nuevoEstado: String): Int {
        val valores = ContentValues().apply {
            put(ReservaContrato.COLUMNA_ESTADO, nuevoEstado)
            put(ReservaContrato.COLUMNA_SYNCED, 0)
        }
        return dbHelper.writableDatabase.update(
            ReservaContrato.TABLA_RESERVAS,
            valores,
            "${ReservaContrato.COLUMNA_ID} = ?",
            arrayOf(idReserva.toString())
        )
    }

    // Dos reservas de la misma cancha se cruzan si inicio1 < fin2 Y inicio2 < fin1.
    private fun hayChoqueDeHorario(
        db: SQLiteDatabase, canchaId: Int, horaInicio: Long, horaFin: Long
    ): Boolean {
        db.query(
            ReservaContrato.TABLA_RESERVAS,
            arrayOf(ReservaContrato.COLUMNA_ID),
            "${ReservaContrato.COLUMNA_ID_CANCHA} = ? " +
                    "AND ${ReservaContrato.COLUMNA_ESTADO} != 'Cancelada' " +
                    "AND ${ReservaContrato.COLUMNA_HORA_INICIO} < ? " +
                    "AND ${ReservaContrato.COLUMNA_HORA_FIN} > ?",
            arrayOf(canchaId.toString(), horaFin.toString(), horaInicio.toString()),
            null, null, null
        ).use { cursor ->
            return cursor.count > 0
        }
    }

    // Devuelve el id local del cliente (existente o recién creado), o null si falló.
    private fun resolverCliente(db: SQLiteDatabase, cliente: Cliente): Int? {
        if (cliente.id != 0) return cliente.id
        ClienteStore.buscarPorDocumento(db, cliente.numeroDocumento)?.let { return it.id }
        val nuevoId = ClienteStore.insertar(db, cliente)
        return if (nuevoId == -1L) null else nuevoId.toInt()
    }

    companion object {
        private const val DURACION_RESERVA_MS = 3_600_000L // regla: toda reserva dura 1 hora
        private const val ESTADO_INICIAL = "Pendiente"
        private const val TOLERANCIA_DECIMALES = 0.005
    }
}