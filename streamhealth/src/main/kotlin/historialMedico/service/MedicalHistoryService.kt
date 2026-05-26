package com.betha.historialMedico.service

import com.betha.historialMedico.dto.CreateMedicalHistoryRequest
import com.betha.historialMedico.dto.MedicalHistoryResponse
import com.betha.historialMedico.dto.UpdateMedicalHistoryRequest

/**
 * Service interface for medical history operations
 */
interface MedicalHistoryService {
    /**
     * Create a new medical history record
     * @param request Medical history creation data
     * @return Created MedicalHistoryResponse
     */
    suspend fun createMedicalHistory(request: CreateMedicalHistoryRequest): MedicalHistoryResponse

    /**
     * Get medical history by MongoDB ID
     * @param id MongoDB ObjectId
     * @return MedicalHistoryResponse if found, null otherwise
     */
    suspend fun getById(id: String): MedicalHistoryResponse?

    /**
     * Get medical history by compound medicalHistoryId
     * @param medicalHistoryId Compound ID (e.g., "MH-20241215-ABC123")
     * @return MedicalHistoryResponse if found, null otherwise
     */
    suspend fun getByMedicalHistoryId(medicalHistoryId: String): MedicalHistoryResponse?

    /**
     * Get all medical histories for a patient
     * @param patientId Patient's identification number (cédula)
     * @return List of MedicalHistoryResponses
     */
    suspend fun getByPatientId(patientId: String): List<MedicalHistoryResponse>

    /**
     * Get all medical histories for a doctor
     * @param doctorId Doctor's identification number (cédula)
     * @return List of MedicalHistoryResponses
     */
    suspend fun getByDoctorId(doctorId: String): List<MedicalHistoryResponse>

    /**
     * Update an existing medical history
     * @param medicalHistoryId Compound ID
     * @param request Update request with optional fields
     * @return Updated MedicalHistoryResponse if found, null otherwise
     */
    suspend fun updateMedicalHistory(medicalHistoryId: String, request: UpdateMedicalHistoryRequest): MedicalHistoryResponse?

    /**
     * Delete a medical history
     * @param medicalHistoryId Compound ID
     * @return true if deleted, false otherwise
     */
    suspend fun deleteMedicalHistory(medicalHistoryId: String): Boolean
}