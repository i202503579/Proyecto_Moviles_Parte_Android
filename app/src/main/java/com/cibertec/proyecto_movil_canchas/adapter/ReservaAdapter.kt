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

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun actualizar(nuevasReservas: List<Reserva>) {
            reservas.clear()
            reservas.addAll(nuevasReservas)
            notifyDataSetChanged()
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
                binding.chipBalon.text = when(reserva.prestamoEstado){
                    null -> binding.root.context.getString(R.string.chip_balon)
                    "Pendiente" -> binding.root.context.getString(R.string.chip_balon_prestado)
                    else -> binding.root.context.getString(R.string.chip_balon_estado_formato, reserva.prestamoEstado)
                }
                binding.chipBalon.visibility = if (reserva.tipoCliente == "Institucional") View.VISIBLE else View.GONE
                val fondoSemaforo = when (reserva.estado) {
                    binding.root.context.getString(R.string.estado_confirmado) -> R.drawable.bg_semaforo_verde
                    binding.root.context.getString(R.string.estado_pendiente) -> R.drawable.bg_semaforo_amarillo
                    binding.root.context.getString(R.string.estado_cancelada) -> R.drawable.bg_semaforo_rojo
                    else -> R.drawable.bg_semaforo_neutro
                }
                binding.viewSemaforoTarjeta.setBackgroundResource(fondoSemaforo)

                binding.root.setOnClickListener { onReservaClick(reserva) }
            }
        }
}