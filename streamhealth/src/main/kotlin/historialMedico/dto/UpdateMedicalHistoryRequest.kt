package com.betha.historialMedico.dto

import kotlinx.serialization.Serializable

/**
 * Request DTO for updating an existing medical history
 */
@Serializable
data class UpdateMedicalHistoryRequest(
    val diagnostico: String? = null,
    val observaciones: String? = null,
    val recetas: List<String>? = null
)