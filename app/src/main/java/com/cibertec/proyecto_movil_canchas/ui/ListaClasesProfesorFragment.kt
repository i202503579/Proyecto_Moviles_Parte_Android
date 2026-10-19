package com.cibertec.proyecto_movil_canchas.ui

import android.content.Context
import android.health.connect.datatypes.ExerciseRoute
import android.icu.text.DateFormat
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.adapter.AlumnoAsistenciaAdapter
import com.cibertec.proyecto_movil_canchas.adapter.ClaseProfesorAdapter
import com.cibertec.proyecto_movil_canchas.data.AcademiaStore
import com.cibertec.proyecto_movil_canchas.data.SessionManager
import com.cibertec.proyecto_movil_canchas.databinding.DialogAsistenciaClaseBinding
import com.cibertec.proyecto_movil_canchas.databinding.FragmentListaClasesProfesorBinding
import com.cibertec.proyecto_movil_canchas.location.LocationCliente
import com.cibertec.proyecto_movil_canchas.location.ReglasUbicación
import com.cibertec.proyecto_movil_canchas.location.ResultadoZona
import com.cibertec.proyecto_movil_canchas.model.AlumnoAsistenciaItem
import com.cibertec.proyecto_movil_canchas.model.ClaseAcademia
import com.cibertec.proyecto_movil_canchas.model.FiltroEstadoClase
import com.cibertec.proyecto_movil_canchas.model.ProfesorItem
import com.cibertec.proyecto_movil_canchas.util.PermissionHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Date
import kotlin.math.roundToInt

class ListaClasesProfesorFragment : Fragment() {

    private var _binding: FragmentListaClasesProfesorBinding? = null
    private val binding get() = _binding!!

    private lateinit var academiaStore: AcademiaStore
    private lateinit var sessionManager: SessionManager
    private lateinit var claseAdapter: ClaseProfesorAdapter
    private var profesoresDisponibles: List<ProfesorItem> = emptyList()
    private var idProfesorSeleccionado: Int? = null
    private var filtroEstado: FiltroEstadoClase = FiltroEstadoClase.PROGRAMADAS
    private lateinit var locationCliente: LocationCliente
    private var accionPendienteUbicacion: (() -> Unit)? = null
    private val permisoUbicacionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){
        if (PermissionHelper.hasFineLocationPermission(requireContext())){
            accionPendienteUbicacion?.invoke()
        } else{
            Toast.makeText(requireContext(), R.string.error_permiso_ubicacion, Toast.LENGTH_LONG).show()
        }
        accionPendienteUbicacion = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        academiaStore = AcademiaStore(requireContext().applicationContext)
        sessionManager = SessionManager(requireContext().applicationContext)
        locationCliente = LocationCliente(requireContext().applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListaClasesProfesorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
        configurarFiltros()
        mostrarUltimaSincronizacion()

        binding.buttonSincronizarClases.setOnClickListener {
            sincronizarConAzure()
        }
    }

    override fun onResume() {
        super.onResume()
        cargarProfesoresYClasesLocales()
    }

    private fun configurarRecyclerView() {
        claseAdapter = ClaseProfesorAdapter { clase ->
            mostrarDialogoAsistencia(clase)
        }
        binding.recyclerViewClasesProfesor.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewClasesProfesor.adapter = claseAdapter
    }

    private fun configurarFiltros() {
        if (sessionManager.esProfesor()) {
            binding.layoutFiltroProfesor.visibility = View.GONE
            idProfesorSeleccionado = sessionManager.obtenerIdUsuario()
        } else {
            binding.layoutFiltroProfesor.visibility = View.VISIBLE
        }

        binding.chipGroupEstadoClase.setOnCheckedStateChangeListener { _, ids ->
            filtroEstado = when (ids.firstOrNull()) {
                R.id.chipClasesProgramadas -> FiltroEstadoClase.PROGRAMADAS
                R.id.chipClasesCompletadas -> FiltroEstadoClase.COMPLETADAS
                else -> FiltroEstadoClase.TODAS
            }
            cargarClasesLocales()
        }

        binding.spinnerProfesor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!sessionManager.esProfesor()) {
                    idProfesorSeleccionado = if (position == 0) {
                        null
                    } else {
                        profesoresDisponibles.getOrNull(position - 1)?.idUsuario
                    }
                    cargarClasesLocales()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun cargarProfesoresYClasesLocales() {
        if (sessionManager.esProfesor()) {
            idProfesorSeleccionado = sessionManager.obtenerIdUsuario()
            cargarClasesLocales()
            return
        }

        Thread {
            val profesores = academiaStore.obtenerProfesoresLocales()
            activity?.runOnUiThread {
                val bindingActual = _binding ?: return@runOnUiThread
                profesoresDisponibles = profesores

                val nombres = mutableListOf(getString(R.string.profesor_todos))
                nombres.addAll(profesores.map {
                    getString(R.string.formato_profesor_spinner, it.nombre, it.idUsuario)
                })

                val adapterSpinner = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    nombres
                ).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                bindingActual.spinnerProfesor.adapter = adapterSpinner

                cargarClasesLocales()
            }
        }.start()
    }

    private fun cargarClasesLocales() {
        binding.progressIndicatorClases.visibility = View.VISIBLE
        binding.recyclerViewClasesProfesor.visibility = View.GONE
        binding.textViewListaVaciaClases.visibility = View.GONE

        Thread {
            val clases = academiaStore.obtenerClases(idProfesorSeleccionado, filtroEstado)
            activity?.runOnUiThread {
                val bindingActual = _binding ?: return@runOnUiThread
                claseAdapter.actualizar(clases)
                bindingActual.progressIndicatorClases.visibility = View.GONE
                bindingActual.recyclerViewClasesProfesor.visibility =
                    if (clases.isEmpty()) View.GONE else View.VISIBLE
                bindingActual.textViewListaVaciaClases.visibility =
                    if (clases.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun sincronizarConAzure() {
        binding.progressIndicatorClases.visibility = View.VISIBLE
        binding.buttonSincronizarClases.isEnabled = false
        binding.textViewEstadoSyncClases.setText(R.string.sincronizando_clases)

        Thread {
            val resultado = academiaStore.sincronizarConWeb()
            activity?.runOnUiThread {
                val bindingActual = _binding ?: return@runOnUiThread
                bindingActual.buttonSincronizarClases.isEnabled = true

                resultado.onSuccess { cantidad ->
                    guardarMarcaSincronizacion()
                    mostrarUltimaSincronizacion()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sincronizacion_clases_exito, cantidad),
                        Toast.LENGTH_SHORT
                    ).show()
                    cargarProfesoresYClasesLocales()
                }.onFailure {
                    bindingActual.progressIndicatorClases.visibility = View.GONE
                    bindingActual.textViewEstadoSyncClases.setText(R.string.sincronizacion_clases_error)
                    Toast.makeText(
                        requireContext(),
                        R.string.sincronizacion_clases_error,
                        Toast.LENGTH_LONG
                    ).show()
                    cargarClasesLocales()
                }
            }
        }.start()
    }

    private fun mostrarDialogoAsistencia(clase: ClaseAcademia) {
        Thread {
            val alumnos = academiaStore.obtenerAlumnosParaAsistencia(clase.idClase, clase.idUsuario)
            activity?.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                abrirModalAsistencia(clase, alumnos)
            }
        }.start()
    }

    private fun abrirModalAsistencia(
        clase: ClaseAcademia,
        alumnos: List<com.cibertec.proyecto_movil_canchas.model.AlumnoAsistenciaItem>
    ) {
        val dialogBinding = DialogAsistenciaClaseBinding.inflate(layoutInflater)
        val soloLectura = clase.estadoClase == "Completada" || clase.estadoClase == "Cancelada"

        dialogBinding.textViewDetalleClaseHeader.text = getString(
            R.string.formato_detalle_clase_dialogo,
            clase.nombreCancha,
            clase.fechaClase,
            clase.horaInicio,
            clase.horaFin
        )
        dialogBinding.textViewSubdetalleClaseHeader.text = getString(
            R.string.formato_subdetalle_clase_dialogo,
            clase.nombreProfesor,
            clase.estadoClase
        )

        val adapterAlumnos = AlumnoAsistenciaAdapter(alumnos, soloLectura)
        dialogBinding.recyclerViewAlumnosAsistencia.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.recyclerViewAlumnosAsistencia.adapter = adapterAlumnos

        if (alumnos.isEmpty()) {
            dialogBinding.textViewSinAlumnos.visibility = View.VISIBLE
            dialogBinding.recyclerViewAlumnosAsistencia.visibility = View.GONE
            dialogBinding.buttonMarcarTodosPresentes.visibility = View.GONE
            dialogBinding.checkBoxCompletarClase.visibility = View.GONE
        } else {
            dialogBinding.textViewSinAlumnos.visibility = View.GONE
            dialogBinding.recyclerViewAlumnosAsistencia.visibility = View.VISIBLE
            dialogBinding.buttonMarcarTodosPresentes.visibility =
                if (soloLectura) View.GONE else View.VISIBLE
            dialogBinding.checkBoxCompletarClase.visibility =
                if (soloLectura) View.GONE else View.VISIBLE
        }

        dialogBinding.buttonMarcarTodosPresentes.setOnClickListener {
            adapterAlumnos.marcarTodosPresentes()
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialogo_asistencia_titulo, clase.idClase))
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.dialogo_volver, null)

        if (!soloLectura && alumnos.isNotEmpty()) {
            builder.setPositiveButton(R.string.dialogo_guardar) { _, _ ->
                val marcarCompletada = dialogBinding.checkBoxCompletarClase.isChecked
                val alumnosMarcados = adapterAlumnos.obtenerListaActualizada()
                verificarUbicacionYGuardar(clase.idClase, alumnosMarcados, marcarCompletada)
            }
        } else if (soloLectura) {
            Toast.makeText(
                requireContext(),
                getString(R.string.clase_cerrada_aviso, clase.estadoClase),
                Toast.LENGTH_SHORT
            ).show()
        }
        builder.show()
    }
    private fun verificarUbicacionYGuardar(
        idClase: Int,
        alumnos: List<AlumnoAsistenciaItem>,
        marcarCompletada: Boolean
    ){
        if (!sessionManager.esProfesor()){
            guardarYSincronizarAsistencia(idClase, alumnos, marcarCompletada)
            return
        }
        if (!PermissionHelper.hasFineLocationPermission(requireContext())){
            accionPendienteUbicacion = {
                verificarUbicacionYGuardar(idClase, alumnos, marcarCompletada)
            }
            permisoUbicacionLauncher.launch(PermissionHelper.locationPermission)
            return
        }
        if (!locationCliente.isLocationEnabled()){
            Toast.makeText(requireContext(), R.string.error_gps_apagado, Toast.LENGTH_LONG).show()
            return
        }
        binding.progressIndicatorClases.visibility = View.VISIBLE
        Toast.makeText(requireContext(), R.string.verificando_ubicacion, Toast.LENGTH_SHORT).show()
        locationCliente.requestCurrentLocation(
            onSuccess = { ubicacion ->
                alRecibirUbicacion(ubicacion, idClase,alumnos, marcarCompletada)
            },
            onError = { alFallarUbicacion() }
        )
    }
    private fun alRecibirUbicacion(
        ubicacion: Location,
        idClase: Int,
        alumnos: List<AlumnoAsistenciaItem>,
        marcarCompletada: Boolean
    ){
        val bindingActual = _binding ?: return
        bindingActual.progressIndicatorClases.visibility = View.GONE

        val evaluacion = ReglasUbicación.evaluar(ubicacion)
        when (evaluacion.resultado){
            ResultadoZona.DENTRO -> guardarYSincronizarAsistencia(idClase, alumnos, marcarCompletada)
            ResultadoZona.FUERA -> Toast.makeText(
                requireContext(),
                getString(R.string.error_fuera_de_zona, evaluacion.distanciaMetros.roundToInt()),
                Toast.LENGTH_LONG
            ).show()
            ResultadoZona.IMPRECISA -> Toast.makeText(
                requireContext(),
                getString(R.string.error_ubicacion_imprecisa, evaluacion.precisionMetros.roundToInt()),
                Toast.LENGTH_LONG
            ).show()
            ResultadoZona.UBICACION_FALSA ->
                Toast.makeText(requireContext(), R.string.error_ubicacion_falsa, Toast.LENGTH_LONG).show()
        }
    }
    private fun alFallarUbicacion(){
        val bindingActual = _binding ?: return
        bindingActual.progressIndicatorClases.visibility = View.GONE
        Toast.makeText(requireContext(), R.string.error_ubicacion_no_disponible, Toast.LENGTH_LONG).show()
    }
    private fun guardarYSincronizarAsistencia(
        idClase: Int,
        alumnos: List<com.cibertec.proyecto_movil_canchas.model.AlumnoAsistenciaItem>,
        marcarCompletada: Boolean
    ) {
        binding.progressIndicatorClases.visibility = View.VISIBLE
        Thread {
            val guardadoOk = academiaStore.guardarAsistenciasLocal(idClase, alumnos, marcarCompletada)
            val subidoNube = if (guardadoOk) {
                runCatching {
                    academiaStore.subirPendientesAAzure()
                }.getOrDefault(false)
            } else false

            activity?.runOnUiThread {
                val bindingActual = _binding ?: return@runOnUiThread
                bindingActual.progressIndicatorClases.visibility = View.GONE
                if (guardadoOk) {
                    val msg = if (subidoNube) {
                        R.string.asistencia_guardada_nube
                    } else {
                        R.string.asistencia_guardada_local
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
                cargarClasesLocales()
            }
        }.start()
    }

    private fun guardarMarcaSincronizacion() {
        requireContext().getSharedPreferences(PREFS_ACADEMIA_SYNC, Context.MODE_PRIVATE)
            .edit {
                putLong(CLAVE_ULTIMO_SYNC_ACADEMIA, System.currentTimeMillis())
            }
    }

    private fun mostrarUltimaSincronizacion() {
        val prefs = requireContext().getSharedPreferences(PREFS_ACADEMIA_SYNC, Context.MODE_PRIVATE)
        val ultimo = prefs.getLong(CLAVE_ULTIMO_SYNC_ACADEMIA, 0L)
        if (ultimo > 0L) {
            val fecha = DateFormat.getDateTimeInstance().format(Date(ultimo))
            binding.textViewEstadoSyncClases.text = getString(R.string.respaldo_ultimo_formato, fecha)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationCliente.stopUpdates()
        _binding = null
    }

    companion object {
        private const val PREFS_ACADEMIA_SYNC = "prefs_academia_sync"
        private const val CLAVE_ULTIMO_SYNC_ACADEMIA = "ultimo_sync_academia"
    }
}