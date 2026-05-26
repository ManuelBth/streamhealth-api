package com.betha.call.dto

import kotlinx.serialization.Serializable

/**
 * Request to initiate a video call
 */
@Serializable
data class IniciarLlamadaRequest(
    val appointmentId: String
)

/**
 * Request to join an existing video call
 */
@Serializable
data class UnirseLlamadaRequest(
    val callId: String
)

/**
 * Request to end a video call
 */
@Serializable
data class FinalizarLlamadaRequest(
    val callId: String
)