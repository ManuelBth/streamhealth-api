package com.betha.call.document

import kotlinx.serialization.Serializable

/**
 * Enum representing video call states
 */
@Serializable
enum class CallEstado {
    INICIADA,
    EN_CURSO,
    FINALIZADA,
    RECHAZADA,
    PERDIDA
}