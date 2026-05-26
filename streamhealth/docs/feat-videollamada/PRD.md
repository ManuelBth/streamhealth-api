# PRD - Videollamadas Médicas (WebRTC Signaling)

## 1. Visión General

Sistema de videollamadas peer-to-peer (P2P) integrado en la plataforma de citas médicas. El doctor inicia la llamada y el paciente la recibe como una llamada normal (patrón "Ring"), similar a WhatsApp Call.

**Stack técnico de referencia:**
- Backend: Ktor (Kotlin) + Netty
- Base de datos: MongoDB
- Comunicación: WebSockets
- Protocolo: WebRTC con servidor de señalización

---

## 2. Requisitos Funcionales

### 2.1 Flujo Principal (Ring)

```
1. Doctor abre app → Ve cita confirmada → Toca "Llamar Paciente"
2. Backend crea registro en HistorialLlamadas (estado: ringing)
3. Backend envía señal al paciente vía WebSocket/Push
4. Paciente recibe pantalla de llamada entrante (suena + vibra)
5. Paciente toca "Contestar"
6. Se establece conexión WebRTC P2P (video + audio)
7. Al finalizar, cualquiera corta → estado: ended + duración
```

### 2.2 Estados de la Llamada

| Estado | Descripción |
|--------|-------------|
| `ringing` | Doctor inició, esperando respuesta del paciente |
| `active` | Paciente contestó, llamada en curso |
| `ended` | Finalizada normalmente |
| `missed` | Sonó pero no contestó (timeout 45s) |
| `declined` | Paciente rechazó explícitamente |

---

## 3. Requisitos No Funcionales

| Requisito | Especificación |
|-----------|---------------|
| **Tiempo de setup** | < 3 segundos desde que el paciente contesta hasta ver video |
| **Soporte simultáneo** | 1 llamada por cita (solo 2 peers) |
| **Seguridad** | Solo doctor y paciente de la cita pueden unirse |
| **Timeout ring** | 45 segundos, luego pasa a `missed` |
| **Reconexión** | Si se corta, debe poder reintentar (nueva sesión) |
| **Compatibilidad** | iOS 14+, Android 10+, Chrome, Safari, Firefox |

---

## 4. Schema de Datos

### 4.1 HistorialLlamadas (MongoDB)

```json
{
  "_id": "ObjectId",
  "appointmentId": "20260504-N6IBY2",
  "doctorId": "87654321",
  "patientId": "12345678",
  "estado": "ringing",
  "iniciadaPor": "87654321",
  "fechaInicio": "2026-12-15T10:05:00Z",
  "fechaFin": "2026-12-15T10:25:00Z",
  "duracionSegundos": 1200
}
```

### 4.2 Estados Permitidos

```
ringing → active → ended
ringing → missed (timeout 45s)
ringing → declined (paciente rechaza)
```

---

## 5. API Endpoints

### 5.1 Iniciar Llamada (Doctor)

```http
POST /api/appointments/{appointmentId}/call
Authorization: Bearer {jwt_token}
Content-Type: application/json

Response 201 Created:
{
  "callId": "507f1f77bcf86cd799439011",
  "appointmentId": "20260504-N6IBY2",
  "estado": "ringing",
  "wsUrl": "wss://api.tudominio.com/ws/call/507f1f77bcf86cd799439011"
}
```

### 5.2 Unirse a Llamada (Paciente)

```http
POST /api/calls/{callId}/join
Authorization: Bearer {jwt_token}
Content-Type: application/json

Response 200 OK:
{
  "callId": "507f1f77bcf86cd799439011",
  "estado": "active",
  "wsUrl": "wss://api.tudominio.com/ws/call/507f1f77bcf86cd799439011"
}
```

### 5.3 Finalizar Llamada

```http
POST /api/calls/{callId}/end
Authorization: Bearer {jwt_token}
Content-Type: application/json

Response 200 OK:
{
  "callId": "507f1f77bcf86cd799439011",
  "estado": "ended",
  "duracionSegundos": 1250
}
```

### 5.4 Obtener Historial de Llamadas de una Cita

```http
GET /api/appointments/{appointmentId}/calls
Authorization: Bearer {jwt_token}

Response 200 OK:
{
  "calls": [
    {
      "callId": "507f1f77bcf86cd799439011",
      "estado": "ended",
      "fechaInicio": "2026-12-15T10:05:00Z",
      "fechaFin": "2026-12-15T10:25:00Z",
      "duracionSegundos": 1200
    }
  ]
}
```

---

## 6. WebSocket Protocol

### 6.1 Conexión

```
wss://api.tudominio.com/ws/call/{callId}
Headers: Authorization: Bearer {jwt_token}
```

### 6.2 Tipos de Mensajes

#### Cliente → Servidor

| Tipo | Descripción | Ejemplo |
|------|-------------|---------|
| `state` | Solicitar estado actual de la sesión | `{"type": "state"}` |
| `offer` | Enviar SDP offer (WebRTC) | `{"type": "offer", "sdp": "..."}` |
| `answer` | Enviar SDP answer (WebRTC) | `{"type": "answer", "sdp": "..."}` |
| `ice` | Enviar ICE candidate | `{"type": "ice", "candidate": "..."}` |
| `hangup` | Colgar la llamada | `{"type": "hangup"}` |

#### Servidor → Cliente

| Tipo | Descripción | Ejemplo |
|------|-------------|---------|
| `state` | Estado actual de la sesión | `{"type": "state", "status": "ready"}` |
| `offer` | Reenviar offer del otro peer | `{"type": "offer", "sdp": "..."}` |
| `answer` | Reenviar answer del otro peer | `{"type": "answer", "sdp": "..."}` |
| `ice` | Reenviar ICE candidate | `{"type": "ice", "candidate": "..."}` |
| `peer_joined` | El otro peer se conectó | `{"type": "peer_joined", "userId": "12345678"}` |
| `peer_left` | El otro peer se desconectó | `{"type": "peer_left", "userId": "12345678"}` |
| `call_ended` | La llamada terminó | `{"type": "call_ended", "reason": "hangup"}` |

### 6.3 Estados de Sesión WebRTC

```
impossible → ready → creating → active
```

| Estado | Significado |
|--------|-------------|
| `impossible` | Menos de 2 peers conectados |
| `ready` | 2 peers conectados, listos para iniciar |
| `creating` | Offer enviado, esperando answer |
| `active` | Answer recibido, conexión P2P establecida |

---

## 7. Secuencia de Mensajes WebSocket (Flujo Completo)

```
DOCTOR                                          PACIENTE
  │                                               │
  │  1. Conecta a WS /ws/call/{callId}           │
  │──────────────────────────────────────────────►│
  │                                               │
  │  2. Server: {type: "state", status: "ready"}│
  │◄─────────────────────────────────────────────│
  │                                               │
  │  3. Genera SDP Offer (WebRTC)                 │
  │  4. Envía: {type: "offer", sdp: "..."}       │
  │──────────────────────────────────────────────►│
  │                                               │
  │                          5. Recibe offer      │
  │                          6. Genera SDP Answer │
  │  7. Recibe: {type: "answer", sdp: "..."}     │
  │◄─────────────────────────────────────────────│
  │                                               │
  │  8. Intercambio de ICE candidates             │
  │◄─────────────────────────────────────────────►│
  │                                               │
  │  9. Conexión P2P establecida (video/audio)   │
  │◄═════════════════════════════════════════════►│
  │                                               │
  │  10. Doctor o Paciente envía "hangup"        │
  │──────────────────────────────────────────────►│
  │  11. Server: {type: "call_ended"}            │
  │◄─────────────────────────────────────────────│
```

---

## 8. Seguridad

### 8.1 Autorización

- **JWT Token** requerido en todas las peticiones HTTP y en el header del WebSocket
- Validar que el `userId` del token corresponda al `doctorId` o `patientId` de la cita
- Rechazar conexiones no autorizadas con `403 Forbidden`

### 8.2 Validaciones

```
1. ¿El appointmentId existe? → Si no, 404
2. ¿La cita está en estado "confirmada"? → Si no, 400
3. ¿El usuario autenticado es doctor o paciente de esta cita? → Si no, 403
4. ¿Ya hay una llamada active para esta cita? → Si sí, 409 Conflict
5. ¿El paciente está online/recibe la señal? → Si no, marcar como missed después de 45s
```

---

## 9. Casos de Error

| Escenario | Código HTTP | Respuesta |
|-----------|-------------|-----------|
| Cita no encontrada | 404 | `{"error": "Appointment not found"}` |
| Cita no confirmada | 400 | `{"error": "Appointment must be confirmed"}` |
| Usuario no autorizado | 403 | `{"error": "Forbidden"}` |
| Llamada ya en curso | 409 | `{"error": "Call already in progress"}` |
| Paciente no contesta | - | Estado: `missed` después de 45s |
| Fallo WebRTC | - | Reintentar 3 veces, luego error |
| Servidor cae | - | Reconectar WS automáticamente |

---

## 10. Métricas y Monitoreo

| Métrica | Descripción |
|---------|-------------|
| `call_initiated_total` | Total de llamadas iniciadas |
| `call_answered_total` | Total contestadas |
| `call_missed_total` | Total perdidas |
| `call_duration_seconds` | Duración promedio |
| `webrtc_setup_duration_ms` | Tiempo de setup P2P |
| `ws_connections_active` | Conexiones WS activas |

---

## 11. Límites y Restricciones

- **Máximo 2 peers** por llamada (doctor y paciente)
- **Timeout ring**: 45 segundos
- **Máximo intentos reconexión**: 3
- **Resolución video**: 720p máximo (ahorro ancho de banda)
- **Bitrate audio**: 64 kbps mínimo

---

## 12. Integración con Sistema Existente

### 12.1 Nuevos Archivos/Código a Agregar

Ver archivo `IMPLEMENTATION_GUIDE.md` para el paso a paso.

### 12.2 Archivos de Referencia Incluidos

- `SERVER_CODE.md` - Código del servidor de señalización
- `CLIENT_CODE.md` - Código del cliente WebRTC
- `WEBSOCKET_PROTOCOL.md` - Especificación detallada del protocolo WS

---

## 13. Roadmap Sugerido

### Fase 1: MVP (2 semanas)
- Servidor de señalización básico
- Documento HistorialLlamadas
- Endpoints HTTP
- WebSocket protocol
- Cliente básico (audio only)

### Fase 2: Completo (1 semana)
- Video
- Push notifications
- Pantalla de llamada entrante (Ring)
- Timeout y estados missed/declined

### Fase 3: Polish (1 semana)
- Métricas
- Logs
- Tests
- Optimización de setup

---

*Documento v1.0 - Generado para implementación*
