package com.betha.recetas.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePrescriptionRequest(
    val medicamento: String? = null,
    val cantidad: String? = null,
    val frecuencia: String? = null,
    val recomendaciones: String? = null,
    val fechaValidacion: String? = null
)