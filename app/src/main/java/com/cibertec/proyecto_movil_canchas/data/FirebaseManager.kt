package com.cibertec.proyecto_movil_canchas.data

import android.util.Log
import com.cibertec.proyecto_movil_canchas.network.AlumnoDTO
import com.cibertec.proyecto_movil_canchas.network.AsistenciaDTO
import com.cibertec.proyecto_movil_canchas.network.CanchaDTO
import com.cibertec.proyecto_movil_canchas.network.ClaseDTO
import com.cibertec.proyecto_movil_canchas.network.ClienteDTO
import com.cibertec.proyecto_movil_canchas.network.ReservaDTO
import com.cibertec.proyecto_movil_canchas.network.RetrofitClient
import com.cibertec.proyecto_movil_canchas.network.UsuarioDTO
import com.cibertec.proyecto_movil_canchas.model.AlumnoAsistenciaItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

object FirebaseManager {

    private const val TAG = "FirebaseManager"
    private const val DOMINIO_CORREO_DEFAULT = "@canchitas.com"

    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    fun formatearCorreo(usernameOUsuario: String): String {
        val limpio = usernameOUsuario.trim()
        return if (limpio.contains("@")) {
            limpio
        } else {
            "${limpio.lowercase()}$DOMINIO_CORREO_DEFAULT"
        }
    }

    /**
     * Revisa si Firestore ya tiene datos. Si está vacío, precarga las canchas,
     * usuarios, clases y alumnos iniciales automáticamente en la nube.
     */
    suspend fun inicializarDatosSiEsNecesario() {
        try {
            val snapshotCanchas = db.collection("canchas").limit(1).get().await()
            if (snapshotCanchas.isEmpty) {
                Log.d(TAG, "Firestore está vacío. Precargando datos iniciales...")
                sembrarCanchas()
                sembrarUsuarios()
                sembrarAlumnos()
                sembrarClases()
                Log.d(TAG, "Precarga de datos en Firestore completada exitosamente.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar o sembrar datos en Firestore: ${e.message}")
        }
    }

    private suspend fun sembrarCanchas() {
        val canchasIniciales = listOf(
            CanchaDTO(1, "Cancha 1 - Grass Sintetico", "Cancha de futbol 5 con grass sintetico e iluminacion LED", 20.0, true),
            CanchaDTO(2, "Cancha 2 - Techada Premium", "Cancha techada", 30.0, true),
            CanchaDTO(3, "Cancha 3 - Futbol 7", "Cancha amplia para 7 vs 7", 40.0, true),
            CanchaDTO(4, "Cancha 4 - Grass Natural", "Cancha de entrenamiento y academia deportiva", 45.0, true)
        )

        for (cancha in canchasIniciales) {
            val datos = hashMapOf(
                "idCancha" to cancha.idCancha,
                "nombreCancha" to cancha.nombreCancha,
                "descripcionCancha" to cancha.descripcionCancha,
                "precioAlquiler" to cancha.precioAlquiler,
                "activoCancha" to cancha.activoCancha
            )
            db.collection("canchas").document(cancha.idCancha.toString()).set(datos, SetOptions.merge()).await()
        }
    }

    private suspend fun sembrarUsuarios() {
        val usuariosIniciales = listOf(
            UsuarioDTO(1, "admin", "Administrador Principal", "999000143", "admin123", true, "Administrador"),
            UsuarioDTO(2, "operador1", "Carlos Recepción", "999000222", "123456", true, "Operador"),
            UsuarioDTO(3, "profe_miguel", "Miguel Angel Quispe", "987333444", "123456", true, "Profesor"),
            UsuarioDTO(4, "profe_jorge", "Jorge Luis Ramos", "987555666", "123456", true, "Profesor")
        )

        for (usuario in usuariosIniciales) {
            val email = formatearCorreo(usuario.usernameUsuario)

            // 1. Guardar perfil completo en colección Firestore
            val datos = hashMapOf(
                "idUsuario" to usuario.idUsuario,
                "usernameUsuario" to usuario.usernameUsuario,
                "nombreUsuario" to usuario.nombreUsuario,
                "telefono" to usuario.telefono,
                "email" to email,
                "rol" to usuario.rol,
                "activoUsuario" to usuario.activoUsuario
            )
            db.collection("usuarios").document(usuario.idUsuario.toString()).set(datos, SetOptions.merge()).await()

            // 2. Crear cuenta en Firebase Authentication si aún no existe
            try {
                auth.createUserWithEmailAndPassword(email, usuario.password).await()
            } catch (_: Exception) {
                // Si la cuenta ya existe en Firebase Auth, no hay problema
            }
        }
    }

    private suspend fun sembrarAlumnos() {
        val alumnosIniciales = listOf(
            AlumnoDTO(1, "Carlos Daniel Quispe", "987111222", "2026-03-01", true, 3),
            AlumnoDTO(2, "Mateo Sebastian Ramos", "987333444", "2026-03-05", true, 3),
            AlumnoDTO(3, "Lucas Santiago Flores", "987555666", "2026-03-10", true, 4),
            AlumnoDTO(4, "Joaquin Andres Vargas", "987777888", "2026-03-12", true, 4)
        )

        for (alumno in alumnosIniciales) {
            val datos = hashMapOf(
                "idAlumno" to alumno.idAlumno,
                "nombreAlumno" to alumno.nombreAlumno,
                "telefonoApoderado" to alumno.telefonoApoderado,
                "fechaIngreso" to alumno.fechaIngreso,
                "activoAlumno" to alumno.activoAlumno,
                "idUsuario" to alumno.idUsuario
            )
            db.collection("alumnos").document(alumno.idAlumno.toString()).set(datos, SetOptions.merge()).await()
        }
    }

    private suspend fun sembrarClases() {
        // Las clases inician vacías para que el usuario las cree manualmente
    }

    /**
     * Autentica el usuario contra Firebase Authentication y recupera su perfil y rol desde Firestore.
     */
    suspend fun autenticar(usernameOUsuario: String, pass: String): UsuarioDTO? {
        val email = formatearCorreo(usernameOUsuario)

        // 1. Intentar autenticar con Firebase Authentication
        runCatching {
            auth.signInWithEmailAndPassword(email, pass).await()
        }

        // 2. Buscar perfil en Firestore por usernameUsuario o por email
        val queryUser = runCatching {
            db.collection("usuarios")
                .whereEqualTo("usernameUsuario", usernameOUsuario.trim())
                .limit(1)
                .get()
                .await()
        }.getOrNull()

        val doc = if (queryUser != null && !queryUser.isEmpty) {
            queryUser.documents.first()
        } else {
            runCatching {
                val queryEmail = db.collection("usuarios")
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .await()
                queryEmail.documents.firstOrNull()
            }.getOrNull()
        }

        if (doc != null) {
            return UsuarioDTO(
                idUsuario = (doc.getLong("idUsuario") ?: 0L).toInt(),
                usernameUsuario = doc.getString("usernameUsuario") ?: usernameOUsuario,
                nombreUsuario = doc.getString("nombreUsuario") ?: "",
                telefono = doc.getString("telefono") ?: "",
                password = pass,
                activoUsuario = doc.getBoolean("activoUsuario") ?: true,
                rol = doc.getString("rol") ?: "Operador"
            )
        }
        return null
    }

    suspend fun obtenerCanchas(): List<CanchaDTO> {
        return try {
            val snapshot = db.collection("canchas").get().await()
            snapshot.documents.mapNotNull { doc ->
                CanchaDTO(
                    idCancha = (doc.getLong("idCancha") ?: 0L).toInt(),
                    nombreCancha = doc.getString("nombreCancha").orEmpty(),
                    descripcionCancha = doc.getString("descripcionCancha").orEmpty(),
                    precioAlquiler = doc.getDouble("precioAlquiler") ?: 0.0,
                    activoCancha = doc.getBoolean("activoCancha") ?: true
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun guardarReserva(reserva: ReservaDTO): Boolean {
        return try {
            val idStr = if (reserva.idReserva > 0) reserva.idReserva.toString() else System.currentTimeMillis().toString()
            val datos = hashMapOf(
                "idReserva" to (if (reserva.idReserva > 0) reserva.idReserva else idStr.takeLast(6).toInt()),
                "horaInicio" to reserva.horaInicio,
                "horaFin" to reserva.horaFin,
                "precioReserva" to reserva.precioReserva,
                "estadoReserva" to reserva.estadoReserva,
                "montoRecibido" to reserva.montoRecibido,
                "idCliente" to reserva.idCliente,
                "idCancha" to reserva.idCancha,
                "idUsuario" to reserva.idUsuario
            )
            db.collection("reservas").document(idStr).set(datos, SetOptions.merge()).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun obtenerReservas(): List<ReservaDTO> {
        return try {
            val snapshot = db.collection("reservas").get().await()
            snapshot.documents.mapNotNull { doc ->
                ReservaDTO(
                    idReserva = (doc.getLong("idReserva") ?: 0L).toInt(),
                    horaInicio = doc.getString("horaInicio").orEmpty(),
                    horaFin = doc.getString("horaFin").orEmpty(),
                    precioReserva = doc.getDouble("precioReserva") ?: 0.0,
                    estadoReserva = doc.getString("estadoReserva") ?: "Pendiente",
                    montoRecibido = doc.getDouble("montoRecibido") ?: 0.0,
                    idCliente = (doc.getLong("idCliente") ?: 0L).toInt(),
                    idCancha = (doc.getLong("idCancha") ?: 0L).toInt(),
                    idUsuario = (doc.getLong("idUsuario") ?: 1L).toInt()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun guardarAsistencias(
        idClase: Int,
        alumnos: List<AlumnoAsistenciaItem>,
        marcarCompletada: Boolean
    ): Boolean {
        return try {
            for (item in alumnos) {
                val docId = "${idClase}_${item.idAlumno}"
                val datos = hashMapOf(
                    "idAsistencia" to item.idAsistenciaServidor,
                    "idClase" to idClase,
                    "idAlumno" to item.idAlumno,
                    "nombreAlumno" to item.nombreAlumno,
                    "estadoAsistencia" to item.estadoAsistencia,
                    "actualizadoEn" to System.currentTimeMillis()
                )
                db.collection("asistencias").document(docId).set(datos, SetOptions.merge()).await()
            }
            if (marcarCompletada) {
                val datosClase = hashMapOf<String, Any>(
                    "estadoClase" to "Completada"
                )
                db.collection("clases").document(idClase.toString()).set(datosClase, SetOptions.merge()).await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar asistencias en Firestore: ${e.message}")
            false
        }
    }

    suspend fun guardarAsistenciaIndividual(
        idClase: Int,
        idAlumno: Int,
        nombreAlumno: String,
        estadoAsistencia: String
    ): Boolean {
        return try {
            val docId = "${idClase}_$idAlumno"
            val datos = hashMapOf(
                "idClase" to idClase,
                "idAlumno" to idAlumno,
                "nombreAlumno" to nombreAlumno,
                "estadoAsistencia" to estadoAsistencia,
                "actualizadoEn" to System.currentTimeMillis()
            )
            db.collection("asistencias").document(docId).set(datos, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar asistencia individual en Firestore: ${e.message}")
            false
        }
    }

    suspend fun actualizarEstadoClase(idClase: Int, nuevoEstado: String): Boolean {
        return try {
            val datos = hashMapOf<String, Any>(
                "estadoClase" to nuevoEstado
            )
            db.collection("clases").document(idClase.toString()).set(datos, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar estado clase en Firestore: ${e.message}")
            false
        }
    }

    suspend fun obtenerClases(): List<ClaseDTO> {
        return try {
            val snapshot = db.collection("clases").get().await()
            snapshot.documents.mapNotNull { doc ->
                ClaseDTO(
                    idClase = (doc.getLong("idClase") ?: 0L).toInt(),
                    idUsuario = (doc.getLong("idUsuario") ?: 0L).toInt(),
                    idCancha = (doc.getLong("idCancha") ?: 0L).toInt(),
                    fechaClase = doc.getString("fechaClase").orEmpty(),
                    horaInicio = doc.getString("horaInicio").orEmpty(),
                    horaFin = doc.getString("horaFin").orEmpty(),
                    estadoClase = doc.getString("estadoClase") ?: "Programada",
                    activoClase = doc.getBoolean("activoClase") ?: true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener clases de Firestore: ${e.message}")
            emptyList()
        }
    }

    suspend fun obtenerAlumnos(): List<AlumnoDTO> {
        return try {
            val snapshot = db.collection("alumnos").get().await()
            snapshot.documents.mapNotNull { doc ->
                AlumnoDTO(
                    idAlumno = (doc.getLong("idAlumno") ?: 0L).toInt(),
                    nombreAlumno = doc.getString("nombreAlumno").orEmpty(),
                    telefonoApoderado = doc.getString("telefonoApoderado"),
                    fechaIngreso = doc.getString("fechaIngreso"),
                    activoAlumno = doc.getBoolean("activoAlumno") ?: true,
                    idUsuario = (doc.getLong("idUsuario") ?: 0L).toInt()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener alumnos de Firestore: ${e.message}")
            emptyList()
        }
    }

    suspend fun obtenerUsuarios(): List<UsuarioDTO> {
        return try {
            val snapshot = db.collection("usuarios").get().await()
            snapshot.documents.mapNotNull { doc ->
                UsuarioDTO(
                    idUsuario = (doc.getLong("idUsuario") ?: 0L).toInt(),
                    usernameUsuario = doc.getString("usernameUsuario").orEmpty(),
                    nombreUsuario = doc.getString("nombreUsuario").orEmpty(),
                    telefono = doc.getString("telefono").orEmpty(),
                    password = "",
                    activoUsuario = doc.getBoolean("activoUsuario") ?: true,
                    rol = doc.getString("rol").orEmpty()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener usuarios de Firestore: ${e.message}")
            emptyList()
        }
    }

    suspend fun obtenerAsistencias(): List<AsistenciaDTO> {
        return try {
            val snapshot = db.collection("asistencias").get().await()
            snapshot.documents.mapNotNull { doc ->
                AsistenciaDTO(
                    idAsistencia = (doc.getLong("idAsistencia") ?: 0L).toInt(),
                    idClase = (doc.getLong("idClase") ?: 0L).toInt(),
                    idAlumno = (doc.getLong("idAlumno") ?: 0L).toInt(),
                    estadoAsistencia = doc.getString("estadoAsistencia") ?: "Presente"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener asistencias de Firestore: ${e.message}")
            emptyList()
        }
    }

    suspend fun sincronizarAlumno(alumno: AlumnoDTO) {
        try {
            val d = hashMapOf(
                "idAlumno" to alumno.idAlumno,
                "nombreAlumno" to alumno.nombreAlumno,
                "telefonoApoderado" to (alumno.telefonoApoderado ?: ""),
                "fechaIngreso" to (alumno.fechaIngreso ?: ""),
                "activoAlumno" to alumno.activoAlumno,
                "idUsuario" to alumno.idUsuario
            )
            db.collection("alumnos").document(alumno.idAlumno.toString()).set(d, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w(TAG, "Aviso sincronizando alumno en Firestore: ${e.message}")
        }
    }

    suspend fun sincronizarClase(clase: ClaseDTO) {
        try {
            val d = hashMapOf(
                "idClase" to clase.idClase,
                "idUsuario" to clase.idUsuario,
                "idCancha" to clase.idCancha,
                "fechaClase" to clase.fechaClase,
                "horaInicio" to clase.horaInicio,
                "horaFin" to clase.horaFin,
                "estadoClase" to clase.estadoClase,
                "activoClase" to clase.activoClase
            )
            db.collection("clases").document(clase.idClase.toString()).set(d, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w(TAG, "Aviso sincronizando clase en Firestore: ${e.message}")
        }
    }
    // Replica clientes a Firestore SIN suspender ni esperar al servidor.
    // Firestore guarda la escritura localmente y la envía cuando hay red.
    fun replicarClientes(clientes: List<ClienteDTO>) {
        // Un lote admite máximo 500 operaciones
        clientes.filter { it.numeroDocumento.isNotBlank() }.chunked(400).forEach { grupo ->
            val lote = db.batch()
            for (c in grupo) {
                val docLimpio = c.numeroDocumento.trim()
                val datos = hashMapOf(
                    "idCliente" to c.idCliente,
                    "nombreCliente" to c.nombreCliente,
                    "numeroDocumento" to docLimpio,
                    "telefonoCliente" to (c.telefonoCliente ?: ""),
                    "tipoCliente" to c.tipoCliente
                )
                lote.set(db.collection("clientes").document(docLimpio), datos, SetOptions.merge())
            }
            lote.commit().addOnFailureListener {
                Log.w(TAG, "Aviso replicando clientes en Firestore: ${it.message}")
            }
        }
    }
    // Replica alumnos y clases a Firestore en lotes, SIN suspender ni esperar al servidor.
    fun replicarAlumnosYClases(alumnos: List<AlumnoDTO>, clases: List<ClaseDTO>) {
        alumnos.chunked(400).forEach { grupo ->
            val lote = db.batch()
            for (al in grupo) {
                val datos = hashMapOf(
                    "idAlumno" to al.idAlumno,
                    "nombreAlumno" to al.nombreAlumno,
                    "telefonoApoderado" to (al.telefonoApoderado ?: ""),
                    "fechaIngreso" to (al.fechaIngreso ?: ""),
                    "activoAlumno" to al.activoAlumno,
                    "idUsuario" to al.idUsuario
                )
                lote.set(db.collection("alumnos").document(al.idAlumno.toString()), datos, SetOptions.merge())
            }
            lote.commit().addOnFailureListener {
                Log.w(TAG, "Aviso replicando alumnos en Firestore: ${it.message}")
            }
        }
        clases.chunked(400).forEach { grupo ->
            val lote = db.batch()
            for (cl in grupo) {
                val datos = hashMapOf(
                    "idClase" to cl.idClase,
                    "idUsuario" to cl.idUsuario,
                    "idCancha" to cl.idCancha,
                    "fechaClase" to cl.fechaClase,
                    "horaInicio" to cl.horaInicio,
                    "horaFin" to cl.horaFin,
                    "estadoClase" to cl.estadoClase,
                    "activoClase" to cl.activoClase
                )
                lote.set(db.collection("clases").document(cl.idClase.toString()), datos, SetOptions.merge())
            }
            lote.commit().addOnFailureListener {
                Log.w(TAG, "Aviso replicando clases en Firestore: ${it.message}")
            }
        }
    }
    suspend fun sincronizarCliente(cliente: ClienteDTO) {
        val docLimpio = cliente.numeroDocumento.trim()
        if (docLimpio.isBlank()) return
        try {
            val d = hashMapOf(
                "idCliente" to cliente.idCliente,
                "nombreCliente" to cliente.nombreCliente,
                "numeroDocumento" to docLimpio,
                "telefonoCliente" to (cliente.telefonoCliente ?: ""),
                "tipoCliente" to cliente.tipoCliente
            )
            db.collection("clientes").document(docLimpio).set(d, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w(TAG, "Aviso sincronizando cliente en Firestore: ${e.message}")
        }
    }

    suspend fun obtenerClientes(): List<ClienteDTO> {
        return try {
            val snapshot = db.collection("clientes").get().await()
            snapshot.documents.mapNotNull { doc ->
                val numDoc = doc.getString("numeroDocumento") ?: doc.id
                ClienteDTO(
                    idCliente = (doc.getLong("idCliente") ?: 0L).toInt(),
                    nombreCliente = doc.getString("nombreCliente").orEmpty(),
                    numeroDocumento = numDoc,
                    telefonoCliente = doc.getString("telefonoCliente"),
                    tipoCliente = doc.getString("tipoCliente") ?: "Particular"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener clientes de Firestore: ${e.message}")
            emptyList()
        }
    }

    suspend fun buscarClienteEnFirestore(documento: String): ClienteDTO? {
        val docLimpio = documento.trim()
        if (docLimpio.isBlank()) return null
        return try {
            val docSnap = db.collection("clientes").document(docLimpio).get().await()
            if (docSnap.exists()) {
                ClienteDTO(
                    idCliente = (docSnap.getLong("idCliente") ?: 0L).toInt(),
                    nombreCliente = docSnap.getString("nombreCliente").orEmpty(),
                    numeroDocumento = docSnap.getString("numeroDocumento") ?: docLimpio,
                    telefonoCliente = docSnap.getString("telefonoCliente"),
                    tipoCliente = docSnap.getString("tipoCliente") ?: "Particular"
                )
            } else {
                val querySnap = db.collection("clientes").whereEqualTo("numeroDocumento", docLimpio).get().await()
                querySnap.documents.firstOrNull()?.let { d ->
                    ClienteDTO(
                        idCliente = (d.getLong("idCliente") ?: 0L).toInt(),
                        nombreCliente = d.getString("nombreCliente").orEmpty(),
                        numeroDocumento = d.getString("numeroDocumento") ?: docLimpio,
                        telefonoCliente = d.getString("telefonoCliente"),
                        tipoCliente = d.getString("tipoCliente") ?: "Particular"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error buscando cliente en Firestore: ${e.message}")
            null
        }
    }

    suspend fun registrarUsuario(usuario: UsuarioDTO): Boolean {
        val usernameLimpio = usuario.usernameUsuario.trim().lowercase()
        if (usernameLimpio.isBlank() || usuario.password.length < 6) return false
        val email = formatearCorreo(usernameLimpio)

        var idAsignado = usuario.idUsuario

        // 1. Guardar en Azure SQL vía Web API
        try {
            val resp = RetrofitClient.apiService.postUsuario(usuario)
            if (resp.isSuccessful) {
                val lista = RetrofitClient.apiService.getUsuarios()
                val creado = lista.find { it.usernameUsuario.trim().equals(usernameLimpio, ignoreCase = true) }
                if (creado != null) {
                    idAsignado = creado.idUsuario
                }
                Log.i(TAG, "Usuario $usernameLimpio creado en Azure con ID $idAsignado")
            } else {
                Log.w(TAG, "Aviso POST usuario Azure: HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aviso creando usuario en Azure (modo offline): ${e.message}")
        }

        // 2. Crear en Firebase Authentication
        try {
            auth.createUserWithEmailAndPassword(email, usuario.password).await()
            Log.i(TAG, "Usuario $email creado en Firebase Auth")
        } catch (e: Exception) {
            Log.w(TAG, "Aviso Auth (puede que ya exista): ${e.message}")
        }

        // 3. Guardar en Cloud Firestore
        try {
            val docId = if (idAsignado > 0) idAsignado.toString() else usernameLimpio
            val datos = hashMapOf(
                "idUsuario" to idAsignado,
                "usernameUsuario" to usernameLimpio,
                "nombreUsuario" to usuario.nombreUsuario.trim(),
                "telefono" to usuario.telefono.trim(),
                "email" to email,
                "rol" to usuario.rol,
                "password" to usuario.password,
                "activoUsuario" to usuario.activoUsuario
            )
            db.collection("usuarios").document(docId).set(datos, SetOptions.merge()).await()
            Log.i(TAG, "Usuario $docId guardado en Firestore")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando usuario en Firestore: ${e.message}")
            return idAsignado > 0
        }
    }

    suspend fun sincronizarUsuariosDesdeAzure() {
        try {
            val usuariosAzure = RetrofitClient.apiService.getUsuarios()
            for (u in usuariosAzure) {
                val email = formatearCorreo(u.usernameUsuario)
                if (u.password.length >= 6) {
                    try {
                        auth.createUserWithEmailAndPassword(email, u.password).await()
                    } catch (_: Exception) {}
                }
                val datos = hashMapOf(
                    "idUsuario" to u.idUsuario,
                    "usernameUsuario" to u.usernameUsuario.trim(),
                    "nombreUsuario" to u.nombreUsuario.trim(),
                    "telefono" to u.telefono.trim(),
                    "email" to email,
                    "rol" to u.rol,
                    "password" to u.password,
                    "activoUsuario" to u.activoUsuario
                )
                db.collection("usuarios").document(u.idUsuario.toString()).set(datos, SetOptions.merge()).await()
            }
            Log.d(TAG, "Usuarios de Azure sincronizados a Firebase exitosamente.")
        } catch (e: Exception) {
            Log.w(TAG, "Error sincronizando usuarios de Azure a Firebase: ${e.message}")
        }
    }
}
