# Guía de Implementación Paso a Paso

> Instrucciones detalladas para integrar el sistema de videollamadas en tu servidor existente.

---

## Fase 1: Preparación (Día 1)

### 1.1 Revisar tu servidor actual

Antes de tocar nada, asegurate de tener:
- [ ] Servidor Ktor funcionando con autenticación JWT
- [ ] MongoDB conectado y funcionando
- [ ] Endpoints de citas médicas operativos
- [ ] Schema de citas con los campos: `appointmentId`, `doctorId`, `patientId`, `estado`

### 1.2 Backup

```bash
# Hacer backup de tu código actual
git checkout -b feature/videollamadas
git add .
git commit -m "Backup antes de implementar videollamadas"
```

---

## Fase 2: Modelos de Datos (Día 1-2)

### 2.1 Crear documento HistorialLlamadas en MongoDB

En tu colección MongoDB, agregar el documento:

```kotlin
// En tu package de modelos (ej: com.tuempresa.model)

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
    RINGING,
    ACTIVE,
    ENDED,
    MISSED,
    DECLINED
}
```

### 2.2 Agregar dependencias

```kotlin
// build.gradle.kts

dependencies {
    // Si NO tenés WebSockets:
    implementation("io.ktor:ktor-server-websockets:$ktor_version")
    
    // Si NO tenés serialización JSON:
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // WebRTC - solo si necesitás algo en el servidor (normalmente no)
    // El servidor solo retransmite, no procesa WebRTC
}
```

### 2.3 Crear repositorio para HistorialLlamadas

```kotlin
// repository/HistorialLlamadasRepository.kt

interface HistorialLlamadasRepository {
    suspend fun create(historial: HistorialLlamadas): HistorialLlamadas
    suspend fun findById(id: String): HistorialLlamadas?
    suspend fun findByAppointmentId(appointmentId: String): List<HistorialLlamadas>
    suspend fun findActiveByAppointmentId(appointmentId: String): HistorialLlamadas?
    suspend fun update(id: String, update: HistorialLlamadasUpdate): Boolean
}

data class HistorialLlamadasUpdate(
    val estado: CallEstado? = null,
    val fechaFin: Instant? = null,
    val duracionSegundos: Int? = null
)
```

Implementación con KMongo:

```kotlin
class HistorialLlamadasRepositoryImpl(
    private val collection: CoroutineCollection<HistorialLlamadas>
) : HistorialLlamadasRepository {
    
    override suspend fun create(historial: HistorialLlamadas): HistorialLlamadas {
        collection.insertOne(historial)
        return historial
    }
    
    override suspend fun findById(id: String): HistorialLlamadas? {
        return collection.findOneById(ObjectId(id))
    }
    
    override suspend fun findByAppointmentId(appointmentId: String): List<HistorialLlamadas> {
        return collection.find(HistorialLlamadas::appointmentId eq appointmentId)
            .toList()
    }
    
    override suspend fun findActiveByAppointmentId(appointmentId: String): HistorialLlamadas? {
        return collection.find(
            and(
                HistorialLlamadas::appointmentId eq appointmentId,
                HistorialLlamadas::estado `in` listOf(CallEstado.RINGING, CallEstado.ACTIVE)
            )
        ).firstOrNull()
    }
    
    override suspend fun update(id: String, update: HistorialLlamadasUpdate): Boolean {
        val updates = mutableListOf<SetTo<*>>()
        
        update.estado?.let { updates.add(HistorialLlamadas::estado setTo it) }
        update.fechaFin?.let { updates.add(HistorialLlamadas::fechaFin setTo it) }
        update.duracionSegundos?.let { updates.add(HistorialLlamadas::duracionSegundos setTo it) }
        
        if (updates.isEmpty()) return false
        
        val result = collection.updateOneById(
            ObjectId(id),
            set(*updates.toTypedArray())
        )
        
        return result.modifiedCount > 0
    }
}
```

---

## Fase 3: Servicio de Señalización (Día 2-3)

### 3.1 Copiar SignalingService

Copiar el archivo `SignalingService.kt` del `SERVER_CODE.md` a tu proyecto en:
```
src/main/kotlin/com/tuempresa/service/SignalingService.kt
```

### 3.2 Configurar WebSockets

En tu `Application.kt` o archivo de configuración:

```kotlin
fun Application.configurePlugins() {
    // ... tu config existente ...
    
    // Agregar WebSockets
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
```

### 3.3 Configurar rutas WebSocket

Crear archivo `routes/WebSocketRoutes.kt`:

```kotlin
package com.tuempresa.routes

import com.tuempresa.service.SignalingService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException

fun Application.configureWebSocketRoutes() {
    routing {
        authenticate("auth-jwt") {
            webSocket("/ws/call/{callId}") {
                val callId = call.parameters["callId"] 
                    ?: run {
                        close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing callId"))
                        return@webSocket
                    }
                
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: run {
                        close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid token"))
                        return@webSocket
                    }
                
                try {
                    SignalingService.onPeerJoined(callId, userId, this)
                    
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                SignalingService.onMessage(callId, userId, frame.readText())
                            }
                            else -> Unit
                        }
                    }
                    
                    println("WS closed normally for user $userId")
                    SignalingService.onPeerLeft(callId, userId)
                    
                } catch (e: ClosedReceiveChannelException) {
                    println("WS closed by client: $userId")
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

### 3.4 Registrar rutas en Application.kt

```kotlin
fun Application.module() {
    // ... config existente ...
    
    configureWebSocketRoutes()
    configureCallRoutes()  // Lo creamos en la siguiente fase
}
```

---

## Fase 4: Endpoints REST (Día 3-4)

### 4.1 Crear CallRoutes.kt

Copiar el archivo `CallRoutes.kt` del `SERVER_CODE.md` y adaptar las funciones placeholder.

### 4.2 Integrar con tus repositories existentes

Reemplazar las funciones `TODO()` con tus repositories reales:

```kotlin
// En CallRoutes.kt, reemplazar:

fun obtenerCita(appointmentId: String): Cita? {
    return citaRepository.findById(appointmentId)  // Tu repository real
}

fun obtenerLlamadaActiva(appointmentId: String): HistorialLlamadas? {
    return historialRepository.findActiveByAppointmentId(appointmentId)
}

fun guardarHistorial(historial: HistorialLlamadas) {
    historialRepository.create(historial)
}

// ... etc
```

### 4.3 Implementar envío de Push Notifications

```kotlin
// service/PushNotificationService.kt

class PushNotificationService(
    private val fcmService: FCMService,  // O tu servicio de push actual
    private val userRepository: UserRepository
) {
    suspend fun enviarLlamadaEntrante(patientId: String, doctorId: String, callId: String) {
        val doctor = userRepository.findById(doctorId)
        val patientFcmToken = userRepository.getFcmToken(patientId)
        
        if (patientFcmToken != null) {
            fcmService.send(
                token = patientFcmToken,
                title = "Llamada entrante",
                body = "Dr. ${doctor?.nombre ?: "Doctor"} está llamándote",
                data = mapOf(
                    "type" to "incoming_call",
                    "callId" to callId,
                    "doctorName" to (doctor?.nombre ?: "")
                )
            )
        }
    }
}
```

---

## Fase 5: Seguridad (Día 4)

### 5.1 Validar acceso a llamadas

Asegurar que solo el doctor y paciente de UNA cita específica puedan unirse:

```kotlin
// En SignalingService.onPeerJoined()

suspend fun onPeerJoined(callId: String, userId: String, session: DefaultWebSocketServerSession) {
    val room = rooms[callId] ?: run {
        session.close(CloseReason(CloseReason.Codes.NORMAL, "Room not found"))
        return
    }
    
    // VALIDACIÓN CRÍTICA
    if (userId != room.doctorId && userId != room.patientId) {
        session.close(CloseReason(CloseReason.Codes.NORMAL, "Unauthorized"))
        return
    }
    
    // ... resto del código
}
```

### 5.2 Validar estado de la cita

Antes de permitir iniciar una llamada:

```kotlin
// En CallRoutes.kt

if (cita.estado != "confirmada") {
    return@post call.respond(HttpStatusCode.BadRequest, "Appointment must be confirmed")
}

// Opcional: Validar que sea la fecha/hora de la cita
if (!esHoraDeLaCita(cita)) {
    return@post call.respond(HttpStatusCode.BadRequest, "It's not time for this appointment yet")
}
```

---

## Fase 6: Tests (Día 5)

### 6.1 Tests unitarios

```kotlin
// test/kotlin/service/SignalingServiceTest.kt

class SignalingServiceTest {
    
    @Test
    fun `should create room when doctor initiates call`() = runBlocking {
        // Given
        val callId = "test-call-id"
        val appointmentId = "20260504-N6IBY2"
        val doctorId = "87654321"
        val patientId = "12345678"
        
        // When
        SignalingService.createRoom(callId, appointmentId, doctorId, patientId)
        
        // Then
        val room = SignalingService.getRoom(callId)
        assertNotNull(room)
        assertEquals(doctorId, room?.doctorId)
        assertEquals(patientId, room?.patientId)
    }
    
    @Test
    fun `should reject third peer`() = runBlocking {
        // Given
        val callId = "test-call-id"
        // ... setup room with 2 peers
        
        // When & Then
        // Third peer should be rejected
    }
}
```

### 6.2 Tests de integración

```kotlin
// test/kotlin/routes/CallRoutesTest.kt

class CallRoutesTest {
    
    @Test
    fun `should return 403 if non-doctor tries to initiate call`() = testApplication {
        // Setup
        // ...
        
        // Execute
        val response = client.post("/api/appointments/123/call") {
            header(HttpHeaders.Authorization, "Bearer ${tokenPaciente}")
        }
        
        // Verify
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
    
    @Test
    fun `should create call and return ws url`() = testApplication {
        // Setup con token de doctor
        // ...
        
        // Execute
        val response = client.post("/api/appointments/20260504-N6IBY2/call") {
            header(HttpHeaders.Authorization, "Bearer ${tokenDoctor}")
        }
        
        // Verify
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<Map<String, String>>()
        assertTrue(body["wsUrl"]!!.contains("/ws/call/"))
    }
}
```

### 6.3 Test manual con cliente WebSocket

```bash
# Terminal 1: Conectar como Doctor
wscat -c "ws://localhost:8080/ws/call/test-call-id" \
  -H "Authorization: Bearer TOKEN_DOCTOR"

# Terminal 2: Conectar como Paciente  
wscat -c "ws://localhost:8080/ws/call/test-call-id" \
  -H "Authorization: Bearer TOKEN_PACIENTE"

# En Terminal 1 (Doctor), enviar:
{"type": "offer", "sdp": "v=0\no=-..."}

# En Terminal 2 (Paciente), debería recibir el offer
```

---

## Fase 7: Cliente (Día 6-10)

### 7.1 Android

1. Agregar permisos en `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

2. Copiar `WebRTCManager.kt` del `CLIENT_CODE.md`

3. Crear Activities:
   - `DoctorCallActivity` - Para iniciar llamada
   - `IncomingCallActivity` - Para recibir llamada (con sonido y vibración)
   - `PatientCallActivity` - Pantalla durante la videollamada

4. Configurar Firebase Cloud Messaging para push notifications

### 7.2 iOS

1. Agregar framework WebRTC de Google
2. Implementar CallKit para integración nativa de llamadas
3. Configurar Push Notifications con APNS
4. Implementar pantalla de llamada entrante

### 7.3 Web

1. Copiar `webrtc-client.js` del `CLIENT_CODE.md`
2. Crear HTML de videollamada
3. Manejar permisos de cámara/micrófono

---

## Fase 8: Deployment (Día 11)

### 8.1 Configurar en producción

```yaml
# application.prod.yaml
ktor:
  deployment:
    port: 8080
    # Si usás HTTPS:
    ssl:
      port: 8443
      keyStore: path/to/keystore.jks
      keyStorePassword: password
      privateKeyPassword: password

# WebSocket en producción necesita WSS (WebSocket Secure)
# Configurar reverse proxy (nginx) o SSL directo
```

### 8.2 Nginx reverse proxy (opcional)

```nginx
server {
    listen 443 ssl;
    server_name api.tudominio.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    # WebSocket específico
    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }
}
```

### 8.3 Health checks

```kotlin
// Endpoint de health check
get("/health") {
    call.respond(mapOf(
        "status" to "ok",
        "timestamp" to Instant.now().toString(),
        "activeCalls" to SignalingService.getActiveRoomsCount(),
        "activeConnections" to SignalingService.getActiveConnectionsCount()
    ))
}
```

---

## Fase 9: Monitoreo (Día 12)

### 9.1 Logging

```kotlin
// Configurar Logback para logging estructurado
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SignalingService::class.java)

// En vez de println, usar:
logger.info("Call started: callId=$callId, doctorId=$doctorId, patientId=$patientId")
logger.error("WebSocket error for user $userId", e)
```

### 9.2 Métricas

```kotlin
// Agregar métricas simples
object CallMetrics {
    val callsInitiated = AtomicInteger(0)
    val callsAnswered = AtomicInteger(0)
    val callsMissed = AtomicInteger(0)
    val activeConnections = AtomicInteger(0)
    
    fun recordCallInitiated() = callsInitiated.incrementAndGet()
    fun recordCallAnswered() = callsAnswered.incrementAndGet()
    fun recordCallMissed() = callsMissed.incrementAndGet()
    fun recordConnectionOpened() = activeConnections.incrementAndGet()
    fun recordConnectionClosed() = activeConnections.decrementAndGet()
}
```

---

## Checklist Final

### Servidor
- [ ] Modelo `HistorialLlamadas` creado en MongoDB
- [ ] Repositorio implementado
- [ ] `SignalingService` copiado y funcionando
- [ ] WebSocket routes configuradas
- [ ] Endpoints REST implementados
- [ ] Autenticación JWT funcionando en WS
- [ ] Validaciones de seguridad implementadas
- [ ] Push notifications configuradas
- [ ] Tests unitarios pasando
- [ ] Tests de integración pasando
- [ ] Logging configurado
- [ ] Health checks funcionando

### Cliente
- [ ] Android: Permisos agregados
- [ ] Android: WebRTCManager implementado
- [ ] Android: Pantallas de llamada creadas
- [ ] Android: Push notifications funcionando
- [ ] iOS: Implementación completa
- [ ] Web: Cliente JavaScript funcionando
- [ ] Todos los clientes pueden conectarse al servidor

### Producción
- [ ] Servidor desplegado
- [ ] SSL/WSS configurado
- [ ] Nginx reverse proxy (si aplica)
- [ ] Monitoreo activo
- [ ] Rollback plan definido

---

## Troubleshooting Común

### Problema: WebSocket no conecta
**Causa**: Firewall bloqueando puerto, o falta configuración de upgrade
**Solución**: Verificar que el proxy/reverse proxy pase los headers `Upgrade` y `Connection`

### Problema: ICE candidates fallan
**Causa**: STUN/TURN servers no configurados
**Solución**: Agregar servidores TURN para NAT traversal. Opciones gratuitas:
- `stun:stun.l.google.com:19302`
- `stun:stun1.l.google.com:19302`
- Para TURN (relay): usar Twilio, Xirsys, o tu propio servidor TURN

### Problema: No se ve video
**Causa**: Permisos de cámara no otorgados
**Solución**: Verificar permisos en AndroidManifest/iOS Info.plist, y solicitar runtime permissions

### Problema: Llamada se corta a los 30 segundos
**Causa**: Timeout de proxy/nginx
**Solución**: Aumentar `proxy_read_timeout` en nginx o equivalente

### Problema: WebRTC funciona en LAN pero no en 4G/5G
**Causa**: NAT traversal. Los peers no pueden encontrarse directamente
**Solución**: Agregar TURN server. WebRTC necesita TURN cuando ambos están en redes restrictivas

---

## Recursos Adicionales

- **WebRTC MDN**: https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API
- **Ktor WebSockets**: https://ktor.io/docs/websocket.html
- **WebRTC.org**: https://webrtc.org/getting-started/overview
- **STUN/TURN servers**: https://github.com/coturn/coturn (para self-hosting)

---

*Guía completa para implementar videollamadas en tu servidor existente*
