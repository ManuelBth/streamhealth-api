package com.betha.historialMedico.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document

/**
 * Medical history document for MongoDB persistence
 * Represents a medical history record in the StreamHealth telemedicine platform
 */
@Serializable
data class MedicalHistoryDocument(
    val id: String? = null,
    val medicalHistoryId: String,
    val patientId: String,
    val doctorId: String,
    val appointmentId: String,
    val diagnostico: String,
    val observaciones: String,
    val recetas: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromDocument(document: Document): MedicalHistoryDocument = json.decodeFromString(document.toJson())
    }
}