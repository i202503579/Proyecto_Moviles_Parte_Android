package com.cibertec.proyecto_movil_canchas.ui

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.cibertec.proyecto_movil_canchas.MainActivity
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.data.ReservaContrato
import com.cibertec.proyecto_movil_canchas.data.ReservaStore
import com.cibertec.proyecto_movil_canchas.databinding.FragmentRegistroReservaBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class RegistroReservaFragment : Fragment() {

    // El signo ? permite que _binding contenga null cuando la vista todavía no existe
    // o ya fue destruida. El guion bajo es una convención para la propiedad interna.
    private var _binding: FragmentRegistroReservaBinding? = null
    // get() crea un getter personalizado. !! afirma que _binding no es null mientras
    // se usa la vista del Fragment; por eso se libera obligatoriamente en onDestroyView().
    private val binding get() = _binding!!

    private lateinit var reservaStore: ReservaStore

    private var horaInicioSeleccionada: Calendar? = null
    private var horaFinSeleccionada: Calendar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
// applicationContext evita que estas clases conserven accidentalmente la vista.
        reservaStore = ReservaStore(requireContext().applicationContext)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentRegistroReservaBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarSelectorCancha()
        binding.buttonGuardarReserva.setOnClickListener {
            registrarReserva()
        }
        binding.buttonHoraInicio.setOnClickListener { elegirHora(esInicio = true) }
        binding.buttonHoraFin.setOnClickListener { elegirHora(esInicio = false) }
    }
    private fun elegirHora(esInicio: Boolean) {
        val ahora = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, horas, minutos ->
                val seleccion = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, horas)
                    set(Calendar.MINUTE, minutos)
                    set(Calendar.SECOND, 0)
                }
                if (esInicio) horaInicioSeleccionada = seleccion else horaFinSeleccionada = seleccion
                actualizarTextoHorario()
            },
            ahora.get(Calendar.HOUR_OF_DAY),
            ahora.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun actualizarTextoHorario() {
        val formato = SimpleDateFormat("HH:mm", Locale.getDefault())
        val textoInicio = horaInicioSeleccionada?.let { formato.format(it.time) } ?: "--:--"
        val textoFin = horaFinSeleccionada?.let { formato.format(it.time) } ?: "--:--"
        binding.textViewHorarioSeleccionado.text = getString(R.string.horario_formato, textoInicio, textoFin)
    }
    private fun configurarSelectorCancha(){
        binding.autoCompleteCancha.setSimpleItems(R.array.canchas_disponibles)
        // El evento entrega cuatro parámetros. Cada _ indica que ese parámetro existe,
        // pero no se necesita dentro de esta lambda. -> marca el inicio de su cuerpo.
        binding.autoCompleteCancha.setOnItemClickListener { _, _, _, _ ->
            binding.inputLayoutCancha.error = null
        }
    }

    private fun registrarReserva() {
        val nombreCancha = obtenerCanchaSeleccionada()
        val nombre = binding.editTextCliente.text.toString().trim()
        val tipoCliente = obtenerTipoCliente()
        val horaInicio = horaInicioSeleccionada
        val horaFin = horaFinSeleccionada
        val adelanto = binding.editTextAdelanto.text.toString().toDoubleOrNull()

        binding.inputLayoutCliente.error = null
        binding.inputLayoutCancha.error = null
        binding.inputLayoutAdelanto.error = null
        binding.textViewErrorFormulario.visibility = View.GONE


        var formularioValido = true
        if (nombre.isBlank()) {
            binding.inputLayoutCliente.error = getString(R.string.error_cliente)
            formularioValido = false
        }
        if (nombreCancha == null) {
            binding.inputLayoutCancha.error = getString(R.string.error_cancha)
            formularioValido = false
        }
        if (tipoCliente == null) {
            binding.radioGroupTipoCliente.requestFocus()
            binding.textViewErrorFormulario.text =
                "Selecciona un tipo de cliente"
            binding.textViewErrorFormulario.visibility = View.VISIBLE
            formularioValido = false
        }
        if (horaInicio == null || horaFin == null || !horaFin.after(horaInicio)) {
            binding.textViewErrorFormulario.text = getString(R.string.error_horario)
            binding.textViewErrorFormulario.visibility = View.VISIBLE
            formularioValido = false
        }
        if (adelanto == null || adelanto < ADELANTO_MINIMO) {
            binding.inputLayoutAdelanto.error = getString(R.string.error_adelanto)
            formularioValido = false
        }
        if (!formularioValido || nombreCancha == null || tipoCliente == null || horaInicio == null || horaFin == null || adelanto == null) {
            return
        }
        binding.buttonGuardarReserva.isEnabled = false
        binding.buttonGuardarReserva.setText(R.string.guardando_reserva)

        Thread {
            val idGenerado = runCatching {
                reservaStore.agregar(
                    canchaNombre = nombreCancha,
                    clienteNombre = nombre,
                    horaInicio = horaInicio.timeInMillis,
                    horaFin = horaFin.timeInMillis,
                    estado = ESTADO_POR_DEFECTO,
                    tipoCliente = tipoCliente,
                    adelanto = adelanto,
                    saldo = SALDO_MINIMO
                )
            }.getOrDefault(-1L)

            activity?.runOnUiThread {
                val bindingActual = _binding ?: return@runOnUiThread
                bindingActual.buttonGuardarReserva.isEnabled = true
                bindingActual.buttonGuardarReserva.setText(R.string.dialogo_guardar)

                if (idGenerado == -1L) {
                    Toast.makeText(
                        requireContext(),
                        R.string.error_guardando_reserva,
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }
                limpiarFormulario()
                (activity as? MainActivity)?.abrirLista()
            }

        }.start()
    }
    private fun obtenerCanchaSeleccionada(): String?{
        return binding.autoCompleteCancha.text.toString()
            .takeIf { cancha -> cancha.isNotBlank() }
    }
    private fun obtenerTipoCliente(): String?{
        return when(binding.radioGroupTipoCliente.checkedRadioButtonId){
            R.id.radioInstitucional -> getString(R.string.institucional)
            R.id.radioParticular -> getString(R.string.tipo_particular)
            else -> null
        }
    }
    private fun limpiarFormulario(){
        binding.editTextCliente.text?.clear()
        binding.editTextAdelanto.text?.clear()
        binding.autoCompleteCancha.setText(null, false)
        binding.radioGroupTipoCliente.clearCheck()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    companion object {
        private const val ADELANTO_MINIMO = 0.0
        private const val SALDO_MINIMO = 0.0
        private const val ESTADO_POR_DEFECTO = "Confirmada"

    }
}