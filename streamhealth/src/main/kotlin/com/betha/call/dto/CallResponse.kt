package com.betha.call.dto

import com.betha.call.document.CallEstado
import kotlinx.serialization.Serializable

/**
 * Response DTO for video call information
 */
@Serializable
data class LlamadaResponse(
    val callId: String,
    val appointmentId: String,
    val estado: CallEstado,
    val doctorId: String,
    val pacienteId: String,
    val iniciadaPor: String,
    val fechaInicio: Long? = null,
    val fechaFin: Long? = null,
    val duracionSegundos: Long? = null
)

/**
 * WebSocket signaling message types
 */
@Serializable
sealed class SignalingMessage {
    abstract val type: String

    @Serializable
    data class Offer(
        val sdp: String,
        val callerId: String
    ) : SignalingMessage() {
        override val type = "offer"
    }

    @Serializable
    data class Answer(
        val sdp: String,
        val calleeId: String
    ) : SignalingMessage() {
        override val type = "answer"
    }

    @Serializable
    data class IceCandidate(
        val candidate: String,
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = null
    ) : SignalingMessage() {
        override val type = "ice_candidate"
    }

    @Serializable
    data class CallStarted(
        val callId: String,
        val appointmentId: String,
        val initiatedBy: String
    ) : SignalingMessage() {
        override val type = "call_started"
    }

    @Serializable
    data class CallJoined(
        val callId: String,
        val userId: String
    ) : SignalingMessage() {
        override val type = "call_joined"
    }

    @Serializable
    data class CallEnded(
        val callId: String,
        val reason: String? = null
    ) : SignalingMessage() {
        override val type = "call_ended"
    }

    @Serializable
    data class UserJoined(
        val callId: String,
        val userId: String
    ) : SignalingMessage() {
        override val type = "user_joined"
    }

    @Serializable
    data class UserLeft(
        val callId: String,
        val userId: String
    ) : SignalingMessage() {
        override val type = "user_left"
    }

    @Serializable
    data class Error(
        val code: String,
        val message: String
    ) : SignalingMessage() {
        override val type = "error"
    }
}