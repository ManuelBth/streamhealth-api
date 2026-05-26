package com.betha.historialMedico.dto

import com.betha.historialMedico.document.MedicalHistoryDocument
import kotlinx.serialization.Serializable

/**
 * Response DTO for medical history data
 */
@Serializable
data class MedicalHistoryResponse(
    val id: String,
    val medicalHistoryId: String,
    val patientId: String,
    val doctorId: String,
    val appointmentId: String,
    val diagnostico: String,
    val observaciones: String,
    val recetas: List<String>,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Extension function to convert MedicalHistoryDocument to MedicalHistoryResponse
 */
fun MedicalHistoryDocument.toMedicalHistoryResponse(): MedicalHistoryResponse {
    return MedicalHistoryResponse(
        id = this.id ?: "",
        medicalHistoryId = this.medicalHistoryId,
        patientId = this.patientId,
        doctorId = this.doctorId,
        appointmentId = this.appointmentId,
        diagnostico = this.diagnostico,
        observaciones = this.observaciones,
        recetas = this.recetas,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}