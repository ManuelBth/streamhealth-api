package com.betha.historialMedico.repository

import com.betha.historialMedico.document.MedicalHistoryDocument

/**
 * Repository interface for medical history operations
 */
interface MedicalHistoryRepository {
    /**
     * Create a new medical history record
     * @param document MedicalHistoryDocument to create
     * @return Created MedicalHistoryDocument with generated ID
     */
    suspend fun create(document: MedicalHistoryDocument): MedicalHistoryDocument

    /**
     * Find medical history by MongoDB ID
     * @param id MongoDB ObjectId as String
     * @return MedicalHistoryDocument if found, null otherwise
     */
    suspend fun findById(id: String): MedicalHistoryDocument?

    /**
     * Find medical history by compound medicalHistoryId
     * @param medicalHistoryId Compound ID (e.g., "MH-20241215-ABC123")
     * @return MedicalHistoryDocument if found, null otherwise
     */
    suspend fun findByMedicalHistoryId(medicalHistoryId: String): MedicalHistoryDocument?

    /**
     * Find all medical histories for a patient
     * @param patientId Patient's identification number (cédula)
     * @return List of MedicalHistoryDocuments
     */
    suspend fun findByPatientId(patientId: String): List<MedicalHistoryDocument>

    /**
     * Find all medical histories for a doctor
     * @param doctorId Doctor's identification number (cédula)
     * @return List of MedicalHistoryDocuments
     */
    suspend fun findByDoctorId(doctorId: String): List<MedicalHistoryDocument>

    /**
     * Update an existing medical history
     * @param medicalHistoryId Compound ID
     * @param updates Map of field names to new values
     * @return Updated MedicalHistoryDocument if found, null otherwise
     */
    suspend fun update(medicalHistoryId: String, updates: Map<String, Any>): MedicalHistoryDocument?

    /**
     * Delete a medical history
     * @param medicalHistoryId Compound ID
     * @return true if deleted, false otherwise
     */
    suspend fun delete(medicalHistoryId: String): Boolean
}