package com.cibertec.proyecto_movil_canchas.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.databinding.ItemClaseProfesorBinding
import com.cibertec.proyecto_movil_canchas.model.ClaseAcademia

class ClaseProfesorAdapter(
    private val alTocarClase: (ClaseAcademia) -> Unit
) : RecyclerView.Adapter<ClaseProfesorAdapter.ClaseViewHolder>() {

    private val clases = mutableListOf<ClaseAcademia>()

    fun actualizar(nuevasClases: List<ClaseAcademia>) {
        clases.clear()
        clases.addAll(nuevasClases)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClaseViewHolder {
        val binding = ItemClaseProfesorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ClaseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClaseViewHolder, position: Int) {
        holder.vincular(clases[position])
    }

    override fun getItemCount(): Int = clases.size

    inner class ClaseViewHolder(
        private val binding: ItemClaseProfesorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun vincular(clase: ClaseAcademia) {
            val context = binding.root.context
            binding.textViewCanchaClase.text = context.getString(
                R.string.formato_cancha_clase,
                clase.nombreCancha,
                clase.idClase
            )
            binding.textViewFechaHorarioClase.text = context.getString(
                R.string.formato_fecha_horario_clase,
                clase.fechaClase,
                clase.horaInicio,
                clase.horaFin
            )
            binding.textViewProfesorClase.text = context.getString(
                R.string.formato_profesor_item,
                clase.nombreProfesor
            )
            binding.textViewEstadoClase.text = clase.estadoClase
            binding.textViewResumenAlumnos.text = context.getString(
                R.string.formato_resumen_alumnos,
                clase.totalAsistenciasMarcadas,
                clase.totalAlumnos
            )

            val semaforoRes = when (clase.estadoClase) {
                "Completada" -> R.drawable.bg_semaforo_verde
                "En curso" -> R.drawable.bg_semaforo_azul
                "Programada" -> R.drawable.bg_semaforo_amarillo
                "Cancelada" -> R.drawable.bg_semaforo_rojo
                else -> R.drawable.bg_semaforo_neutro
            }
            binding.viewSemaforoClase.setBackgroundResource(semaforoRes)

            binding.chipPendienteSync.visibility = if (clase.synced) View.GONE else View.VISIBLE

            binding.root.setOnClickListener {
                alTocarClase(clase)
            }
        }
    }
}