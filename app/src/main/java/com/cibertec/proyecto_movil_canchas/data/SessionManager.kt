package com.cibertec.proyecto_movil_canchas.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.cibertec.proyecto_movil_canchas.network.UsuarioDTO

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun guardarSesion(usuario: UsuarioDTO) {
        prefs.edit {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putInt(KEY_ID_USUARIO, usuario.idUsuario)
            putString(KEY_USERNAME, usuario.usernameUsuario)
            putString(KEY_NOMBRE, usuario.nombreUsuario)
            putString(KEY_TELEFONO, usuario.telefono)
            putString(KEY_ROL, usuario.rol)
            putBoolean(KEY_ACTIVO, usuario.activoUsuario)
        }
    }

    fun estaLogueado(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun obtenerUsuario(): UsuarioDTO? {
        if (!estaLogueado()) return null
        return UsuarioDTO(
            idUsuario = prefs.getInt(KEY_ID_USUARIO, 0),
            usernameUsuario = prefs.getString(KEY_USERNAME, "").orEmpty(),
            nombreUsuario = prefs.getString(KEY_NOMBRE, "").orEmpty(),
            telefono = prefs.getString(KEY_TELEFONO, "").orEmpty(),
            password = "",
            activoUsuario = prefs.getBoolean(KEY_ACTIVO, true),
            rol = prefs.getString(KEY_ROL, "Operador").orEmpty()
        )
    }

    fun obtenerIdUsuario(): Int = prefs.getInt(KEY_ID_USUARIO, 0)

    @Suppress("unused")
    fun obtenerNombreUsuario(): String = prefs.getString(KEY_NOMBRE, "").orEmpty()

    fun obtenerRol(): String = prefs.getString(KEY_ROL, "Operador").orEmpty()

    fun esOperador(): Boolean {
        val rol = obtenerRol()
        return rol.equals("Operador", ignoreCase = true) || rol.equals("Recepcionista", ignoreCase = true)
    }

    fun esProfesor(): Boolean {
        val rol = obtenerRol()
        return rol.equals("Profesor", ignoreCase = true) || rol.equals("Entrenador", ignoreCase = true)
    }

    @Suppress("unused")
    fun esAdmin(): Boolean {
        val rol = obtenerRol()
        return rol.equals("Administrador", ignoreCase = true) || rol.equals("Admin", ignoreCase = true)
    }

    fun cerrarSesion() {
        prefs.edit {
            clear()
        }
    }

    companion object {
        private const val PREF_NAME = "sesion_usuario_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_ID_USUARIO = "id_usuario"
        private const val KEY_USERNAME = "username"
        private const val KEY_NOMBRE = "nombre"
        private const val KEY_TELEFONO = "telefono"
        private const val KEY_ROL = "rol"
        private const val KEY_ACTIVO = "activo"
    }
}
