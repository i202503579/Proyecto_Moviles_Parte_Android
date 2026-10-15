package com.cibertec.proyecto_movil_canchas.network

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface ApiService {

    @GET("api/UsuariosAPI/GetUsuarios")
    fun obtenerUsuarios(): Call<List<UsuarioDTO>>

    @GET("api/CanchasAPI/GetCanchas")
    fun obtenerCanchas(): Call<List<CanchaDTO>>

    @GET("api/ClasesAPI/GetClases")
    fun obtenerClases(): Call<List<ClaseDTO>>

    @PUT("api/ClasesAPI")
    fun actualizarClase(@Body clase: ClaseDTO): Call<ResponseBody>

    @GET("api/AlumnosAPI/GetAlumnos")
    fun obtenerAlumnos(): Call<List<AlumnoDTO>>

    @GET("api/AsistenciasAPI/GetAsistencias")
    fun obtenerAsistencias(): Call<List<AsistenciaDTO>>

    @POST("api/AsistenciasAPI")
    fun registrarAsistencia(@Body asistencia: AsistenciaDTO): Call<ResponseBody>

    @PUT("api/AsistenciasAPI")
    fun actualizarAsistencia(@Body asistencia: AsistenciaDTO): Call<ResponseBody>

    // --- Endpoints para SyncRepository (Corrutinas) ---
    @GET("api/UsuariosAPI/GetUsuarios")
    suspend fun getUsuarios(): List<UsuarioDTO>

    @GET("api/CanchasAPI/GetCanchas")
    suspend fun getCanchas(): List<CanchaDTO>

    @GET("api/ClientesAPI/GetClientes")
    suspend fun getClientes(): List<ClienteDTO>

    @POST("api/ClientesAPI")
    suspend fun postCliente(@Body cliente: ClienteDTO): retrofit2.Response<ResponseBody>

    @PUT("api/ClientesAPI")
    suspend fun putCliente(@Body cliente: ClienteDTO): retrofit2.Response<ResponseBody>

    @GET("api/ReservasAPI/GetReservas")
    suspend fun getReservas(): List<ReservaDTO>

    @POST("api/ReservasAPI")
    suspend fun postReserva(@Body reserva: ReservaDTO): retrofit2.Response<ResponseBody>

    @PUT("api/ReservasAPI")
    suspend fun putReserva(@Body reserva: ReservaDTO): retrofit2.Response<ResponseBody>
}