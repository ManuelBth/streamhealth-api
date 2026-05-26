package com.betha.call.service

import com.betha.call.dto.SignalingMessage
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object SignalingService {

    private val logger = LoggerFactory.getLogger(SignalingService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val rooms = ConcurrentHashMap<String, RoomState>()

    const val RING_TIMEOUT_MS = 45000L

    data class RoomState(
        val callId: String,
        val appointmentId: String,
        val doctorId: String,
        val pacienteId: String,
        val sessions: MutableMap<String, WebSocketServerSession> = mutableMapOf(),
        var sessionState: WebRTCSessionState = WebRTCSessionState.IMPOSSIBLE,
        var ringTimer: Job? = null
    )

    enum class WebRTCSessionState {
        IMPOSSIBLE,
        READY,
        CREATING,
        ACTIVE
    }

    suspend fun createRoom(
        callId: String,
        appointmentId: String,
        doctorId: String,
        pacienteId: String
    ) {
        mutex.withLock {
            val room = RoomState(
                callId = callId,
                appointmentId = appointmentId,
                doctorId = doctorId,
                pacienteId = pacienteId
            )
            rooms[callId] = room

            room.ringTimer = scope.launch {
                delay(RING_TIMEOUT_MS)
                mutex.withLock {
                    if (room.sessions.size < 2) {
                        logger.info("Ring timeout for callId: $callId - moving to MISSED state")
                        room.sessionState = WebRTCSessionState.IMPOSSIBLE
                        room.sessions.values.forEach { session ->
                            sendToSession(session, SignalingMessage.CallEnded(callId = callId, reason = "missed"))
                        }
                        room.sessions.clear()
                        rooms.remove(callId)
                    }
                }
            }
            logger.info("Created signaling room for callId: $callId")
        }
    }

    suspend fun onPeerJoined(
        callId: String,
        userId: String,
        session: WebSocketServerSession
    ) {
        val room = rooms[callId] ?: run {
            logger.warn("Room not found for callId: $callId")
            session.close(CloseReason(CloseReason.Codes.NORMAL, "Room not found"))
            return
        }

        mutex.withLock {
            if (userId != room.doctorId && userId != room.pacienteId) {
                logger.warn("Unauthorized user $userId trying to join callId: $callId")
                session.close(CloseReason(CloseReason.Codes.NORMAL, "Unauthorized"))
                return
            }

            if (room.sessions.size >= 2) {
                logger.warn("Room full for callId: $callId")
                session.close(CloseReason(CloseReason.Codes.NORMAL, "Room full"))
                return
            }

            room.sessions[userId] = session
            logger.info("Peer $userId joined callId: $callId (total: ${room.sessions.size})")

            if (room.sessions.size == 2) {
                room.sessionState = WebRTCSessionState.READY
                room.ringTimer?.cancel()
                room.ringTimer = null

                room.sessions.forEach { (id, clientSession) ->
                    val otherUserId = room.sessions.keys.first { key -> key != id }
                    sendToSession(clientSession, SignalingMessage.UserJoined(callId = callId, userId = otherUserId))
                }
                logger.info("Both peers joined callId: $callId - session ready")
            }

            sendToSession(session, SignalingMessage.CallJoined(callId = callId, userId = userId))
        }
    }

    fun onMessage(callId: String, userId: String, message: String) {
        val room = rooms[callId] ?: run {
            logger.warn("Room not found for message in callId: $callId")
            return
        }

        val signalingMessage = try {
            Json.decodeFromString<SignalingMessage>(message)
        } catch (e: Exception) {
            logger.error("Error parsing message: ${e.message}")
            return
        }

        when (signalingMessage.type) {
            "offer" -> handleOffer(room, userId, message)
            "answer" -> handleAnswer(room, userId, message)
            "ice_candidate" -> handleIceCandidate(room, userId, message)
            "call_ended", "hangup" -> handleHangup(room, userId, callId)
            else -> logger.debug("Unhandled message type: ${signalingMessage.type}")
        }
    }

    private fun handleOffer(room: RoomState, userId: String, message: String) {
        if (room.sessionState != WebRTCSessionState.READY) {
            logger.warn("Cannot handle offer: room not ready, state=${room.sessionState}")
            return
        }

        room.sessionState = WebRTCSessionState.CREATING
        logger.info("Handling offer from $userId in room ${room.callId}")

        val otherSession = findOtherSession(room, userId)
        otherSession?.let {
            sendRawToSession(it, message)
            logger.debug("Relayed offer to other peer in room ${room.callId}")
        }
    }

    private fun handleAnswer(room: RoomState, userId: String, message: String) {
        if (room.sessionState != WebRTCSessionState.CREATING) {
            logger.warn("Cannot handle answer: not in creating state")
            return
        }

        logger.info("Handling answer from $userId in room ${room.callId}")

        val otherSession = findOtherSession(room, userId)
        otherSession?.let {
            sendRawToSession(it, message)
            logger.debug("Relayed answer to other peer in room ${room.callId}")
        }
        room.sessionState = WebRTCSessionState.ACTIVE
        logger.info("WebRTC session ACTIVE for callId: ${room.callId}")
    }

    private fun handleIceCandidate(room: RoomState, userId: String, message: String) {
        logger.debug("Handling ICE from $userId in room ${room.callId}")

        val otherSession = findOtherSession(room, userId)
        otherSession?.let {
            sendRawToSession(it, message)
            logger.debug("Relayed ICE candidate to other peer")
        }
    }

    private fun findOtherSession(room: RoomState, userId: String): WebSocketServerSession? {
        for ((key, value) in room.sessions) {
            if (key != userId) {
                return value
            }
        }
        return null
    }

    private fun handleHangup(room: RoomState, userId: String, callId: String) {
        logger.info("Hangup from $userId in room $callId")

        scope.launch {
            mutex.withLock {
                room.sessions.values.forEach { session ->
                    sendToSession(session, SignalingMessage.CallEnded(callId = callId, reason = "hangup"))
                }
                room.sessions.clear()
                room.sessionState = WebRTCSessionState.IMPOSSIBLE
                rooms.remove(callId)
                logger.info("Room closed for callId: $callId")
            }
        }
    }

    suspend fun onPeerLeft(callId: String, userId: String) {
        val room = rooms[callId] ?: return

        mutex.withLock {
            room.sessions.remove(userId)
            logger.info("Peer $userId left callId: $callId (remaining: ${room.sessions.size})")

            if (room.sessions.size < 2) {
                room.sessionState = WebRTCSessionState.IMPOSSIBLE
            }

            room.sessions.values.forEach { session ->
                sendToSession(session, SignalingMessage.UserLeft(callId = callId, userId = userId))
            }

            if (room.sessions.isEmpty()) {
                room.ringTimer?.cancel()
                rooms.remove(callId)
                logger.info("Room cleaned up for callId: $callId")
            }
        }
    }

    private fun sendToSession(session: WebSocketServerSession, message: SignalingMessage) {
        scope.launch {
            try {
                val json = Json.encodeToString(message)
                session.send(Frame.Text(json))
            } catch (e: Exception) {
                logger.error("Error sending to session: ${e.message}")
            }
        }
    }

    private fun sendRawToSession(session: WebSocketServerSession, message: String) {
        scope.launch {
            try {
                session.send(Frame.Text(message))
            } catch (e: Exception) {
                logger.error("Error sending raw to session: ${e.message}")
            }
        }
    }

    fun closeRoom(callId: String) {
        val room = rooms[callId] ?: return
        scope.launch {
            mutex.withLock {
                room.ringTimer?.cancel()
                room.sessions.values.forEach { session ->
                    try {
                        session.close(CloseReason(CloseReason.Codes.NORMAL, "Room closed"))
                    } catch (e: Exception) {
                        logger.error("Error closing session: ${e.message}")
                    }
                }
                room.sessions.clear()
                rooms.remove(callId)
                logger.info("Room forcefully closed for callId: $callId")
            }
        }
    }
}