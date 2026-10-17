package com.cibertec.proyecto_movil_canchas.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cibertec.proyecto_movil_canchas.MainActivity
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.data.AcademiaStore
import com.cibertec.proyecto_movil_canchas.data.FirebaseManager
import com.cibertec.proyecto_movil_canchas.data.SessionManager
import com.cibertec.proyecto_movil_canchas.databinding.ActivityLoginBinding
import com.cibertec.proyecto_movil_canchas.network.RetrofitClient
import com.cibertec.proyecto_movil_canchas.network.UsuarioDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        if (sessionManager.estaLogueado()) {
            irAMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar datos en Firebase Firestore en segundo plano si está vacío
        lifecycleScope.launch(Dispatchers.IO) {
            FirebaseManager.inicializarDatosSiEsNecesario()
        }

        configurarAccesosRapidos()

        binding.buttonLogin.setOnClickListener {
            ejecutarLogin()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun configurarAccesosRapidos() {
        binding.chipOperador.setOnClickListener {
            binding.editTextUsuario.setText("operador1")
            binding.editTextPassword.setText("123456")
            binding.textViewErrorLogin.visibility = View.GONE
        }
        binding.chipProfesor.setOnClickListener {
            binding.editTextUsuario.setText("profe_miguel")
            binding.editTextPassword.setText("123456")
            binding.textViewErrorLogin.visibility = View.GONE
        }
        binding.chipAdmin.setOnClickListener {
            binding.editTextUsuario.setText("admin")
            binding.editTextPassword.setText("admin123")
            binding.textViewErrorLogin.visibility = View.GONE
        }
    }

    private fun ejecutarLogin() {
        val username = binding.editTextUsuario.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()

        binding.inputLayoutUsuario.error = null
        binding.inputLayoutPassword.error = null
        binding.textViewErrorLogin.visibility = View.GONE

        var valido = true
        if (username.isBlank()) {
            binding.inputLayoutUsuario.error = getString(R.string.login_error_usuario_vacio)
            valido = false
        }
        if (password.isBlank()) {
            binding.inputLayoutPassword.error = getString(R.string.login_error_password_vacio)
            valido = false
        }
        if (!valido) return

        bloquearUI(true)

        lifecycleScope.launch(Dispatchers.IO) {
            val usuarioEncontrado = validarCredenciales(username, password)

            withContext(Dispatchers.Main) {
                bloquearUI(false)

                if (usuarioEncontrado != null) {
                    if (!usuarioEncontrado.activoUsuario) {
                        binding.textViewErrorLogin.text = getString(R.string.login_error_usuario_inactivo)
                        binding.textViewErrorLogin.visibility = View.VISIBLE
                        return@withContext
                    }

                    sessionManager.guardarSesion(usuarioEncontrado)
                    Toast.makeText(
                        this@LoginActivity,
                        getString(
                            R.string.login_bienvenida_formato,
                            usuarioEncontrado.nombreUsuario,
                            usuarioEncontrado.rol
                        ),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Si es profesor, precargar sus clases en segundo plano
                    if (sessionManager.esProfesor()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                AcademiaStore(applicationContext).sincronizarConWeb()
                            } catch (_: Exception) {}
                        }
                    }

                    // Si es admin, sincronizar usuarios de Azure a Firebase en segundo plano
                    if (sessionManager.esAdmin()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                FirebaseManager.sincronizarUsuariosDesdeAzure()
                            } catch (_: Exception) {}
                        }
                    }

                    irAMain()
                } else {
                    binding.textViewErrorLogin.text = getString(R.string.login_error_credenciales)
                    binding.textViewErrorLogin.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun validarCredenciales(user: String, pass: String): UsuarioDTO? {
        try {
            val usuarioFirebase = FirebaseManager.autenticar(user, pass)
            if (usuarioFirebase != null) return usuarioFirebase
        } catch (_: Exception) {}

        try {
            val listaRemota = RetrofitClient.apiService.getUsuarios()
            val encontrado = listaRemota.find {
                it.usernameUsuario.trim().equals(user, ignoreCase = true) &&
                it.password.trim() == pass
            }
            if (encontrado != null) return encontrado
        } catch (_: Exception) {}

        val cuentasRespaldo = listOf(
            UsuarioDTO(1, "admin", "Administrador Principal", "999000143", "admin123", true, "Administrador"),
            UsuarioDTO(2, "operador1", "Carlos Recepción", "999000222", "123456", true, "Operador"),
            UsuarioDTO(3, "profe_miguel", "Miguel Angel Quispe", "987333444", "123456", true, "Profesor"),
            UsuarioDTO(4, "profe_jorge", "Jorge Luis Ramos", "987555666", "123456", true, "Profesor")
        )
        return cuentasRespaldo.find {
            it.usernameUsuario.trim().equals(user, ignoreCase = true) &&
            it.password.trim() == pass
        }
    }

    private fun bloquearUI(bloquear: Boolean) {
        binding.buttonLogin.isEnabled = !bloquear
        binding.editTextUsuario.isEnabled = !bloquear
        binding.editTextPassword.isEnabled = !bloquear
        binding.chipOperador.isEnabled = !bloquear
        binding.chipProfesor.isEnabled = !bloquear
        binding.chipAdmin.isEnabled = !bloquear
        binding.progressLogin.visibility = if (bloquear) View.VISIBLE else View.GONE
    }

    private fun irAMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
