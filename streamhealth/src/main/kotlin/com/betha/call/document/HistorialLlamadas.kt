package com.betha.call.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document

/**
 * Document for storing video call history
 * Represents a video call session in the StreamHealth telemedicine platform
 */
@Serializable
data class HistorialLlamadas(
    val id: String? = null,
    val appointmentId: String,
    val doctorId: String,
    val pacienteId: String,
    val estado: CallEstado,
    val iniciadaPor: String,
    val fechaInicio: Long? = null,
    val fechaFin: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromDocument(document: Document): HistorialLlamadas = json.decodeFromString(document.toJson())
    }
}