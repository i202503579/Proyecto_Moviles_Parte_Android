package com.cibertec.proyecto_movil_canchas

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.cibertec.proyecto_movil_canchas.databinding.ActivityMainBinding
import com.cibertec.proyecto_movil_canchas.ui.ListaReservasFragment
import com.cibertec.proyecto_movil_canchas.ui.RegistroReservaFragment


class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var sensorLuz: Sensor? = null
    private var modoOscuro = false
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sensorManager = getSystemService(SensorManager::class.java)
        sensorLuz = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        binding.buttonRegistro.setOnClickListener {
            mostrarFragmento(RegistroReservaFragment())
        }

        binding.buttonLista.setOnClickListener {
            abrirLista()
        }

        if (savedInstanceState == null) {
            mostrarFragmento(RegistroReservaFragment())
        }
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