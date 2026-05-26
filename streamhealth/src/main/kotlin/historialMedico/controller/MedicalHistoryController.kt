package com.betha.historialMedico.controller

import com.betha.auth.service.AuthService
import com.betha.historialMedico.dto.CreateMedicalHistoryRequest
import com.betha.historialMedico.dto.UpdateMedicalHistoryRequest
import com.betha.historialMedico.service.MedicalHistoryService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe

/**
 * Extract Bearer token from Authorization header
 */
private fun extractToken(authorizationHeader: String?): String? {
    if (authorizationHeader == null) return null
    if (!authorizationHeader.startsWith("Bearer ")) return null
    return authorizationHeader.substring(7)
}

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
                val token = extractToken(call.request.headers[HttpHeaders.Authorization])
                    ?: throw IllegalArgumentException("Token requerido")

                authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

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
        }.describe {
            summary = "Create medical history"
            description = "Create a new medical history record (typically after appointment completion)"
            responses {
                HttpStatusCode.Created {
                    description = "Medical history created successfully"
                }
                HttpStatusCode.BadRequest {
                    description = "Invalid input or validation error"
                }
            }
        }

        /**
         * Get patient medical histories (GET /api/v1/medical-history/patient/{patientId})
         */
        get("/patient/{patientId}") {
            try {
                val token = extractToken(call.request.headers[HttpHeaders.Authorization])
                    ?: throw IllegalArgumentException("Token requerido")

                authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

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
        }.describe {
            summary = "Get patient medical histories"
            description = "Retrieve all medical histories for a specific patient"
            responses {
                HttpStatusCode.OK {
                    description = "List of medical histories"
                }
                HttpStatusCode.BadRequest {
                    description = "Invalid request parameters"
                }
            }
        }

        /**
         * Get medical history by ID (GET /api/v1/medical-history/{historyId})
         */
        get("/{historyId}") {
            try {
                val token = extractToken(call.request.headers[HttpHeaders.Authorization])
                    ?: throw IllegalArgumentException("Token requerido")

                authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

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
        }.describe {
            summary = "Get medical history by ID"
            description = "Retrieve a specific medical history by its unique ID"
            responses {
                HttpStatusCode.OK {
                    description = "Medical history found"
                }
                HttpStatusCode.NotFound {
                    description = "Medical history not found"
                }
            }
        }

        /**
         * Update medical history (PUT /api/v1/medical-history/{historyId})
         */
        put("/{historyId}") {
            try {
                val token = extractToken(call.request.headers[HttpHeaders.Authorization])
                    ?: throw IllegalArgumentException("Token requerido")

                authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

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
        }.describe {
            summary = "Update medical history"
            description = "Update an existing medical history (typically diagnosis or prescriptions)"
            responses {
                HttpStatusCode.OK {
                    description = "Medical history updated successfully"
                }
                HttpStatusCode.BadRequest {
                    description = "Invalid input"
                }
                HttpStatusCode.NotFound {
                    description = "Medical history not found"
                }
            }
        }

        /**
         * Delete medical history (DELETE /api/v1/medical-history/{historyId})
         */
        delete("/{historyId}") {
            try {
                val token = extractToken(call.request.headers[HttpHeaders.Authorization])
                    ?: throw IllegalArgumentException("Token requerido")

                authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

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
        }.describe {
            summary = "Delete medical history"
            description = "Delete a medical history record"
            responses {
                HttpStatusCode.NoContent {
                    description = "Medical history deleted successfully"
                }
                HttpStatusCode.NotFound {
                    description = "Medical history not found"
                }
            }
        }
    }
}