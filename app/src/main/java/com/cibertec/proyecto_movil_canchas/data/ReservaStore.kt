package com.cibertec.proyecto_movil_canchas.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.model.Cancha
import com.cibertec.proyecto_movil_canchas.model.Cliente
import com.cibertec.proyecto_movil_canchas.model.FiltroEstado
import com.cibertec.proyecto_movil_canchas.model.FiltroReservas
import com.cibertec.proyecto_movil_canchas.model.RangoFecha
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

    fun obtenerTodas(filtro: FiltroReservas = FiltroReservas()): List<Reserva> {
        val reservas = mutableListOf<Reserva>()
        val condiciones = mutableListOf<String>()
        val args = mutableListOf<String>()

        val col = "r.${ReservaContrato.COLUMNA_HORA_INICIO}"
        when(filtro.rango){
            RangoFecha.HOY -> {
                condiciones += "$col >= ? AND $col <?"
                args += inicioDelDia(0).toString()
                args += inicioDelDia(1).toString()
            }
            RangoFecha.PROXIMAS -> {
                condiciones += "$col >= ?"
                args += inicioDelDia(0).toString()
            }
            RangoFecha.TODAS -> Unit
        }
        val estadoCol = "r.${ReservaContrato.COLUMNA_ESTADO}"
        condiciones += when (filtro.estado){
            FiltroEstado.ACTIVAS -> "$estadoCol != 'Cancelada'"
            FiltroEstado.PENDIENTES -> "$estadoCol = 'Pendiente'"
            FiltroEstado.CONFIRMADAS -> "$estadoCol = 'Confirmada'"
            FiltroEstado.COMPLETADAS -> "$estadoCol = 'Completada'"
            FiltroEstado.CANCELADAS -> "$estadoCol = 'Cancelada'"
        }
        val clausulaWhere = if (condiciones.isNotEmpty()) "WHERE ${condiciones.joinToString(" AND ")}" else ""
        val orden = if (filtro.rango == RangoFecha.TODAS) "DESC" else "ASC"

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
                $clausulaWhere
            ORDER BY $col $orden
        """.trimIndent()

        dbHelper.readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
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
    private fun inicioDelDia(diasDesdeHoy: Int): Long =
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, diasDesdeHoy)
        }.timeInMillis
    fun registrarPago(idReserva: Int, monto: Double): ResultadoPago {
        if (monto <= 0.0) return ResultadoPago.MONTO_INVALIDO
        return ajustarMontoRecibido(idReserva, monto, ESTADOS_SIN_COBRO)
    }

    fun reversarPago(idReserva: Int, montoAReversar: Double): ResultadoPago {
        if (montoAReversar <= 0.0) return ResultadoPago.MONTO_INVALIDO
        return ajustarMontoRecibido(idReserva, -montoAReversar, ESTADOS_SIN_REVERSA)
    }

    /**
     * Suma `delta` al monto recibido: positivo = cobro, negativo = anulación.
     * El estado se recalcula siempre a partir del dinero resultante, así que el
     * monto debe quedar entre 0 y el precio.
     */
    private fun ajustarMontoRecibido(
        idReserva: Int,
        delta: Double,
        estadosBloqueados: Set<String>
    ): ResultadoPago {
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
            if (estado in estadosBloqueados) return ResultadoPago.NO_PERMITIDO

            val nuevoMonto = recibido + delta
            if (nuevoMonto > precio + TOLERANCIA_DECIMALES) return ResultadoPago.EXCEDE_SALDO
            if (nuevoMonto < -TOLERANCIA_DECIMALES) return ResultadoPago.EXCEDE_PAGADO

            // coerceIn absorbe residuos de decimales (-0.0000001 pasa a 0.0)
            val montoFinal = redondear(nuevoMonto.coerceIn(0.0, precio))
            // Determinamos el nuevo estado: si la reserva ya estaba Cancelada, se respeta;
                // de lo contrario, se calcula normalmente según el dinero recibido.
            val nuevoEstado = if (estado == "Cancelada") {
                "Cancelada"
            } else {
                estadoSegunPago(montoFinal, precio)
            }
            val valores = ContentValues().apply {
                put(ReservaContrato.COLUMNA_MONTO_RECIBIDO, montoFinal)
                put(ReservaContrato.COLUMNA_ESTADO, nuevoEstado)
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
    fun actualizarCliente(idReserva: Int, nuevoNombreCliente: String): Int {
        val db = dbHelper.writableDatabase

        var clienteId = -1
        db.query(
            ReservaContrato.TABLA_RESERVAS,
            arrayOf(ReservaContrato.COLUMNA_ID_CLIENTE),
            "${ReservaContrato.COLUMNA_ID} = ?",
            arrayOf(idReserva.toString()),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                clienteId = cursor.getInt(0)
            }
        }

        if (clienteId == -1) return 0

        val valoresCliente = ContentValues().apply {
            put(ClienteContrato.COLUMNA_NOMBRE, nuevoNombreCliente)
        }
        db.update(
            ClienteContrato.TABLA,
            valoresCliente,
            "${ClienteContrato.COLUMNA_ID} = ?",
            arrayOf(clienteId.toString())
        )

        val valoresReserva = ContentValues().apply {
            put(ReservaContrato.COLUMNA_SYNCED, 0)
        }
        return db.update(
            ReservaContrato.TABLA_RESERVAS,
            valoresReserva,
            "${ReservaContrato.COLUMNA_ID} = ?",
            arrayOf(idReserva.toString())
        )
    }

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
        private val ESTADOS_SIN_REVERSA = setOf("Completada")
        private val ESTADOS_SIN_COBRO = setOf("Cancelada", "Confirmada")

    }
}