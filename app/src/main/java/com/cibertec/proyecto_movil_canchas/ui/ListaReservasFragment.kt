package com.cibertec.proyecto_movil_canchas.ui

import android.app.job.JobScheduler
import android.content.Context
import android.icu.text.DateFormat
import android.os.Bundle
import android.text.InputType
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.adapter.ReservaAdapter
import com.cibertec.proyecto_movil_canchas.background.RespaldoScheduler
import com.cibertec.proyecto_movil_canchas.data.ReservaStore
import com.cibertec.proyecto_movil_canchas.databinding.DialogDetalleReservaBinding
import com.cibertec.proyecto_movil_canchas.databinding.FragmentListaReservasBinding
import com.cibertec.proyecto_movil_canchas.model.Reserva
import com.cibertec.proyecto_movil_canchas.model.ResultadoPago
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.Date

class ListaReservasFragment : Fragment() {
    private var _binding: FragmentListaReservasBinding? = null
    private val binding get() = _binding!!

    private lateinit var reservaAdapter: ReservaAdapter
    private lateinit var reservaStore: ReservaStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reservaStore = ReservaStore(requireContext().applicationContext)
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
            dialogBinding.textViewNotaDialog.text = "Nota: $notaLocal"
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

        dialogBinding.buttonPrestamoBalonDialog.visibility =
            if (reserva.tipoCliente == getString(R.string.institucional)) View.VISIBLE else View.GONE
        dialogBinding.buttonPrestamoBalonDialog.setOnClickListener {
            Toast.makeText(requireContext(), R.string.prestamo_pendiente, Toast.LENGTH_LONG).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.boton_registrar_pago) { _, _ -> mostrarPago(reserva) }
            .setNegativeButton(R.string.dialogo_volver, null)
            .show()
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
        Thread {
            val resultado = reservaStore.registrarPago(reserva.id, monto)
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                val mensaje = when (resultado) {
                    ResultadoPago.EXITO -> R.string.pago_registrado
                    ResultadoPago.MONTO_INVALIDO -> R.string.error_pago_invalido
                    ResultadoPago.EXCEDE_SALDO -> R.string.error_pago_excede
                    ResultadoPago.NO_PERMITIDO -> R.string.error_pago_no_permitido
                    ResultadoPago.ERROR -> R.string.error_registrar_pago
                }
                Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
                if (resultado == ResultadoPago.EXITO) cargarReservas()
            }
        }.start()
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
                    confirmarCancelacion(reserva, posicion)
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
        Thread{
            val filasActualizadas = reservaStore.actualizarCliente(reserva.id, nuevoNombre)
            activity?.runOnUiThread{
                if (_binding == null) return@runOnUiThread
                if (filasActualizadas > 0){
                    Toast.makeText(requireContext(), R.string.cliente_actualizado, Toast.LENGTH_SHORT).show()
                    cargarReservas()
                } else{
                    reservaAdapter.restaurarTarjeta(posicion)
                    Toast.makeText(requireContext(), R.string.error_actualizar_reserva, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }.start()
    }
    private fun guardarNotaLocal(idReserva: Int, nota: String) {
        val prefs = requireContext().getSharedPreferences("notas_locales_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("nota_reserva_$idReserva", nota).apply()
    }

    private fun obtenerNotaLocal(idReserva: Int): String {
        val prefs = requireContext().getSharedPreferences("notas_locales_prefs", Context.MODE_PRIVATE)
        return prefs.getString("nota_reserva_$idReserva", "") ?: ""
    }
    private fun confirmarCancelacion(reserva: Reserva, posicion: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialogo_cancelar_reserva_titulo)
            .setMessage(getString(R.string.dialogo_cancelar_reserva_mensaje, reserva.canchaNombre, reserva.clienteNombre))
            .setPositiveButton(R.string.dialogo_cancelar_reserva_confirmar) { _, _ -> cancelarReserva(reserva, posicion) }
            .setNegativeButton(R.string.dialogo_volver) { _, _ -> reservaAdapter.restaurarTarjeta(posicion) }
            .setOnCancelListener { reservaAdapter.restaurarTarjeta(posicion) }
            .show()
    }

    private fun cancelarReserva(reserva: Reserva, posicion: Int) {
        Thread {
            val filas = reservaStore.cancelar(reserva.id)
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                if (filas == 1) {
                    cargarReservas()
                } else {
                    reservaAdapter.restaurarTarjeta(posicion)
                    Toast.makeText(requireContext(), R.string.error_cancelar_reserva, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
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

    override fun onResume() {
        super.onResume()
        cargarReservas()
        mostrarEstadoRespaldo()
    }
    private fun cargarReservas(){
        binding.progressIndicator.visibility = View.VISIBLE
        binding.recyclerViewReservas.visibility = View.GONE
        binding.textViewListaVacia.visibility = View.GONE
        //thread
        Thread{
           val reservas = reservaStore.obtenerTodas()

            activity?.runOnUiThread {
                val bindingActual = _binding ?: return@runOnUiThread
                reservaAdapter.actualizar(reservas)
                bindingActual.progressIndicator.visibility = View.GONE
                bindingActual.recyclerViewReservas.visibility =
                    if (reservas.isEmpty()) View.GONE else View.VISIBLE
                bindingActual.textViewListaVacia.visibility =
                    if (reservas.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }
    private fun mostrarEstadoRespaldo(){
        val bindingActual = _binding ?: return
        val preferencias = requireContext().getSharedPreferences(
            RespaldoScheduler.PREFERENCIAS_RESPALDO,
                    android.content.Context.MODE_PRIVATE
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
    }
}