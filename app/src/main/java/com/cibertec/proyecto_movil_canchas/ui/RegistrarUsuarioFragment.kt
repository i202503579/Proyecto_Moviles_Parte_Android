package com.cibertec.proyecto_movil_canchas.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cibertec.proyecto_movil_canchas.R
import com.cibertec.proyecto_movil_canchas.data.FirebaseManager
import com.cibertec.proyecto_movil_canchas.databinding.FragmentRegistroUsuarioBinding
import com.cibertec.proyecto_movil_canchas.network.UsuarioDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegistrarUsuarioFragment : Fragment() {

    private var _binding: FragmentRegistroUsuarioBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegistroUsuarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editTextNombre.doAfterTextChanged { binding.inputLayoutNombre.error = null }
        binding.editTextUsername.doAfterTextChanged { binding.inputLayoutUsername.error = null }
        binding.editTextTelefono.doAfterTextChanged { binding.inputLayoutTelefono.error = null }
        binding.editTextPassword.doAfterTextChanged { binding.inputLayoutPassword.error = null }

        binding.buttonGuardarUsuario.setOnClickListener {
            guardarUsuario()
        }

        binding.buttonSincronizarAzure.setOnClickListener {
            sincronizarConAzure()
        }

        actualizarContadorUsuarios()
    }

    private fun guardarUsuario() {
        val nombre = binding.editTextNombre.text.toString().trim()
        val username = binding.editTextUsername.text.toString().trim()
        val telefono = binding.editTextTelefono.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()

        var hayError = false

        if (nombre.isBlank()) {
            binding.inputLayoutNombre.error = getString(R.string.error_nombre_usuario_vacio)
            hayError = true
        }

        if (username.isBlank()) {
            binding.inputLayoutUsername.error = getString(R.string.error_username_vacio)
            hayError = true
        }

        if (telefono.isBlank()) {
            binding.inputLayoutTelefono.error = getString(R.string.error_telefono_vacio)
            hayError = true
        }

        if (password.length < 6) {
            binding.inputLayoutPassword.error = getString(R.string.error_password_corta)
            hayError = true
        }

        if (hayError) return

        val rol = when {
            binding.radioAdmin.isChecked -> "Administrador"
            binding.radioProfesor.isChecked -> "Profesor"
            else -> "Operador"
        }

        val nuevoUsuario = UsuarioDTO(
            idUsuario = 0,
            usernameUsuario = username,
            nombreUsuario = nombre,
            telefono = telefono,
            password = password,
            activoUsuario = true,
            rol = rol
        )

        bloquearUI(true)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val exito = FirebaseManager.registrarUsuario(nuevoUsuario)

            withContext(Dispatchers.Main) {
                bloquearUI(false)
                if (_binding == null) return@withContext

                if (exito) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.usuario_creado_exito),
                        Toast.LENGTH_LONG
                    ).show()
                    limpiarFormulario()
                    actualizarContadorUsuarios()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_crear_usuario),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sincronizarConAzure() {
        bloquearUI(true)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            FirebaseManager.sincronizarUsuariosDesdeAzure()
            withContext(Dispatchers.Main) {
                bloquearUI(false)
                if (_binding != null) {
                    Toast.makeText(
                        requireContext(),
                        R.string.usuarios_sincronizados_azure,
                        Toast.LENGTH_SHORT
                    ).show()
                    actualizarContadorUsuarios()
                }
            }
        }
    }

    private fun actualizarContadorUsuarios() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val lista = FirebaseManager.obtenerUsuarios()
            withContext(Dispatchers.Main) {
                _binding?.textViewTotalUsuarios?.text = getString(
                    R.string.formato_usuarios_activos_firebase,
                    lista.size
                )
            }
        }
    }

    private fun limpiarFormulario() {
        binding.editTextNombre.text?.clear()
        binding.editTextUsername.text?.clear()
        binding.editTextTelefono.text?.clear()
        binding.editTextPassword.text?.clear()
        binding.radioOperador.isChecked = true
        binding.inputLayoutNombre.error = null
        binding.inputLayoutUsername.error = null
        binding.inputLayoutTelefono.error = null
        binding.inputLayoutPassword.error = null
    }

    private fun bloquearUI(bloquear: Boolean) {
        binding.buttonGuardarUsuario.isEnabled = !bloquear
        binding.buttonSincronizarAzure.isEnabled = !bloquear
        binding.progressGuardar.visibility = if (bloquear) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}