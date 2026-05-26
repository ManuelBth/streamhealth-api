package com.betha.recetas.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document

@Serializable
data class PrescriptionDocument(
    val _id: String? = null,
    val prescriptionId: String,
    val patientId: String,
    val doctorId: String,
    val medicalHistoryId: String,
    val medicamento: String,
    val cantidad: String,
    val frecuencia: String,
    val recomendaciones: String? = null,
    val fechaValidacion: String,
    val createdAt: String = System.currentTimeMillis().toString()
) {
    fun toDocument(): Document {
        val map = mutableMapOf<String, Any?>()
        map["prescriptionId"] = prescriptionId
        map["patientId"] = patientId
        map["doctorId"] = doctorId
        map["medicalHistoryId"] = medicalHistoryId
        map["medicamento"] = medicamento
        map["cantidad"] = cantidad
        map["frecuencia"] = frecuencia
        map["recomendaciones"] = recomendaciones
        map["fechaValidacion"] = fechaValidacion
        map["createdAt"] = createdAt
        return Document(map)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromDocument(document: Document): PrescriptionDocument {
            val cleanDoc = Document(document.toMutableMap().apply { remove("_id") })
            return json.decodeFromString(cleanDoc.toJson())
        }
    }
}