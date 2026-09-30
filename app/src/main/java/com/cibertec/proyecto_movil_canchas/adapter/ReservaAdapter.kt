package com.cibertec.proyecto_movil_canchas.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.databinding.ItemReservaBinding
import com.cibertec.proyecto_movil_canchas.model.Reserva
import java.text.SimpleDateFormat
import java.util.Locale


class ReservaAdapter(
    private val onReservaClick: (Reserva) -> Unit
    ) : RecyclerView.Adapter<ReservaAdapter.ReservaViewHolder>() {

        private val reservas = mutableListOf<Reserva>()
        private val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun actualizar(nuevasReservas: List<Reserva>) {
            val cantidadAnterior = reservas.size
            reservas.clear()
            if (cantidadAnterior > 0) notifyItemRangeRemoved(0, cantidadAnterior)
            reservas.addAll(nuevasReservas)
            if (reservas.isNotEmpty()) notifyItemRangeInserted(0, reservas.size)
        }

        fun obtenerReserva(position: Int): Reserva = reservas[position]

        fun restaurarTarjeta(position: Int) = notifyItemChanged(position)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservaViewHolder {
            val binding = ItemReservaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ReservaViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ReservaViewHolder, position: Int) {
            holder.vincular(reservas[position])
        }

        override fun getItemCount(): Int = reservas.size

        inner class ReservaViewHolder(private val binding: ItemReservaBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun vincular(reserva: Reserva) {
                binding.textViewCancha.text = reserva.canchaNombre
                binding.textViewHorario.text = binding.root.context.getString(
                    R.string.horario_formato,
                    formatoHora.format(reserva.horaInicio),
                    formatoHora.format(reserva.horaFin)
                )
                binding.textViewCliente.text = binding.root.context.getString(
                    R.string.cliente_formato,
                    reserva.clienteNombre,
                    reserva.tipoCliente
                )
                binding.textViewEstado.text = reserva.estado

                // RN08: la etiqueta de balón solo se muestra si la reserva es institucional.
                binding.chipBalon.visibility =
                    if (reserva.tipoCliente == "Institucional") View.VISIBLE else View.GONE

                val fondoSemaforo = when (reserva.estado) {
                    "Confirmada" -> R.drawable.bg_semaforo_verde
                    "Pendiente" -> R.drawable.bg_semaforo_amarillo
                    "Cancelada" -> R.drawable.bg_semaforo_rojo
                    else -> R.drawable.bg_semaforo_neutro
                }
                binding.viewSemaforoTarjeta.setBackgroundResource(fondoSemaforo)

                binding.root.setOnClickListener { onReservaClick(reserva) }
            }
        }
}