package com.betha.schedule.controller

import com.betha.auth.service.AuthService
import com.betha.schedule.dto.CreateAppointmentRequest
import com.betha.schedule.dto.UpdateAppointmentRequest
import com.betha.schedule.service.ScheduleService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Schedule controller for patient and doctor appointment management
 *
 * Patient endpoints:
 * - POST /api/v1/schedule/patient - Create appointment
 * - GET /api/v1/schedule/patient/{idNumber} - Get patient appointments
 * - PUT /api/v1/schedule/patient/{appointmentId} - Update (cancel) appointment
 * - DELETE /api/v1/schedule/patient/{appointmentId} - Cancel appointment
 *
 * Doctor endpoints:
 * - GET /api/v1/schedule/doctor/{idNumber} - Get doctor appointments
 * - PUT /api/v1/schedule/doctor/{appointmentId} - Update (confirm/complete) appointment
 */
fun Routing.scheduleController(
    scheduleService: ScheduleService,
    authService: AuthService
) {
    // =====================
    // ENDPOINTS DE PACIENTE
    // =====================

    route("/api/v1/schedule/patient") {
        /**
         * Create appointment (POST /api/v1/schedule/patient)
         */
        post {
            try {
                val request = call.receive<CreateAppointmentRequest>()
                val response = scheduleService.createAppointment(request)
                    ?: throw IllegalArgumentException("Error al crear la cita")

                call.respond(HttpStatusCode.Created, response)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = mapOf("error" to (e.message ?: "Error de validación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * Get patient appointments (GET /api/v1/schedule/patient/{idNumber})
         */
        get("/{idNumber}") {
            try {
                val idNumber = call.parameters["idNumber"]
                    ?: throw IllegalArgumentException("idNumber requerido")

                val response = scheduleService.getPatientAppointments(idNumber)
                call.respond(response)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = mapOf("error" to (e.message ?: "Error de validación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * Update (cancel) appointment (PUT /api/v1/schedule/patient/{appointmentId})
         */
        put("/{appointmentId}") {
            try {
                val appointmentId = call.parameters["appointmentId"]
                    ?: throw IllegalArgumentException("appointmentId requerido")
                val request = call.receive<UpdateAppointmentRequest>()

                val response = scheduleService.updatePatientAppointment(appointmentId, request)
                if (response != null) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = mapOf("error" to "Cita no encontrada")
                    )
                }
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = mapOf("error" to (e.message ?: "Error de validación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * Cancel appointment (DELETE /api/v1/schedule/patient/{appointmentId})
         */
        delete("/{appointmentId}") {
            try {
                val appointmentId = call.parameters["appointmentId"]
                    ?: throw IllegalArgumentException("appointmentId requerido")

                val deleted = scheduleService.deleteAppointment(appointmentId)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = mapOf("error" to "Cita no encontrada")
                    )
                }
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = mapOf("error" to (e.message ?: "Error de validación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }
    }

    // =====================
    // ENDPOINTS DE DOCTOR
    // =====================

    route("/api/v1/schedule/doctor") {
        /**
         * Get doctor appointments (GET /api/v1/schedule/doctor/{idNumber})
         */
        get("/{idNumber}") {
            try {
                val idNumber = call.parameters["idNumber"]
                    ?: throw IllegalArgumentException("idNumber requerido")

                val response = scheduleService.getDoctorAppointments(idNumber)
                call.respond(response)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = mapOf("error" to (e.message ?: "Error de validación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * Update (confirm/complete/cancel) appointment (PUT /api/v1/schedule/doctor/{appointmentId})
         */
        put("/{appointmentId}") {
            try {
                val appointmentId = call.parameters["appointmentId"]
                    ?: throw IllegalArgumentException("appointmentId requerido")
                val request = call.receive<UpdateAppointmentRequest>()

                val response = scheduleService.updateDoctorAppointment(appointmentId, request)
                if (response != null) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = mapOf("error" to "Cita no encontrada")
                    )
                }
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = mapOf("error" to (e.message ?: "Error de validación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }
    }
}