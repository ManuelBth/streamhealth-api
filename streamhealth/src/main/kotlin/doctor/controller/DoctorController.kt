package com.betha.doctor.controller

import com.betha.auth.service.AuthService
import com.betha.common.document.Rol
import com.betha.doctor.dto.CreateDoctorProfileRequest
import com.betha.doctor.dto.UpdateDoctorProfileRequest
import com.betha.doctor.service.DoctorService
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Doctor controller with professional profile routes
 *
 * Endpoints:
 * - GET /api/v1/doctors - List all doctors (requires JWT auth)
 * - GET /api/v1/doctors/{idNumber} - Get doctor by ID Number/Cédula (requires JWT auth)
 * - POST /api/v1/doctors - Create doctor profile (DOCTOR role only)
 * - PUT /api/v1/doctors - Update doctor profile (DOCTOR role only)
 *
 * @see DoctorResponse for response structure
 * @see CreateDoctorProfileRequest for creation fields
 * @see UpdateDoctorProfileRequest for update fields
 */
fun Routing.doctorController(
    doctorService: DoctorService,
    authService: AuthService
) {
    route("/api/v1/doctors") {
        /**
         * List all doctors
         *
         * Retrieves all doctors with their professional profiles
         *
         * @header Authorization Bearer JWT token
         * @return List of DoctorResponse
         */
        get {
            try {
                val authHeader = call.request.headers["Authorization"]
                    ?: throw IllegalArgumentException("Token requerido")

                val token = authHeader.removePrefix("Bearer ")
                val userInfo = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

                val doctors = doctorService.getAllDoctors()
                call.respond(doctors)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.Unauthorized,
                    message = mapOf("error" to (e.message ?: "Error de autenticación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * Get doctor by ID Number (Cédula)
         *
         * Retrieves a specific doctor's profile by their user ID (cedula)
         *
         * @param idNumber Path parameter - The user's identification number (Cédula)
         * @header Authorization Bearer JWT token
         * @return DoctorResponse with doctor profile data
         */
        get("/{idNumber}") {
            try {
                val authHeader = call.request.headers["Authorization"]
                    ?: throw IllegalArgumentException("Token requerido")

                val token = authHeader.removePrefix("Bearer ")
                val userInfo = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

                val idNumber = call.parameters["idNumber"]
                    ?: throw IllegalArgumentException("ID de doctor requerido")

                val doctor = doctorService.getDoctorByUserId(idNumber)
                    ?: throw IllegalArgumentException("Doctor no encontrado")

                call.respond(doctor)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.Unauthorized,
                    message = mapOf("error" to (e.message ?: "Error de autenticación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * Create doctor profile
         *
         * Creates a professional doctor profile for the authenticated user
         * Only available for users with DOCTOR role
         *
         * @param CreateDoctorProfileRequest Optional: titulo, universidad, especialidades, doctorados, licencia, telefono, direccion
         * @header Authorization Bearer JWT token (must be DOCTOR role)
         * @return DoctorResponse with doctor profile data
         */
        post {
            try {
                val authHeader = call.request.headers["Authorization"]
                    ?: throw IllegalArgumentException("Token requerido")

                val token = authHeader.removePrefix("Bearer ")
                val userInfo = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

                if (userInfo.rol != Rol.DOCTOR) {
                    throw IllegalArgumentException("Solo los doctores pueden crear un perfil profesional")
                }

                val request = call.receive<CreateDoctorProfileRequest>()
                val doctor = doctorService.createDoctorProfile(userInfo.id, request)
                    ?: throw IllegalArgumentException("Error al crear el perfil profesional")

                call.respond(doctor)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.BadRequest,
                    message = mapOf("error" to (e.message ?: "Error de validación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * Update doctor profile
         *
         * Updates the professional doctor profile of the authenticated user
         * Only available for users with DOCTOR role
         *
         * @param UpdateDoctorProfileRequest Optional fields: titulo, universidad, especialidades, doctorados, licencia, telefono, direccion
         * @header Authorization Bearer JWT token (must be DOCTOR role)
         * @return DoctorResponse with updated doctor profile data
         */
        put {
            try {
                val authHeader = call.request.headers["Authorization"]
                    ?: throw IllegalArgumentException("Token requerido")

                val token = authHeader.removePrefix("Bearer ")
                val userInfo = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

                if (userInfo.rol != Rol.DOCTOR) {
                    throw IllegalArgumentException("Solo los doctores pueden actualizar un perfil profesional")
                }

                val request = call.receive<UpdateDoctorProfileRequest>()
                val doctor = doctorService.updateDoctorProfile(userInfo.id, request)
                    ?: throw IllegalArgumentException("Error al actualizar el perfil profesional")

                call.respond(doctor)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.BadRequest,
                    message = mapOf("error" to (e.message ?: "Error de validación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }
    }
}