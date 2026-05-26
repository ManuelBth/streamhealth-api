package com.betha.recetas.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreatePrescriptionRequest(
    val patientId: String,
    val doctorId: String,
    val medicalHistoryId: String,
    val medicamento: String,
    val cantidad: String,
    val frecuencia: String,
    val recomendaciones: String? = null,
    val fechaValidacion: String  // ISO8601 datetime string
)