package com.cibertec.proyecto_movil_canchas.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.cibertec.proyecto_movil_canchas.MainActivity
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.data.CanchaStore
import com.cibertec.proyecto_movil_canchas.data.ClienteStore
import com.cibertec.proyecto_movil_canchas.data.ReservaStore
import com.cibertec.proyecto_movil_canchas.databinding.FragmentRegistroReservaBinding
import com.cibertec.proyecto_movil_canchas.model.Cancha
import com.cibertec.proyecto_movil_canchas.model.Cliente
import com.cibertec.proyecto_movil_canchas.model.ResultadoReserva
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class RegistroReservaFragment : Fragment() {

    private var _binding: FragmentRegistroReservaBinding? = null
    private val binding get() = _binding!!

    private lateinit var reservaStore: ReservaStore
    private lateinit var canchaStore: CanchaStore
    private lateinit var clienteStore: ClienteStore

    private var canchas: List<Cancha> = emptyList()
    private var canchaSeleccionada: Cancha? = null
    private var inicioSeleccionado: Calendar? = null
    private var clienteExistente: Cliente? = null

    private fun horasSeleccionadas(): Int = if(binding.radioDosHoras.isChecked) 2 else 1
    private fun actualizarPrecio(){
        val cancha = canchaSeleccionada ?: return
        val horas = horasSeleccionadas()
        binding.textViewPrecio.text =
            getString(R.string.precio_total_formato, cancha.precioAlquiler * horas, horas)
        binding.textViewPrecio.visibility = View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contexto = requireContext().applicationContext
        reservaStore = ReservaStore(contexto)
        canchaStore = CanchaStore(contexto)
        clienteStore = ClienteStore(contexto)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegistroReservaBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cargarCanchas()

        binding.buttonBuscarCliente.setOnClickListener { buscarCliente() }
        binding.buttonSeleccionarHorario.setOnClickListener { mostrarSelectorFechaHora() }
        binding.radioGroupDuracion.setOnCheckedChangeListener { _, _ ->
            actualizarTextoHorario()
            actualizarPrecio()
        }
        binding.buttonGuardarReserva.setOnClickListener { guardarReserva() }

        binding.editTextDocumento.doAfterTextChanged {
            binding.inputLayoutDocumento.error = null
            if (clienteExistente != null) reiniciarCliente()
        }
    }
    private fun mostrarSelectorFechaHora() {
        val calendarioActual = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, anio, mes, dia ->
                TimePickerDialog(
                    requireContext(),
                    { _, hora, minuto ->
                        inicioSeleccionado = Calendar.getInstance().apply {
                            set(anio, mes, dia, hora, minuto, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        actualizarTextoHorario()
                    },
                    calendarioActual.get(Calendar.HOUR_OF_DAY),
                    calendarioActual.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendarioActual.get(Calendar.YEAR),
            calendarioActual.get(Calendar.MONTH),
            calendarioActual.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    private fun cargarCanchas() {
        Thread {
            val lista = canchaStore.obtenerActivas()
            activity?.runOnUiThread {
                val bindingActual = _binding ?: return@runOnUiThread
                canchas = lista
                bindingActual.autoCompleteCancha.setAdapter(
                    ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, lista)
                )
                bindingActual.autoCompleteCancha.setOnItemClickListener { _, _, posicion, _ ->
                    val cancha = canchas[posicion]
                    canchaSeleccionada = cancha
                    bindingActual.inputLayoutCancha.error = null
                    actualizarPrecio()
                }
            }
        }.start()
    }
    private fun buscarCliente() {
        val documento = binding.editTextDocumento.text.toString().trim()
        if (documento.isBlank()) {
            binding.inputLayoutDocumento.error = getString(R.string.error_documento)
            return
        }
        Thread {
            val cliente = clienteStore.buscarPorDocumento(documento)
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                clienteExistente = cliente
                if (cliente != null) mostrarClienteExistente(cliente) else mostrarClienteNuevo()
            }
        }.start()
    }
    private fun mostrarClienteExistente(cliente: Cliente) {
        binding.editTextCliente.setText(cliente.nombre)
        binding.editTextTelefono.setText(cliente.telefono.orEmpty())
        if (cliente.tipoCliente == TIPO_INSTITUCIONAL) {
            binding.radioInstitucional.isChecked = true
        } else {
            binding.radioParticular.isChecked = true
        }
        bloquearDatosCliente(true)
        binding.textViewEstadoCliente.setText(R.string.cliente_encontrado)
        binding.textViewEstadoCliente.visibility = View.VISIBLE
    }

    private fun mostrarClienteNuevo() {
        bloquearDatosCliente(false)
        binding.textViewEstadoCliente.setText(R.string.cliente_nuevo)
        binding.textViewEstadoCliente.visibility = View.VISIBLE
    }

    private fun reiniciarCliente() {
        clienteExistente = null
        bloquearDatosCliente(false)
        binding.editTextCliente.text?.clear()
        binding.editTextTelefono.text?.clear()
        binding.radioParticular.isChecked = true
        binding.textViewEstadoCliente.visibility = View.GONE
    }
    private fun bloquearDatosCliente(bloquear: Boolean) {
        binding.editTextCliente.isEnabled = !bloquear
        binding.editTextTelefono.isEnabled = !bloquear
        binding.radioParticular.isEnabled = !bloquear
        binding.radioInstitucional.isEnabled = !bloquear
    }
    private fun actualizarTextoHorario() {
        val inicio = inicioSeleccionado ?: return
        val fin = (inicio.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, horasSeleccionadas()) }
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.textViewHorarioSeleccionado.text = getString(
            R.string.horario_reserva_formato,
            formatoFecha.format(inicio.time),
            formatoHora.format(inicio.time),
            formatoHora.format(fin.time)
        )
        binding.textViewErrorFormulario.visibility = View.GONE
    }
    private fun guardarReserva() {
        val cancha = canchaSeleccionada
        val documento = binding.editTextDocumento.text.toString().trim()
        val nombre = binding.editTextCliente.text.toString().trim()
        val telefono = binding.editTextTelefono.text.toString().trim().ifBlank { null }
        val tipoCliente = if (binding.radioInstitucional.isChecked) TIPO_INSTITUCIONAL else TIPO_PARTICULAR
        val inicio = inicioSeleccionado

        binding.inputLayoutCancha.error = null
        binding.inputLayoutDocumento.error = null
        binding.inputLayoutCliente.error = null
        binding.textViewErrorFormulario.visibility = View.GONE

        var formularioValido = true
        if (cancha == null) {
            binding.inputLayoutCancha.error = getString(R.string.error_cancha)
            formularioValido = false
        }
        if (documento.isBlank()) {
            binding.inputLayoutDocumento.error = getString(R.string.error_documento)
            formularioValido = false
        }
        if (nombre.isBlank()) {
            binding.inputLayoutCliente.error = getString(R.string.error_cliente)
            formularioValido = false
        }
        if (inicio == null) {
            mostrarError(getString(R.string.error_fecha_hora))
            formularioValido = false
        }
        if (!formularioValido || cancha == null || inicio == null) return

        // Si el cliente ya existía se usa tal cual; si no, se crea junto con la reserva.
        val cliente = clienteExistente ?: Cliente(
            nombre = nombre,
            numeroDocumento = documento,
            telefono = telefono,
            tipoCliente = tipoCliente
        )

        binding.buttonGuardarReserva.isEnabled = false
        binding.buttonGuardarReserva.setText(R.string.guardando_reserva)

        Thread {
            val resultado = reservaStore.agregar(cancha, cliente, inicio.timeInMillis, horasSeleccionadas())

            activity?.runOnUiThread {
                val bindingActual = _binding ?: return@runOnUiThread
                bindingActual.buttonGuardarReserva.isEnabled = true
                bindingActual.buttonGuardarReserva.setText(R.string.boton_guardar_reserva)

                when (resultado) {
                    is ResultadoReserva.EXITO -> {
                        limpiarFormulario()
                        (activity as? MainActivity)?.abrirLista()
                    }
                    ResultadoReserva.HorarioOcupado ->
                        mostrarError(getString(R.string.error_horario_ocupado))
                    ResultadoReserva.Error ->
                        Toast.makeText(
                            requireContext(),
                            R.string.error_guardando_reserva,
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
        }.start()
    }
    private fun mostrarError(mensaje: String) {
        binding.textViewErrorFormulario.text = mensaje
        binding.textViewErrorFormulario.visibility = View.VISIBLE
    }

    private fun limpiarFormulario() {
        binding.autoCompleteCancha.setText(null, false)
        canchaSeleccionada = null
        binding.textViewPrecio.visibility = View.GONE
        binding.radioUnaHora.isChecked = true

        binding.editTextDocumento.text?.clear()
        reiniciarCliente()

        inicioSeleccionado = null
        binding.textViewHorarioSeleccionado.setText(R.string.horario_reserva_sin_seleccionar)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TIPO_PARTICULAR = "Particular"
        private const val TIPO_INSTITUCIONAL = "Institucional"
    }
}