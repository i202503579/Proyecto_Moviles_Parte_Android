package com.cibertec.proyecto_movil_canchas.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.databinding.ItemAlumnoAsistenciaBinding
import com.cibertec.proyecto_movil_canchas.model.AlumnoAsistenciaItem

class AlumnoAsistenciaAdapter(
    private val alumnos: List<AlumnoAsistenciaItem>,
    private val soloLectura: Boolean = false
) : RecyclerView.Adapter<AlumnoAsistenciaAdapter.AlumnoViewHolder>() {

    fun marcarTodosPresentes() {
        if (soloLectura) return
        alumnos.forEach { it.estadoAsistencia = "Presente" }
        notifyDataSetChanged()
    }

    fun obtenerListaActualizada(): List<AlumnoAsistenciaItem> = alumnos

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlumnoViewHolder {
        val binding = ItemAlumnoAsistenciaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlumnoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlumnoViewHolder, position: Int) {
        holder.vincular(alumnos[position], position + 1)
    }

    override fun getItemCount(): Int = alumnos.size

    inner class AlumnoViewHolder(
        private val binding: ItemAlumnoAsistenciaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun vincular(item: AlumnoAsistenciaItem, numero: Int) {
            val context = binding.root.context
            binding.textViewNombreAlumno.text = "$numero. ${item.nombreAlumno}"
            binding.textViewTelefonoApoderado.text = if (item.telefonoApoderado.isNullOrBlank()) {
                context.getString(R.string.apoderado_no_registrado)
            } else {
                context.getString(R.string.formato_apoderado_tel, item.telefonoApoderado)
            }

            binding.radioGroupAsistencia.setOnCheckedChangeListener(null)

            when (item.estadoAsistencia) {
                "Falta" -> binding.radioFalta.isChecked = true
                "Justificado" -> binding.radioJustificado.isChecked = true
                else -> binding.radioPresente.isChecked = true
            }

            binding.radioPresente.isEnabled = !soloLectura
            binding.radioFalta.isEnabled = !soloLectura
            binding.radioJustificado.isEnabled = !soloLectura

            binding.radioGroupAsistencia.setOnCheckedChangeListener { _, checkedId ->
                item.estadoAsistencia = when (checkedId) {
                    R.id.radioFalta -> "Falta"
                    R.id.radioJustificado -> "Justificado"
                    else -> "Presente"
                }
            }
        }
    }
}