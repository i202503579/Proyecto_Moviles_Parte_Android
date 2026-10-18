package com.cibertec.proyecto_movil_canchas

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.cibertec.proyecto_movil_canchas.data.SessionManager
import com.cibertec.proyecto_movil_canchas.databinding.ActivityMainBinding
import com.cibertec.proyecto_movil_canchas.ui.ListaClasesProfesorFragment
import com.cibertec.proyecto_movil_canchas.ui.ListaReservasFragment
import com.cibertec.proyecto_movil_canchas.ui.LoginActivity
import com.cibertec.proyecto_movil_canchas.ui.RegistrarUsuarioFragment
import com.cibertec.proyecto_movil_canchas.ui.RegistroReservaFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var sensorLuz: Sensor? = null
    private var modoOscuro = false
    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        if (!sessionManager.estaLogueado()) {
            irALogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sensorManager = getSystemService(SensorManager::class.java)
        sensorLuz = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        configurarSesionYPermisos(savedInstanceState)

        binding.buttonRegistro.setOnClickListener {
            mostrarFragmento(RegistroReservaFragment())
        }

        binding.buttonLista.setOnClickListener {
            abrirLista()
        }

        binding.buttonProfesor.setOnClickListener {
            mostrarFragmento(ListaClasesProfesorFragment())
        }

        binding.buttonUsuarios.setOnClickListener {
            mostrarFragmento(RegistrarUsuarioFragment())
        }

        binding.buttonCerrarSesion.setOnClickListener {
            confirmarCerrarSesion()
        }
    }

    private fun configurarSesionYPermisos(savedInstanceState: Bundle?) {
        val usuario = sessionManager.obtenerUsuario()
        val nombre = usuario?.nombreUsuario ?: getString(R.string.usuario_por_defecto)
        val rol = usuario?.rol ?: getString(R.string.rol_operador)

        binding.textViewUsuarioSesion.text = getString(R.string.formato_usuario_sesion, nombre, rol)

        when {
            sessionManager.esProfesor() -> {
                binding.buttonRegistro.visibility = View.GONE
                binding.buttonLista.visibility = View.GONE
                binding.buttonProfesor.visibility = View.VISIBLE
                binding.buttonUsuarios.visibility = View.GONE
                if (savedInstanceState == null) {
                    mostrarFragmento(ListaClasesProfesorFragment())
                }
            }
            sessionManager.esOperador() -> {
                binding.buttonRegistro.visibility = View.VISIBLE
                binding.buttonLista.visibility = View.VISIBLE
                binding.buttonProfesor.visibility = View.GONE
                binding.buttonUsuarios.visibility = View.GONE
                if (savedInstanceState == null) {
                    mostrarFragmento(RegistroReservaFragment())
                }
            }
            sessionManager.esAdmin() -> {
                binding.buttonRegistro.visibility = View.GONE
                binding.buttonLista.visibility = View.VISIBLE
                binding.buttonProfesor.visibility = View.GONE
                binding.buttonUsuarios.visibility = View.VISIBLE
                if (savedInstanceState == null) {
                    mostrarFragmento(RegistrarUsuarioFragment())
                }
            }
            else -> {
                binding.buttonRegistro.visibility = View.VISIBLE
                binding.buttonLista.visibility = View.VISIBLE
                binding.buttonProfesor.visibility = View.VISIBLE
                binding.buttonUsuarios.visibility = View.GONE
                if (savedInstanceState == null) {
                    mostrarFragmento(RegistroReservaFragment())
                }
            }
        }
    }

    private fun confirmarCerrarSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialogo_cerrar_sesion_titulo)
            .setMessage(R.string.dialogo_cerrar_sesion_mensaje)
            .setPositiveButton(R.string.dialogo_cerrar_sesion_confirmar) { _, _ ->
                sessionManager.cerrarSesion()
                irALogin()
            }
            .setNegativeButton(R.string.dialogo_cancelar, null)
            .show()
    }

    private fun irALogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        sensorLuz?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT) return
        val lux = event.values[0]

        val nuevoModo = when {
            lux < UMBRAL_OSCURO -> true
            lux > UMBRAL_CLARO -> false
            else -> modoOscuro
        }
        if (nuevoModo != modoOscuro) aplicarModo(nuevoModo)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun aplicarModo(oscuro: Boolean) {
        modoOscuro = oscuro
        binding.root.setBackgroundResource(
            if (oscuro) R.drawable.bg_screen_oscuro else R.drawable.bg_screen
        )
    }

    fun abrirLista() {
        mostrarFragmento(ListaReservasFragment())
    }

    private fun mostrarFragmento(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    companion object {
        private const val UMBRAL_OSCURO = 15f   // baja de esto y oscuro
        private const val UMBRAL_CLARO = 40f    // sube de esto y claro
    }
}