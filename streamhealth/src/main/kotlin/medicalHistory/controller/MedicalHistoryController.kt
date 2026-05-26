package com.betha.medicalHistory.controller

import com.betha.auth.service.AuthService
import com.betha.medicalHistory.dto.CreateMedicalHistoryRequest
import com.betha.medicalHistory.dto.MedicalHistoryResponse
import com.betha.medicalHistory.dto.UpdateMedicalHistoryRequest
import com.betha.medicalHistory.service.MedicalHistoryService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Medical History controller for managing patient medical records
 *
 * Endpoints:
 * - POST /api/v1/medical-history - Create medical history
 * - GET /api/v1/medical-history/patient/{patientId} - Get patient medical histories
 * - GET /api/v1/medical-history/{historyId} - Get medical history by ID
 * - PUT /api/v1/medical-history/{historyId} - Update medical history
 * - DELETE /api/v1/medical-history/{historyId} - Delete medical history
 */
fun Routing.medicalHistoryController(
    medicalHistoryService: MedicalHistoryService,
    authService: AuthService
) {
    route("/api/v1/medical-history") {
        /**
         * Create medical history (POST /api/v1/medical-history)
         */
        post {
            try {
                val request = call.receive<CreateMedicalHistoryRequest>()
                val response = medicalHistoryService.createMedicalHistory(request)

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
         * Get patient medical histories (GET /api/v1/medical-history/patient/{patientId})
         */
        get("/patient/{patientId}") {
            try {
                val patientId = call.parameters["patientId"]
                    ?: throw IllegalArgumentException("patientId requerido")

                val response = medicalHistoryService.getByPatientId(patientId)
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
         * Get medical history by ID (GET /api/v1/medical-history/{historyId})
         */
        get("/{historyId}") {
            try {
                val historyId = call.parameters["historyId"]
                    ?: throw IllegalArgumentException("historyId requerido")

                val response = medicalHistoryService.getByMedicalHistoryId(historyId)
                if (response != null) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = mapOf("error" to "Historial médico no encontrado")
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
         * Update medical history (PUT /api/v1/medical-history/{historyId})
         */
        put("/{historyId}") {
            try {
                val historyId = call.parameters["historyId"]
                    ?: throw IllegalArgumentException("historyId requerido")
                val request = call.receive<UpdateMedicalHistoryRequest>()

                val response = medicalHistoryService.updateMedicalHistory(historyId, request)
                if (response != null) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = mapOf("error" to "Historial médico no encontrado")
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
         * Delete medical history (DELETE /api/v1/medical-history/{historyId})
         */
        delete("/{historyId}") {
            try {
                val historyId = call.parameters["historyId"]
                    ?: throw IllegalArgumentException("historyId requerido")

                val deleted = medicalHistoryService.deleteMedicalHistory(historyId)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        status = HttpStatusCode.NotFound,
                        message = mapOf("error" to "Historial médico no encontrado")
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