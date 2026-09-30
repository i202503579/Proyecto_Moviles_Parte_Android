package com.cibertec.proyecto_movil_canchas

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.cibertec.proyecto_movil_canchas.databinding.ActivityMainBinding
import com.cibertec.proyecto_movil_cancha.canchitas.ui.ListaReservasFragment
import com.cibertec.canchitas.ui.RegistroReservaFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    fun abrirLista() {
        mostrarFragmento(ListaReservasFragment())
    }

    private fun mostrarFragmento(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}