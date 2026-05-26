package com.betha.prescription.controller

import com.betha.auth.service.AuthService
import com.betha.prescription.dto.CreatePrescriptionRequest
import com.betha.prescription.dto.PrescriptionResponse
import com.betha.prescription.dto.UpdatePrescriptionRequest
import com.betha.prescription.service.PrescriptionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Prescription controller for managing medical prescriptions
 *
 * Endpoints:
 * - POST /api/v1/prescriptions - Create prescription
 * - GET /api/v1/prescriptions/patient/{patientId} - Get patient prescriptions
 * - GET /api/v1/prescriptions/{prescriptionId} - Get prescription by ID
 * - PUT /api/v1/prescriptions/{prescriptionId} - Update prescription
 * - DELETE /api/v1/prescriptions/{prescriptionId} - Delete prescription
 */
fun Routing.prescriptionController(
    prescriptionService: PrescriptionService,
    authService: AuthService
) {
    route("/api/v1/prescriptions") {
        /**
         * Create prescription (POST /api/v1/prescriptions)
         */
        post {
            try {
                val request = call.receive<CreatePrescriptionRequest>()
                val response = prescriptionService.createPrescription(request)

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
         * Get patient prescriptions (GET /api/v1/prescriptions/patient/{patientId})
         */
        get("/patient/{patientId}") {
            try {
                val patientId = call.parameters["patientId"]
                    ?: throw IllegalArgumentException("patientId requerido")

                val response = prescriptionService.getByPatientId(patientId)
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
         * Get prescription by ID (GET /api/v1/prescriptions/{prescriptionId})
         */
        get("/{prescriptionId}") {
            try {
                val prescriptionId = call.parameters["prescriptionId"]
                    ?: throw IllegalArgumentException("prescriptionId requerido")

                val response = prescriptionService.getByPrescriptionId(prescriptionId)
                if (response != null) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = mapOf("error" to "Receta no encontrada")
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
         * Update prescription (PUT /api/v1/prescriptions/{prescriptionId})
         */
        put("/{prescriptionId}") {
            try {
                val prescriptionId = call.parameters["prescriptionId"]
                    ?: throw IllegalArgumentException("prescriptionId requerido")
                val request = call.receive<UpdatePrescriptionRequest>()

                val response = prescriptionService.updatePrescription(prescriptionId, request)
                if (response != null) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = mapOf("error" to "Receta no encontrada")
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
         * Delete prescription (DELETE /api/v1/prescriptions/{prescriptionId})
         */
        delete("/{prescriptionId}") {
            try {
                val prescriptionId = call.parameters["prescriptionId"]
                    ?: throw IllegalArgumentException("prescriptionId requerido")

                val deleted = prescriptionService.deletePrescription(prescriptionId)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = mapOf("error" to "Receta no encontrada")
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