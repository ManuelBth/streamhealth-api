package com.betha.call.repository

import com.betha.call.document.HistorialLlamadas

/**
 * Repository interface for video call history operations
 */
interface CallRepository {
    /**
     * Save a new call record
     * @param llamada HistorialLlamadas to save
     * @return Saved HistorialLlamadas with generated id
     */
    suspend fun save(llamada: HistorialLlamadas): HistorialLlamadas

    /**
     * Find call by MongoDB ID
     * @param id MongoDB ObjectId as String
     * @return HistorialLlamadas if found, null otherwise
     */
    suspend fun findById(id: String): HistorialLlamadas?

    /**
     * Find all calls for an appointment
     * @param appointmentId Compound appointment ID
     * @return List of HistorialLlamadas
     */
    suspend fun findByAppointmentId(appointmentId: String): List<HistorialLlamadas>

    /**
     * Find active call for an appointment (INICIADA or EN_CURSO)
     * @param appointmentId Compound appointment ID
     * @return Active HistorialLlamadas if found, null otherwise
     */
    suspend fun findActiveByAppointmentId(appointmentId: String): HistorialLlamadas?

    /**
     * Update an existing call record
     * @param llamada Updated HistorialLlamadas
     * @return Updated HistorialLlamadas if found, null otherwise
     */
    suspend fun update(llamada: HistorialLlamadas): HistorialLlamadas?

    /**
     * Find all calls for a user (as doctor or patient)
     * @param userId User's identification number (cédula)
     * @return List of HistorialLlamadas where userId matches doctorId OR pacienteId
     */
    suspend fun findByUserId(userId: String): List<HistorialLlamadas>
}