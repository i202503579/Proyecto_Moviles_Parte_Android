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

    @GET("api/ReservasAPI/GetReservas")
    fun obtenerReservas(): Call<List<ReservaDTO>>

    @POST("api/ReservasAPI")
    fun registrarReserva(@Body reserva: ReservaDTO): Call<ResponseBody>
}