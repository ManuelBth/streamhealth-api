# Código del Servidor de Señalización

> Este código es para adaptar en tu servidor existente. Está basado en el ejemplo Ktor pero modificado para soportar múltiples llamadas, autenticación JWT y el flujo de citas médicas.

---

## 1. Estructura de Archivos

```
src/main/kotlin/com/tuempresa/
├── Application.kt                    # Entry point y configuración
├── plugins/
│   ├── WebSockets.kt                 # Configuración de WebSockets
│   ├── Security.kt                   # JWT validation
│   └── Routing.kt                    # Rutas HTTP + WS
├── routes/
│   ├── CallRoutes.kt                 # Endpoints REST para llamadas
│   └── WebSocketRoutes.kt            # Rutas WebSocket
├── service/
│   ├── CallService.kt                # Lógica de negocio
│   └── SignalingService.kt           # Manejo de señalización WebRTC
├── model/
│   ├── HistorialLlamadas.kt          # Data class MongoDB
│   ├── WebSocketMessage.kt           # Modelos de mensajes WS
│   └── CallState.kt                  # Estados de llamada y sesión
└── repository/
    └── HistorialLlamadasRepository.kt # Acceso a MongoDB
```

---

## 2. Modelos de Datos

### `model/HistorialLlamadas.kt`

```kotlin
package com.tuempresa.model

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

data class HistorialLlamadas(
    @BsonId
    val id: ObjectId = ObjectId(),
    val appointmentId: String,
    val doctorId: String,
    val patientId: String,
    val estado: CallEstado = CallEstado.RINGING,
    val iniciadaPor: String,
    val fechaInicio: Instant = Instant.now(),
    val fechaFin: Instant? = null,
    val duracionSegundos: Int? = null
)

enum class CallEstado {
    RINGING,    // Sonando, esperando respuesta
    ACTIVE,     // En curso
    ENDED,      // Finalizada normal
    MISSED,     // No contestó (timeout)
    DECLINED    // Rechazó explícitamente
}
```

### `model/WebSocketMessage.kt`

```kotlin
package com.tuempresa.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class WebSocketMessage {
    abstract val type: String
}

@Serializable
data class StateMessage(
    override val type: String = "state",
    val status: String
) : WebSocketMessage()

@Serializable
data class OfferMessage(
    override val type: String = "offer",
    val sdp: String
) : WebSocketMessage()

@Serializable
data class AnswerMessage(
    override val type: String = "answer",
    val sdp: String
) : WebSocketMessage()

@Serializable
data class IceMessage(
    override val type: String = "ice",
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
) : WebSocketMessage()

@Serializable
data class PeerJoinedMessage(
    override val type: String = "peer_joined",
    val userId: String
) : WebSocketMessage()

@Serializable
data class PeerLeftMessage(
    override val type: String = "peer_left",
    val userId: String
) : WebSocketMessage()

@Serializable
data class CallEndedMessage(
    override val type: String = "call_ended",
    val reason: String
) : WebSocketMessage()

@Serializable
data class HangupMessage(
    override val type: String = "hangup"
) : WebSocketMessage()

// Para parsing genérico
@Serializable
data class UnknownMessage(
    override val type: String
) : WebSocketMessage()
```

### `model/CallState.kt`

```kotlin
package com.tuempresa.model

enum class WebRTCSessionState {
    IMPOSSIBLE,  // < 2 peers conectados
    READY,       // 2 peers listos
    CREATING,    // Offer enviado, esperando answer
    ACTIVE       // Conexión P2P establecida
}
```

---

## 3. Servicio de Señalización (El Corazón)

### `service/SignalingService.kt`

```kotlin
package com.tuempresa.service

import com.tuempresa.model.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SignalingService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    
    // Map: callId -> RoomState
    private val rooms = ConcurrentHashMap<String, RoomState>()
    
    // Timeout para pasar de ringing a missed (45 segundos)
    private const val RING_TIMEOUT_MS = 45000L

    data class RoomState(
        val callId: String,
        val appointmentId: String,
        val doctorId: String,
        val patientId: String,
        val clients: MutableMap<String, DefaultWebSocketServerSession> = mutableMapOf(),
        var sessionState: WebRTCSessionState = WebRTCSessionState.IMPOSSIBLE,
        var ringTimer: Job? = null
    )

    /**
     * Crea una nueva sala de llamada cuando el doctor inicia
     */
    suspend fun createRoom(
        callId: String,
        appointmentId: String,
        doctorId: String,
        patientId: String
    ) {
        mutex.withLock {
            val room = RoomState(
                callId = callId,
                appointmentId = appointmentId,
                doctorId = doctorId,
                patientId = patientId
            )
            rooms[callId] = room
            
            // Iniciar timer para pasar a missed si nadie contesta
            room.ringTimer = scope.launch {
                delay(RING_TIMEOUT_MS)
                mutex.withLock {
                    if (room.clients.size < 2) {
                        room.sessionState = WebRTCSessionState.IMPOSSIBLE
                        // Notificar a quien esté conectado que nadie contestó
                        room.clients.values.forEach { session ->
                            sendToSession(session, CallEndedMessage(reason = "missed"))
                        }
                        // Cerrar todas las conexiones
                        room.clients.clear()
                        rooms.remove(callId)
                    }
                }
            }
        }
    }

    /**
     * Un peer se conecta al WebSocket
     */
    suspend fun onPeerJoined(
        callId: String,
        userId: String,
        session: DefaultWebSocketServerSession
    ) {
        val room = rooms[callId] ?: run {
            session.close(CloseReason(CloseReason.Codes.NORMAL, "Room not found"))
            return
        }

        mutex.withLock {
            // Validar que el userId sea doctor o patient de esta cita
            if (userId != room.doctorId && userId != room.patientId) {
                session.close(CloseReason(CloseReason.Codes.NORMAL, "Unauthorized"))
                return
            }
            
            // Solo permitir 2 peers
            if (room.clients.size >= 2) {
                session.close(CloseReason(CloseReason.Codes.NORMAL, "Room full"))
                return
            }
            
            room.clients[userId] = session
            
            // Si hay 2 peers, actualizar estado
            if (room.clients.size == 2) {
                room.sessionState = WebRTCSessionState.READY
                room.ringTimer?.cancel() // Cancelar timer de missed
                
                // Notificar a AMBOS que el otro se unió
                room.clients.forEach { (id, clientSession) ->
                    val otherUserId = room.clients.keys.first { it != id }
                    sendToSession(clientSession, PeerJoinedMessage(userId = otherUserId))
                }
            }
            
            // Enviar estado actual
            sendToSession(session, StateMessage(status = room.sessionState.name.lowercase()))
        }
    }

    /**
     * Procesa mensajes WebSocket entrantes
     */
    fun onMessage(callId: String, userId: String, message: String) {
        val room = rooms[callId] ?: return
        
        // Parsear mensaje
        val wsMessage = try {
            Json.decodeFromString(WebSocketMessage.serializer(), message)
        } catch (e: Exception) {
            println("Error parsing message: $e")
            return
        }
        
        when (wsMessage.type.lowercase()) {
            "state" -> handleStateRequest(room, userId)
            "offer" -> handleOffer(room, userId, message)
            "answer" -> handleAnswer(room, userId, message)
            "ice" -> handleIce(room, userId, message)
            "hangup" -> handleHangup(room, userId)
        }
    }

    private fun handleStateRequest(room: RoomState, userId: String) {
        val session = room.clients[userId] ?: return
        sendToSession(session, StateMessage(status = room.sessionState.name.lowercase()))
    }

    private fun handleOffer(room: RoomState, userId: String, message: String) {
        if (room.sessionState != WebRTCSessionState.READY) {
            println("Cannot handle offer: room not ready, state=${room.sessionState}")
            return
        }
        
        room.sessionState = WebRTCSessionState.CREATING
        println("Handling offer from $userId in room ${room.callId}")
        
        // Reenviar al OTRO peer
        val otherSession = room.clients.values.firstOrNull { session ->
            room.clients.entries.first { it.value == session }.key != userId
        }
        
        otherSession?.let { sendRawToSession(it, message) }
        notifyStateUpdate(room)
    }

    private fun handleAnswer(room: RoomState, userId: String, message: String) {
        if (room.sessionState != WebRTCSessionState.CREATING) {
            println("Cannot handle answer: not in creating state")
            return
        }
        
        println("Handling answer from $userId in room ${room.callId}")
        
        // Reenviar al OTRO peer
        val otherSession = room.clients.values.firstOrNull { session ->
            room.clients.entries.first { it.value == session }.key != userId
        }
        
        otherSession?.let { sendRawToSession(it, message) }
        room.sessionState = WebRTCSessionState.ACTIVE
        notifyStateUpdate(room)
    }

    private fun handleIce(room: RoomState, userId: String, message: String) {
        println("Handling ICE from $userId in room ${room.callId}")
        
        // Reenviar al OTRO peer
        val otherSession = room.clients.values.firstOrNull { session ->
            room.clients.entries.first { it.value == session }.key != userId
        }
        
        otherSession?.let { sendRawToSession(it, message) }
    }

    private fun handleHangup(room: RoomState, userId: String) {
        println("Hangup from $userId in room ${room.callId}")
        
        scope.launch {
            mutex.withLock {
                room.clients.values.forEach { session ->
                    sendToSession(session, CallEndedMessage(reason = "hangup"))
                }
                room.clients.clear()
                room.sessionState = WebRTCSessionState.IMPOSSIBLE
                rooms.remove(room.callId)
            }
        }
    }

    /**
     * Peer se desconecta
     */
    suspend fun onPeerLeft(callId: String, userId: String) {
        val room = rooms[callId] ?: return
        
        mutex.withLock {
            room.clients.remove(userId)
            room.sessionState = WebRTCSessionState.IMPOSSIBLE
            
            // Notificar al otro peer que se fue
            room.clients.values.forEach { session ->
                sendToSession(session, PeerLeftMessage(userId = userId))
            }
            
            // Si no queda nadie, limpiar room
            if (room.clients.isEmpty()) {
                room.ringTimer?.cancel()
                rooms.remove(callId)
            }
        }
    }

    private fun notifyStateUpdate(room: RoomState) {
        room.clients.values.forEach { session ->
            sendToSession(session, StateMessage(status = room.sessionState.name.lowercase()))
        }
    }

    private fun sendToSession(session: DefaultWebSocketServerSession, message: WebSocketMessage) {
        scope.launch {
            try {
                val json = Json.encodeToString(WebSocketMessage.serializer(), message)
                session.send(Frame.Text(json))
            } catch (e: Exception) {
                println("Error sending to session: $e")
            }
        }
    }

    private fun sendRawToSession(session: DefaultWebSocketServerSession, message: String) {
        scope.launch {
            try {
                session.send(Frame.Text(message))
            } catch (e: Exception) {
                println("Error sending raw to session: $e")
            }
        }
    }
}
```

---

## 4. Rutas WebSocket

### `routes/WebSocketRoutes.kt`

```kotlin
package com.tuempresa.routes

import com.tuempresa.service.SignalingService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.*

fun Application.configureWebSocketRoutes() {
    routing {
        authenticate("auth-jwt") {
            webSocket("/ws/call/{callId}") {
                val callId = call.parameters["callId"] 
                    ?: run {
                        close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing callId"))
                        return@webSocket
                    }
                
                // Obtener userId del JWT
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: run {
                        close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid token"))
                        return@webSocket
                    }
                
                try {
                    // Notificar que el peer se unió
                    SignalingService.onPeerJoined(callId, userId, this)
                    
                    // Loop principal: escuchar mensajes
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                SignalingService.onMessage(callId, userId, text)
                            }
                            else -> Unit
                        }
                    }
                    
                    println("WS connection closed normally for user $userId")
                    SignalingService.onPeerLeft(callId, userId)
                    
                } catch (e: ClosedReceiveChannelException) {
                    println("WS connection closed by client: $userId")
                    SignalingService.onPeerLeft(callId, userId)
                } catch (e: Throwable) {
                    println("WS error for user $userId: $e")
                    SignalingService.onPeerLeft(callId, userId)
                }
            }
        }
    }
}
```

---

## 5. Rutas HTTP (Endpoints REST)

### `routes/CallRoutes.kt`

```kotlin
package com.tuempresa.routes

import com.tuempresa.model.*
import com.tuempresa.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bson.types.ObjectId
import java.time.Instant

fun Application.configureCallRoutes() {
    routing {
        authenticate("auth-jwt") {
            
            // POST /api/appointments/{appointmentId}/call
            // Doctor inicia la llamada
            post("/api/appointments/{appointmentId}/call") {
                val appointmentId = call.parameters["appointmentId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing appointmentId")
                
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                // Validar que la cita existe y está confirmada
                // TODO: Integrar con tu repository de citas
                val cita = obtenerCita(appointmentId) 
                    ?: return@post call.respond(HttpStatusCode.NotFound, "Appointment not found")
                
                if (cita.estado != "confirmada") {
                    return@post call.respond(HttpStatusCode.BadRequest, "Appointment must be confirmed")
                }
                
                if (cita.doctorId != userId) {
                    return@post call.respond(HttpStatusCode.Forbidden, "Only the doctor can initiate")
                }
                
                // Verificar que no haya una llamada activa
                val llamadaActiva = obtenerLlamadaActiva(appointmentId)
                if (llamadaActiva != null) {
                    return@post call.respond(HttpStatusCode.Conflict, "Call already in progress")
                }
                
                // Crear registro en HistorialLlamadas
                val historial = HistorialLlamadas(
                    appointmentId = appointmentId,
                    doctorId = cita.doctorId,
                    patientId = cita.patientId,
                    estado = CallEstado.RINGING,
                    iniciadaPor = userId
                )
                
                guardarHistorial(historial)
                
                // Crear room en el servicio de señalización
                SignalingService.createRoom(
                    callId = historial.id.toString(),
                    appointmentId = appointmentId,
                    doctorId = cita.doctorId,
                    patientId = cita.patientId
                )
                
                // TODO: Enviar push notification al paciente
                enviarNotificacionLlamada(cita.patientId, cita.doctorId, appointmentId)
                
                call.respond(HttpStatusCode.Created, mapOf(
                    "callId" to historial.id.toString(),
                    "appointmentId" to appointmentId,
                    "estado" to "ringing",
                    "wsUrl" to "wss://${call.request.host()}/ws/call/${historial.id}"
                ))
            }
            
            // POST /api/calls/{callId}/join
            // Paciente se une a la llamada
            post("/api/calls/{callId}/join") {
                val callId = call.parameters["callId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing callId")
                
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val historial = obtenerHistorialPorId(callId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, "Call not found")
                
                if (historial.patientId != userId && historial.doctorId != userId) {
                    return@post call.respond(HttpStatusCode.Forbidden)
                }
                
                if (historial.estado == CallEstado.ENDED || 
                    historial.estado == CallEstado.MISSED ||
                    historial.estado == CallEstado.DECLINED) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Call already finished")
                }
                
                call.respond(HttpStatusCode.OK, mapOf(
                    "callId" to callId,
                    "estado" to historial.estado.name.lowercase(),
                    "wsUrl" to "wss://${call.request.host()}/ws/call/$callId"
                ))
            }
            
            // POST /api/calls/{callId}/end
            // Finalizar llamada (cualquiera puede cortar)
            post("/api/calls/{callId}/end") {
                val callId = call.parameters["callId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing callId")
                
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val historial = obtenerHistorialPorId(callId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, "Call not found")
                
                if (historial.patientId != userId && historial.doctorId != userId) {
                    return@post call.respond(HttpStatusCode.Forbidden)
                }
                
                // Actualizar en DB
                val now = Instant.now()
                val duracion = java.time.Duration.between(historial.fechaInicio, now).seconds.toInt()
                
                actualizarHistorial(
                    callId,
                    estado = CallEstado.ENDED,
                    fechaFin = now,
                    duracionSegundos = duracion
                )
                
                // Notificar vía WebSocket que terminó
                SignalingService.onMessage(
                    callId, 
                    userId, 
                    "{\"type\": \"hangup\"}"
                )
                
                call.respond(HttpStatusCode.OK, mapOf(
                    "callId" to callId,
                    "estado" to "ended",
                    "duracionSegundos" to duracion
                ))
            }
            
            // POST /api/calls/{callId}/decline
            // Paciente rechaza la llamada
            post("/api/calls/{callId}/decline") {
                val callId = call.parameters["callId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing callId")
                
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val historial = obtenerHistorialPorId(callId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, "Call not found")
                
                if (historial.patientId != userId) {
                    return@post call.respond(HttpStatusCode.Forbidden, "Only the patient can decline")
                }
                
                actualizarHistorial(callId, estado = CallEstado.DECLINED)
                
                // Notificar al doctor que fue rechazada
                SignalingService.onMessage(
                    callId,
                    userId,
                    "{\"type\": \"hangup\"}"
                )
                
                call.respond(HttpStatusCode.OK, mapOf(
                    "callId" to callId,
                    "estado" to "declined"
                ))
            }
            
            // GET /api/appointments/{appointmentId}/calls
            // Obtener historial de llamadas de una cita
            get("/api/appointments/{appointmentId}/calls") {
                val appointmentId = call.parameters["appointmentId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing appointmentId")
                
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                // Validar que el usuario es doctor o paciente de esta cita
                val cita = obtenerCita(appointmentId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                
                if (cita.doctorId != userId && cita.patientId != userId) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }
                
                val llamadas = obtenerHistorialPorCita(appointmentId)
                
                call.respond(HttpStatusCode.OK, mapOf(
                    "calls" to llamadas.map { h ->
                        mapOf(
                            "callId" to h.id.toString(),
                            "estado" to h.estado.name.lowercase(),
                            "fechaInicio" to h.fechaInicio.toString(),
                            "fechaFin" to h.fechaFin?.toString(),
                            "duracionSegundos" to h.duracionSegundos
                        )
                    }
                ))
            }
        }
    }
}

// Placeholder functions - reemplazar con tus repositories reales
fun obtenerCita(appointmentId: String): Cita? = TODO("Implementar con tu repository")
fun obtenerLlamadaActiva(appointmentId: String): HistorialLlamadas? = TODO("Implementar con tu repository")
fun obtenerHistorialPorId(callId: String): HistorialLlamadas? = TODO("Implementar con tu repository")
fun obtenerHistorialPorCita(appointmentId: String): List<HistorialLlamadas> = TODO("Implementar con tu repository")
fun guardarHistorial(historial: HistorialLlamadas) = TODO("Implementar con tu repository")
fun actualizarHistorial(callId: String, estado: CallEstado, fechaFin: Instant? = null, duracionSegundos: Int? = null) = TODO("Implementar con tu repository")
fun enviarNotificacionLlamada(patientId: String, doctorId: String, appointmentId: String) = TODO("Implementar con tu servicio de push notifications")

// Placeholder data class
data class Cita(
    val appointmentId: String,
    val doctorId: String,
    val patientId: String,
    val estado: String
)
```

---

## 6. Configuración de WebSockets

### `plugins/WebSockets.kt`

```kotlin
package com.tuempresa.plugins

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
```

---

## 7. Application.kt (Entry Point)

```kotlin
package com.tuempresa

import com.tuempresa.plugins.*
import com.tuempresa.routes.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Configurar plugins
    configureSecurity()      // Tu configuración JWT existente
    configureWebSockets()
    configureSerialization()
    
    // Configurar rutas
    configureCallRoutes()
    configureWebSocketRoutes()
}
```

---

## 8. Dependencias Gradle

Agregar a tu `build.gradle.kts`:

```kotlin
dependencies {
    // Ktor core
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    
    // WebSockets
    implementation("io.ktor:ktor-server-websockets:$ktor_version")
    
    // Auth JWT (si no lo tienes)
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    
    // Serialización JSON
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // MongoDB (si usas KMongo o similar)
    implementation("org.litote.kmongo:kmongo-coroutine:4.11.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
}
```

---

## 9. Configuración application.yaml

```yaml
ktor:
  deployment:
    port: 8080
  application:
    modules:
      - com.tuempresa.ApplicationKt.module

jwt:
  secret: "tu-secret-key-aqui"
  issuer: "tu-app"
  audience: "tu-app-users"
  realm: "tu-app realm"

mongodb:
  connectionString: "mongodb://localhost:27017"
  database: "tu-database"
```

---

## 10. Notas Importantes

### ✅ Lo que ESTÁ incluido:
- Servidor de señalización WebRTC completo
- Soporte para múltiples "rooms" (una por llamada)
- Autenticación JWT
- Timeout de 45s para pasar a "missed"
- Estados de llamada (ringing, active, ended, missed, declined)
- Endpoints REST para iniciar, unirse, finalizar y rechazar
- Protocolo WebSocket completo
- Manejo de errores y desconexiones

### ⚠️ Lo que DEBES adaptar:
- `TODO()` functions: Reemplazar con tus repositories MongoDB reales
- `enviarNotificacionLlamada()`: Integrar con Firebase Cloud Messaging o similar
- Configuración JWT: Adaptar a tu sistema de autenticación actual
- Modelo de citas: Ajustar según tu schema MongoDB existente
- Push notifications: Implementar según tu plataforma (FCM para Android, APNS para iOS)

---

*Código listo para integrar en tu servidor existente*
