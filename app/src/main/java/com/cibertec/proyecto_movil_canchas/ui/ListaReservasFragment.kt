package com.cibertec.proyecto_movil_canchas.ui

import android.app.job.JobScheduler
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.icu.text.DateFormat
import android.os.Bundle
import android.text.InputType
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.adapter.ReservaAdapter
import com.cibertec.proyecto_movil_canchas.background.RespaldoScheduler
import com.cibertec.proyecto_movil_canchas.data.PrestamoStore
import com.cibertec.proyecto_movil_canchas.data.ReservaStore
import com.cibertec.proyecto_movil_canchas.data.SyncRepository
import com.cibertec.proyecto_movil_canchas.databinding.DialogDetalleReservaBinding
import com.cibertec.proyecto_movil_canchas.databinding.FragmentListaReservasBinding
import com.cibertec.proyecto_movil_canchas.model.FiltroEstado
import com.cibertec.proyecto_movil_canchas.model.FiltroReservas
import com.cibertec.proyecto_movil_canchas.model.RangoFecha
import com.cibertec.proyecto_movil_canchas.model.Reserva
import com.cibertec.proyecto_movil_canchas.model.ResultadoPago
import com.cibertec.proyecto_movil_canchas.model.ResultadoPrestamo
import com.cibertec.proyecto_movil_canchas.model.ResultadoRestauracion
import androidx.lifecycle.lifecycleScope
import androidx.core.content.edit
import com.cibertec.proyecto_movil_canchas.data.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.Date
import kotlin.math.abs
import kotlin.math.sqrt

class ListaReservasFragment : Fragment(), SensorEventListener {
    private var _binding: FragmentListaReservasBinding? = null
    private val binding get() = _binding!!
    private var filtro = FiltroReservas()
    private lateinit var reservaAdapter: ReservaAdapter
    private lateinit var reservaStore: ReservaStore
    private lateinit var syncRepository: SyncRepository
    private lateinit var sensorManager: SensorManager
    private lateinit var prestamoStore: PrestamoStore
    private var acelerometro: Sensor? = null
    private var ultimoTiempoSacudida: Long = 0
    private fun esAdmin(): Boolean = SessionManager(requireContext().applicationContext).esAdmin()
    private fun filtroInicial(): FiltroReservas =
        if (esAdmin()) FiltroReservas(rango = RangoFecha.TODAS, estado = FiltroEstado.CANCELADAS)
        else FiltroReservas()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contexto = requireContext().applicationContext
        reservaStore = ReservaStore(contexto)
        syncRepository = SyncRepository(contexto)
        prestamoStore = PrestamoStore(contexto)
        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListaReservasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
        configurarFiltros()
        binding.buttonRespaldo.setOnClickListener {
            programarRespaldo()
        }
    }
    private fun configurarRecyclerView(){
        reservaAdapter = ReservaAdapter { solicitud -> mostrarDetalle(solicitud) }
        binding.recyclerViewReservas.layoutManager =
            LinearLayoutManager(requireContext())
        binding.recyclerViewReservas.adapter = reservaAdapter
        configurarGestos()
    }
    private fun configurarFiltros(){
        if (esAdmin()) {
            filtro = filtroInicial()
            binding.chipGroupRango.check(R.id.chipTodas)
            binding.chipGroupEstado.check(R.id.chipCanceladas)
        }
        binding.chipGroupRango.setOnCheckedStateChangeListener { _, ids ->
            val rango = when (ids.firstOrNull()){
                R.id.chipHoy -> RangoFecha.HOY
                R.id.chipTodas -> RangoFecha.TODAS
                else -> RangoFecha.PROXIMAS
            }
            filtro = filtro.copy(rango = rango)
            cargarReservas()
        }
        binding.chipGroupEstado.setOnCheckedStateChangeListener { _, ids ->
            val estado = when (ids.firstOrNull()){
                R.id.chipPendientes -> FiltroEstado.PENDIENTES
                R.id.chipConfirmadas -> FiltroEstado.CONFIRMADAS
                R.id.chipCompletadas -> FiltroEstado.COMPLETADAS
                R.id.chipCanceladas -> FiltroEstado.CANCELADAS
                else -> FiltroEstado.ACTIVAS
            }
            filtro = filtro.copy(estado = estado)
            cargarReservas()
        }
    }
    private fun mostrarDetalle(reserva: Reserva){
        val formatoHora = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val dialogBinding = DialogDetalleReservaBinding.inflate(layoutInflater)
        dialogBinding.textViewCanchaDialog.text = reserva.canchaNombre
        dialogBinding.textViewClienteDialog.text = reserva.clienteNombre
        dialogBinding.textViewEstadoDialog.text = reserva.estado
        dialogBinding.textViewHorarioDialog.text = getString(R.string.dialogo_formato_horario,
            formatoHora.format(reserva.horaInicio), formatoHora.format(reserva.horaFin) )

        val notaLocal = obtenerNotaLocal(reserva.id)
        if (notaLocal.isNotEmpty()) {
            dialogBinding.textViewNotaDialog.visibility = View.VISIBLE
            dialogBinding.textViewNotaDialog.text = getString(R.string.dialogo_nota_formato, notaLocal)
        } else {
            dialogBinding.textViewNotaDialog.visibility = View.GONE
        }

        val fondoSemaforo = when(reserva.estado){
            getString(R.string.estado_completada) -> R.drawable.bg_semaforo_verde
            getString(R.string.estado_confirmado) -> R.drawable.bg_semaforo_azul
            getString(R.string.estado_pendiente) -> R.drawable.bg_semaforo_amarillo
            getString(R.string.estado_cancelada) -> R.drawable.bg_semaforo_rojo
            else -> R.drawable.bg_semaforo_neutro
        }
        dialogBinding.viewSemaforoDialog.setBackgroundResource(fondoSemaforo)
        var ventana: AlertDialog? = null
        val esInstitucional = reserva.tipoCliente == getString(R.string.institucional)
        val esCancelada = reserva.estado == "Cancelada"
        val balonPendiente = reserva.prestamoEstado == "Pendiente"
        val puedeGestionarBalon = esInstitucional && !esCancelada &&
                (reserva.prestamoEstado == null || balonPendiente)

        dialogBinding.buttonPrestamoBalonDialog.visibility =
            if (puedeGestionarBalon) View.VISIBLE else View.GONE
        dialogBinding.buttonPrestamoBalonDialog.setText(
            if (balonPendiente) R.string.boton_registrar_devolucion else R.string.boton_prestar_balon
        )
        dialogBinding.buttonPrestamoBalonDialog.setOnClickListener {
            ventana?.dismiss()
            if (balonPendiente) mostrarDevolucion(reserva) else confirmarPrestamo(reserva)
        }

        val builder = MaterialAlertDialogBuilder(requireContext()).setView(dialogBinding.root)
        if (esCancelada){
            if (esAdmin()) {
                builder.setPositiveButton(R.string.boton_eliminar_definitivo) { _, _ ->
                    confirmarEliminacionLocal(reserva) }
            }
        }else{
            builder.setPositiveButton(R.string.boton_registrar_pago){ _, _ ->
                mostrarPago(reserva) }
            builder.setNeutralButton(R.string.boton_anular_pago){ _, _ ->
                mostrarAnulacionPago(reserva)}
        }
        builder.setNegativeButton(R.string.dialogo_volver, null)
        ventana = builder.create()
        ventana.show()
    }
    private fun confirmarPrestamo(reserva: Reserva){
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialogo_prestamo_titulo)
            .setMessage(getString(R.string.dialogo_prestamo_mensaje, reserva.clienteNombre))
            .setPositiveButton(R.string.dialogo_prestamo_confirmar){ _, _ ->prestarBalon(reserva)}
            .setNegativeButton(R.string.dialogo_volver, null).show()
    }
    private fun prestarBalon(reserva: Reserva){
        Thread{
            val resultado = prestamoStore.prestar(reserva.id)

            if (resultado == ResultadoPrestamo.EXITO) {
                try {
                    val sincronizado = kotlinx.coroutines.runBlocking {
                        syncRepository.sincronizarPrestamo(reserva.id)
                    }

                    android.util.Log.d(
                        "SyncPrestamo",
                        "Reserva local ${reserva.id}: sincronización = $sincronizado"
                    )
                } catch (e: Exception) {
                    android.util.Log.e(
                        "SyncPrestamo",
                        "Error al sincronizar el préstamo",
                        e
                    )
                }
            }

            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                val mensaje = when (resultado){
                    ResultadoPrestamo.EXITO -> R.string.prestamo_registrado
                    ResultadoPrestamo.NO_PERMITIDO -> R.string.error_prestamo_cancelada
                    ResultadoPrestamo.NO_INSTITUCIONAL -> R.string.error_prestamo_no_institucional
                    ResultadoPrestamo.YA_PRESTADO -> R.string.error_prestamo_ya_prestado
                    else -> R.string.error_prestamo
                }
                Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
                if (resultado == ResultadoPrestamo.EXITO) cargarReservas()
            }
        }.start()
    }
    private fun mostrarDevolucion(reserva: Reserva){
            val opciones = arrayOf("Devuelto", "Dañado", "Perdido")
            var elegido = 0
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialogo_devolucion_titulo)
            .setSingleChoiceItems(opciones, elegido){ _, which -> elegido = which}
            .setPositiveButton(R.string.dialogo_devolucion_confirmar){
                _, _ -> registrarDevolucion(reserva, opciones[elegido]) }
            .setNegativeButton(R.string.dialogo_volver, null)
            .show()
    }
    private fun registrarDevolucion(reserva: Reserva, estadoFinal: String){
        Thread {
            val resultado = prestamoStore.registrarDevolucion(reserva.id, estadoFinal)
            if (resultado == ResultadoPrestamo.EXITO) {
                try {
                    val sincronizado = kotlinx.coroutines.runBlocking {
                        syncRepository.sincronizarPrestamo(reserva.id)
                    }
                    android.util.Log.d(
                        "SyncPrestamo",
                        "Devolución reserva local ${reserva.id}: sincronización = $sincronizado"
                    )
                } catch (e: Exception) {
                    android.util.Log.e("SyncPrestamo", "Error al sincronizar la devolución", e)
                }
            }
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                val mensaje = when (resultado) {
                    ResultadoPrestamo.EXITO -> R.string.devolucion_registrada
                    ResultadoPrestamo.SIN_PRESTAMO_PENDIENTE -> R.string.error_devolucion_sin_prestamo
                    else -> R.string.error_devolucion
                }
                Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
                if (resultado == ResultadoPrestamo.EXITO) cargarReservas()
            }
        }.start()
    }
    private fun restaurarReserva(reserva: Reserva, posicion: Int? = null){
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val resultado = reservaStore.restaurar(reserva.id)
            if (resultado == ResultadoRestauracion.EXITO) {
                syncRepository.sincronizarActualizacionReserva(reserva.id)
            }
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                when (resultado){
                    ResultadoRestauracion.EXITO -> {
                        Toast.makeText(requireContext(), "Reserva restaurada correctamente", Toast.LENGTH_SHORT).show()
                        cargarReservas()
                    }
                    ResultadoRestauracion.HORARIO_OCUPADO -> {
                        Toast.makeText(requireContext(), "No es posible la restauración: Horario ocupado",
                            Toast.LENGTH_LONG).show()
                        posicion?.let {reservaAdapter.restaurarTarjeta(it)}
                    }
                    ResultadoRestauracion.NO_PERMITIDO -> {
                        Toast.makeText(requireContext(), "Solo se puede restaurar reservas canceladas",
                            Toast.LENGTH_SHORT).show()
                        posicion?.let { reservaAdapter.restaurarTarjeta(it) }
                    }

                    ResultadoRestauracion.FECHA_PASADA -> {
                        Toast.makeText(requireContext(), R.string.error_fecha_pasada, Toast.LENGTH_LONG)
                            .show()
                        posicion?.let { reservaAdapter.restaurarTarjeta(it) }
                    }
                    else -> {
                        Toast.makeText(requireContext(), "Error al restaurar reserva", Toast.LENGTH_SHORT).show()
                        posicion?.let { reservaAdapter.restaurarTarjeta(it) }
                    }
                }
            }
        }
    }
    private fun confirmarRestauracion(reserva: Reserva, posicion: Int){
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restaurar reserva")
            .setMessage("¿Estás seguro de que deseas reactivar la reserva de ${reserva.clienteNombre} para la cancha ${reserva.canchaNombre}?")
            .setPositiveButton("Sí, Restaurar"){
                _, _ -> restaurarReserva(reserva,posicion)
            }
            .setNegativeButton(R.string.dialogo_volver){
                _, _ -> reservaAdapter.restaurarTarjeta(posicion)
            }
            .setOnCancelListener {
                reservaAdapter.restaurarTarjeta(posicion)
            }.show()
    }
    private fun confirmarEliminacionLocal(reserva: Reserva, posicion: Int? = null) {
        // Solo el Administrador (también se comprueba dentro de SyncRepository)
        if (!esAdmin()) {
            Toast.makeText(requireContext(), R.string.error_eliminar_solo_admin, Toast.LENGTH_LONG).show()
            posicion?.let { reservaAdapter.restaurarTarjeta(it) }
            return
        }
        if (reserva.montoRecibido > 0.0) {
            Toast.makeText(requireContext(), R.string.error_eliminar_con_dinero, Toast.LENGTH_LONG).show()
            posicion?.let { reservaAdapter.restaurarTarjeta(it) }
            return
        }
        if (reserva.prestamoEstado != null) {
            Toast.makeText(requireContext(), R.string.error_eliminar_con_prestamo, Toast.LENGTH_LONG).show()
            posicion?.let { reservaAdapter.restaurarTarjeta(it) }
            return
        }
        val input = TextInputEditText(requireContext()).apply {
            hint = "Escribe ELIMINAR"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.eliminar_definitivo_titulo)
            .setMessage(getString(R.string.eliminar_definitivo_mensaje, reserva.clienteNombre, reserva.canchaNombre))
            .setView(input)
            .setPositiveButton(R.string.boton_eliminar_confirmar){ _, _ ->
                ejecutarEliminacionLocal(reserva)
            }
            .setNegativeButton(R.string.dialogo_volver) { _, _ ->
                posicion?.let { reservaAdapter.restaurarTarjeta(it) }
            }
            .setOnCancelListener {
                posicion?.let { reservaAdapter.restaurarTarjeta(it) }
            }
            .create()

        dialogo.setOnShowListener {
            val botonEliminar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            botonEliminar.isEnabled = false
            input.doAfterTextChanged { texto ->
                botonEliminar.isEnabled = texto.toString().trim() == PALABRA_CONFIRMACION
            }
        }

        dialogo.show()
    }

    private fun ejecutarEliminacionLocal(reserva: Reserva){
        Thread{
            val exito = kotlinx.coroutines.runBlocking {
                syncRepository.eliminarReservaGlobal(reserva.id)
            }
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                if (exito){
                    limpiarNotaLocal(reserva.id)
                    Toast.makeText(requireContext(), "Reserva eliminada", Toast.LENGTH_SHORT).show()
                    cargarReservas()
                } else{
                    Toast.makeText(requireContext(), "No se pudo eliminar la reserva", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    private fun limpiarNotaLocal(idReserva: Int){
        val prefs = requireContext().getSharedPreferences("notas_locales_prefs", Context.MODE_PRIVATE)
        prefs.edit { remove("nota_reserva_$idReserva") }
    }
    private fun mostrarPago(reserva: Reserva) {
        val input = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.hint_monto_pago)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialogo_pago_titulo)
            .setMessage(getString(R.string.dialogo_pago_mensaje, reserva.precioReserva, reserva.montoRecibido, reserva.saldo))
            .setView(input)
            .setPositiveButton(R.string.dialogo_pago_confirmar) { _, _ ->
                val monto = input.text.toString().toDoubleOrNull()
                if (monto == null) {
                    Toast.makeText(requireContext(), R.string.error_pago_invalido, Toast.LENGTH_LONG).show()
                } else {
                    registrarPago(reserva, monto)
                }
            }
            .setNegativeButton(R.string.dialogo_volver, null)
            .show()
    }

    private fun registrarPago(reserva: Reserva, monto: Double) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val resultado = syncRepository.registrarPago(reserva.id, monto)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                val mensaje = when (resultado) {
                    ResultadoPago.EXITO -> R.string.pago_registrado
                    ResultadoPago.MONTO_INVALIDO -> R.string.error_pago_invalido
                    ResultadoPago.EXCEDE_SALDO -> R.string.error_pago_excede
                    ResultadoPago.EXCEDE_PAGADO -> R.string.error_reverso_excede
                    ResultadoPago.NO_PERMITIDO -> R.string.error_pago_no_permitido
                    ResultadoPago.ERROR -> R.string.error_registrar_pago
                }
                Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
                if (resultado == ResultadoPago.EXITO) cargarReservas()
            }
        }
    }
    private fun configurarGestos(){
        val callback = object : ItemTouchHelper.SimpleCallback(
0,
            ItemTouchHelper.START or ItemTouchHelper.END
        ){
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val posicion = viewHolder.bindingAdapterPosition
                if (posicion == RecyclerView.NO_POSITION) return
                val reserva = reservaAdapter.obtenerReserva(posicion)
                if (direction == ItemTouchHelper.END){
                    mostrarEdicion(reserva, posicion)
                }else{
                    if (reserva.estado == "Cancelada"){
                        confirmarRestauracion(reserva, posicion)
                    }else{
                        confirmarCancelacion(reserva, posicion)
                    }
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerViewReservas)
    }
    private fun mostrarEdicion(reserva: Reserva, posicion: Int){
        val context = requireContext()
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        val inputNombre = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint = getString(R.string.hint_cliente)
            setText(reserva.clienteNombre)
        }
        val inputNota = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE
            hint = getString(R.string.hint_nota)
            setText(obtenerNotaLocal(reserva.id))
        }
        layout.addView(inputNombre)
        layout.addView(inputNota)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialogo_cambiar_nombre_cliente, reserva.clienteNombre))
            .setMessage(R.string.dialogo_mensaje_modificar)
            .setView(layout)
            .setPositiveButton(R.string.dialogo_guardar){ _, _ ->
                val nuevoNombre = inputNombre.text.toString().trim()
                val nuevaNota = inputNota.text.toString().trim()
                if (nuevoNombre.isEmpty()){
                    Toast.makeText(context, R.string.error_cliente, Toast.LENGTH_SHORT).show()
                    reservaAdapter.restaurarTarjeta(posicion)
                    return@setPositiveButton
                }
                guardarNotaLocal(reserva.id, nuevaNota)
                guardarEdicion(reserva, nuevoNombre, posicion)
            }
            .setNegativeButton(R.string.dialogo_cancelar){ _, _ ->
                reservaAdapter.restaurarTarjeta(posicion)
            }
            .setOnCancelListener { reservaAdapter.restaurarTarjeta(posicion) }
            .show()
    }
    private fun guardarEdicion(reserva: Reserva, nuevoNombre: String, posicion: Int){
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val filasActualizadas = syncRepository.actualizarCliente(reserva.id, nuevoNombre)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                reservaAdapter.restaurarTarjeta(posicion)
                if (filasActualizadas > 0){
                    Toast.makeText(requireContext(), R.string.cliente_actualizado, Toast.LENGTH_SHORT).show()
                    cargarReservas()
                } else{
                    Toast.makeText(requireContext(), R.string.error_actualizar_reserva, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
    private fun guardarNotaLocal(idReserva: Int, nota: String) {
        val prefs = requireContext().getSharedPreferences("notas_locales_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("nota_reserva_$idReserva", nota) }
    }

    private fun obtenerNotaLocal(idReserva: Int): String {
        val prefs = requireContext().getSharedPreferences("notas_locales_prefs", Context.MODE_PRIVATE)
        return prefs.getString("nota_reserva_$idReserva", "") ?: ""
    }
    private fun confirmarCancelacion(reserva: Reserva, posicion: Int) {
        if (reserva.prestamoEstado == "Pendiente") {
            Toast.makeText(requireContext(), R.string.error_cancelar_con_balon, Toast.LENGTH_LONG).show()
            reservaAdapter.restaurarTarjeta(posicion)
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialogo_cancelar_reserva_titulo)
            .setMessage(getString(R.string.dialogo_cancelar_reserva_mensaje, reserva.canchaNombre, reserva.clienteNombre))
            .setPositiveButton(R.string.dialogo_cancelar_reserva_confirmar) { _, _ -> cancelarReserva(reserva, posicion) }
            .setNegativeButton(R.string.dialogo_volver) { _, _ -> reservaAdapter.restaurarTarjeta(posicion) }
            .setOnCancelListener { reservaAdapter.restaurarTarjeta(posicion) }
            .show()
    }
    private fun mostrarAnulacionPago(reserva: Reserva) {
        if (reserva.montoRecibido <= 0.0) {
            Toast.makeText(requireContext(), "Esta reserva no tiene pagos registrados para anular", Toast.LENGTH_SHORT).show()
            return
        }

        val input = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Monto a descontar/reversar"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Anular / Reversar Pago")
            .setMessage("Monto recibido actual: S/ ${reserva.montoRecibido}\n\nIngresa la cantidad a descontar:")
            .setView(input)
            .setPositiveButton("Continuar") { _, _ ->
                val monto = input.text.toString().toDoubleOrNull()
                if (monto == null || monto <= 0.0) {
                    Toast.makeText(requireContext(), R.string.error_pago_invalido, Toast.LENGTH_SHORT).show()
                } else if (monto > reserva.montoRecibido) {
                    Toast.makeText(requireContext(), "El monto a descontar supera lo pagado", Toast.LENGTH_SHORT).show()
                } else {
                    // Diálogo de confirmación de seguridad (Trazabilidad visual)
                    confirmarEjecucionReversion(reserva, monto)
                }
            }
            .setNegativeButton(R.string.dialogo_volver, null)
            .show()
    }

    private fun confirmarEjecucionReversion(reserva: Reserva, monto: Double) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Estás seguro?")
            .setMessage("Vas a retirar S/ $monto del registro de esta reserva.\n\nEsta acción modificará el saldo de caja. ¿Deseas proceder?")
            .setPositiveButton("Sí, anular pago") { _, _ ->
                ejecutarReversion(reserva, monto)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarReversion(reserva: Reserva, monto: Double) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val resultado = syncRepository.reversarPago(reserva.id, monto)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                when (resultado) {
                    ResultadoPago.EXITO -> {
                        Toast.makeText(requireContext(), "Pago reversado correctamente", Toast.LENGTH_SHORT).show()
                        cargarReservas()
                    }
                    ResultadoPago.EXCEDE_SALDO, ResultadoPago.EXCEDE_PAGADO -> {
                        Toast.makeText(requireContext(), "El monto a descontar supera lo pagado", Toast.LENGTH_LONG).show()
                    }
                    ResultadoPago.NO_PERMITIDO -> {
                        Toast.makeText(requireContext(), "Acción no permitida para este estado", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(requireContext(), "Error al reversar el pago", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun cancelarReserva(reserva: Reserva, posicion: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val filas = syncRepository.cancelar(reserva.id)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                if (filas == 1) {
                    cargarReservas()
                } else {
                    reservaAdapter.restaurarTarjeta(posicion)
                    Toast.makeText(requireContext(), R.string.error_cancelar_reserva, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun programarRespaldo(){
        val resultado = RespaldoScheduler.programar(requireContext())
        val mensaje = if(resultado == JobScheduler.RESULT_SUCCESS){
            R.string.respaldo_programado
        }else{
            R.string.respaldo_no_programado
        }
        binding.textViewEstadoRespaldo.setText(mensaje)
        Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()

        binding.root.postDelayed({mostrarEstadoRespaldo()}, COMPROBACION_RESPALDO_MS)
    }
    override fun onSensorChanged(event: SensorEvent){
        when(event.sensor.type){
            Sensor.TYPE_ACCELEROMETER -> evaluarSacudida(event.values)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int){

    }
    override fun onResume() {
        super.onResume()
        cargarReservas()
        mostrarEstadoRespaldo()
        listOfNotNull(acelerometro).forEach { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    private fun cargarReservas(){
        binding.progressIndicator.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val reservasLocales = reservaStore.obtenerTodas(filtro)
            withContext(Dispatchers.Main) {
                val bindingActual = _binding ?: return@withContext
                reservaAdapter.actualizar(reservasLocales)
                bindingActual.progressIndicator.visibility = View.GONE
                bindingActual.recyclerViewReservas.visibility =
                    if (reservasLocales.isEmpty()) View.GONE else View.VISIBLE
                bindingActual.textViewListaVacia.visibility =
                    if (reservasLocales.isEmpty()) View.VISIBLE else View.GONE
            }

            val syncExitosa = syncRepository.sincronizarReservas()
            if (syncExitosa) {
                val reservasNuevas = reservaStore.obtenerTodas(filtro)
                withContext(Dispatchers.Main) {
                    val bindingActual = _binding ?: return@withContext
                    reservaAdapter.actualizar(reservasNuevas)
                    bindingActual.recyclerViewReservas.visibility =
                        if (reservasNuevas.isEmpty()) View.GONE else View.VISIBLE
                    bindingActual.textViewListaVacia.visibility =
                        if (reservasNuevas.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
    private fun evaluarSacudida(valores: FloatArray){
        val x = valores[0]
        val y = valores[1]
        val z = valores[2]
        val magnitud = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val aceleracionSinGravedad = abs(magnitud - SensorManager.GRAVITY_EARTH)

        if (aceleracionSinGravedad > 8.0F) {
            val tiempoActual = System.currentTimeMillis()
            if (tiempoActual - ultimoTiempoSacudida > 2000) {
                ultimoTiempoSacudida = tiempoActual
                restablecerFiltros()
            }
        }
    }
    private fun restablecerFiltros() {
        filtro = filtroInicial()

        binding.chipGroupRango.check(if (esAdmin()) R.id.chipTodas else R.id.chipProximas)
        binding.chipGroupEstado.check(if (esAdmin()) R.id.chipCanceladas else R.id.chipActivas)
        Toast.makeText(requireContext(), "Filtros restablecidos", Toast.LENGTH_SHORT).show()
    }
    private fun mostrarEstadoRespaldo(){
        val bindingActual = _binding ?: return
        val preferencias = requireContext().getSharedPreferences(
            RespaldoScheduler.PREFERENCIAS_RESPALDO,
            Context.MODE_PRIVATE
        )
        val ultimoRespaldo = preferencias.getLong(
            RespaldoScheduler.CLAVE_ULTIMO_RESPALDO,
                SIN_RESPALDO
        )
        if (ultimoRespaldo != SIN_RESPALDO){
            val fecha = DateFormat.getDateInstance().format(Date(ultimoRespaldo))
            bindingActual.textViewEstadoRespaldo.text =
                getString(R.string.respaldo_ultimo_formato, fecha)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
    companion object{
        private const val COMPROBACION_RESPALDO_MS = 6_000L
        private const val SIN_RESPALDO = 0L
        private const val PALABRA_CONFIRMACION = "ELIMINAR"
    }
}