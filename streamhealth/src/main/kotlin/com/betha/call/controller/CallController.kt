package com.betha.call.controller

import com.betha.auth.service.AuthService
import com.betha.call.document.CallEstado
import com.betha.call.document.HistorialLlamadas
import com.betha.call.dto.LlamadaResponse
import com.betha.call.repository.CallRepository
import com.betha.call.service.SignalingService
import com.betha.schedule.repository.ScheduleRepository
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private fun extractToken(authorizationHeader: String?): String? {
    if (authorizationHeader == null) return null
    if (!authorizationHeader.startsWith("Bearer ")) return null
    return authorizationHeader.substring(7)
}

fun Routing.callController(
    callRepository: CallRepository,
    scheduleRepository: ScheduleRepository,
    authService: AuthService,
    signalingService: SignalingService
) {
    route("/api/v1/schedule") {
        post("/{appointmentId}/call") {
            try {
                val token = extractToken(call.request.headers[HttpHeaders.Authorization])
                    ?: throw IllegalArgumentException("Token requerido")

                val decodedJWT = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

                val appointmentId = call.parameters["appointmentId"]
                    ?: throw IllegalArgumentException("appointmentId requerido")

                val appointment = scheduleRepository.findByAppointmentId(appointmentId)
                    ?: throw IllegalArgumentException("Cita no encontrada")

                if (appointment.estado != "confirmada") {
                    throw IllegalArgumentException("La cita debe estar confirmada para iniciar una llamada")
                }

                val userId = decodedJWT.id
                if (appointment.doctorId != userId) {
                    throw IllegalArgumentException("Solo el doctor puede iniciar la llamada")
                }

                val activeCall = callRepository.findActiveByAppointmentId(appointmentId)
                if (activeCall != null) {
                    throw IllegalArgumentException("Ya existe una llamada activa para esta cita")
                }

                val historialLlamadas = HistorialLlamadas(
                    appointmentId = appointmentId,
                    doctorId = appointment.doctorId,
                    pacienteId = appointment.patientId,
                    estado = CallEstado.INICIADA,
                    iniciadaPor = userId
                )

                val savedCall = callRepository.save(historialLlamadas)
                val callId = savedCall.id ?: throw IllegalArgumentException("Error al crear la llamada")

                signalingService.createRoom(
                    callId = callId,
                    appointmentId = appointmentId,
                    doctorId = appointment.doctorId,
                    pacienteId = appointment.patientId
                )

                val response = LlamadaResponse(
                    callId = callId,
                    appointmentId = appointmentId,
                    estado = CallEstado.INICIADA,
                    doctorId = appointment.doctorId,
                    pacienteId = appointment.patientId,
                    iniciadaPor = userId
                )

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

        post("/calls/{callId}/join") {
            try {
                val token = extractToken(call.request.headers[HttpHeaders.Authorization])
                    ?: throw IllegalArgumentException("Token requerido")

                val decodedJWT = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

                val callId = call.parameters["callId"]
                    ?: throw IllegalArgumentException("callId requerido")

                val llamada = callRepository.findById(callId)
                    ?: throw IllegalArgumentException("Llamada no encontrada")

                val userId = decodedJWT.id
                if (llamada.doctorId != userId && llamada.pacienteId != userId) {
                    throw IllegalArgumentException("No eres participante de esta llamada")
                }

                val now = System.currentTimeMillis()
                val updatedLlamada = llamada.copy(
                    estado = CallEstado.EN_CURSO,
                    fechaInicio = now,
                    updatedAt = now
                )
                callRepository.update(updatedLlamada)

                val response = LlamadaResponse(
                    callId = callId,
                    appointmentId = llamada.appointmentId,
                    estado = CallEstado.EN_CURSO,
                    doctorId = llamada.doctorId,
                    pacienteId = llamada.pacienteId,
                   iniciadaPor = llamada.iniciadaPor,
                    fechaInicio = updatedLlamada.fechaInicio
                )

                call.respond(HttpStatusCode.OK, response)
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

        post("/calls/{callId}/end") {
            try {
                val token = extractToken(call.request.headers[HttpHeaders.Authorization])
                    ?: throw IllegalArgumentException("Token requerido")

                val decodedJWT = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

                val callId = call.parameters["callId"]
                    ?: throw IllegalArgumentException("callId requerido")

                val llamada = callRepository.findById(callId)
                    ?: throw IllegalArgumentException("Llamada no encontrada")

                val userId = decodedJWT.id
                if (llamada.doctorId != userId && llamada.pacienteId != userId) {
                    throw IllegalArgumentException("No eres participante de esta llamada")
                }

                val fechaFin = System.currentTimeMillis()
                val fechaInicio = llamada.fechaInicio ?: fechaFin
                val duracionSegundos = (fechaFin - fechaInicio) / 1000

                val updatedLlamada = llamada.copy(
                    estado = CallEstado.FINALIZADA,
                    fechaFin = fechaFin,
                    updatedAt = System.currentTimeMillis()
                )
                callRepository.update(updatedLlamada)

                signalingService.onMessage(callId, userId, """{"type":"hangup"}""")

                call.respond(HttpStatusCode.OK, mapOf(
                    "callId" to callId,
                    "estado" to "FINALIZADA",
                    "duracionSegundos" to duracionSegundos
                ))
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

        post("/calls/{callId}/decline") {
            try {
                val token = extractToken(call.request.headers[HttpHeaders.Authorization])
                    ?: throw IllegalArgumentException("Token requerido")

                val decodedJWT = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

                val callId = call.parameters["callId"]
                    ?: throw IllegalArgumentException("callId requerido")

                val llamada = callRepository.findById(callId)
                    ?: throw IllegalArgumentException("Llamada no encontrada")

                val userId = decodedJWT.id
                if (llamada.iniciadaPor == userId) {
                    throw IllegalArgumentException("El iniciador no puede rechazar la llamada")
                }

                if (llamada.doctorId != userId && llamada.pacienteId != userId) {
                    throw IllegalArgumentException("No eres participante de esta llamada")
                }

                val updatedLlamada = llamada.copy(
                    estado = CallEstado.RECHAZADA,
                    updatedAt = System.currentTimeMillis()
                )
                callRepository.update(updatedLlamada)

                signalingService.onMessage(callId, userId, """{"type":"hangup"}""")

                call.respond(HttpStatusCode.OK, mapOf("callId" to callId, "estado" to "RECHAZADA"))
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

        get("/{appointmentId}/calls") {
            try {
                val token = extractToken(call.request.headers[HttpHeaders.Authorization])
                    ?: throw IllegalArgumentException("Token requerido")

                val decodedJWT = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")

                val appointmentId = call.parameters["appointmentId"]
                    ?: throw IllegalArgumentException("appointmentId requerido")

                val appointment = scheduleRepository.findByAppointmentId(appointmentId)
                    ?: throw IllegalArgumentException("Cita no encontrada")

                val userId = decodedJWT.id
                if (appointment.doctorId != userId && appointment.patientId != userId) {
                    throw IllegalArgumentException("No eres participante de esta cita")
                }

                val llamadas = callRepository.findByAppointmentId(appointmentId)
                val responses = llamadas.map { hist ->
                    val duracion = if (hist.fechaFin != null && hist.fechaInicio != null) {
                        (hist.fechaFin - hist.fechaInicio) / 1000
                    } else null

                    LlamadaResponse(
                        callId = hist.id ?: "",
                        appointmentId = hist.appointmentId,
                        estado = hist.estado,
                        doctorId = hist.doctorId,
                        pacienteId = hist.pacienteId,
                        iniciadaPor = hist.iniciadaPor,
                        fechaInicio = hist.fechaInicio,
                        fechaFin = hist.fechaFin,
                        duracionSegundos = duracion
                    )
                }

                call.respond(HttpStatusCode.OK, responses)
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
