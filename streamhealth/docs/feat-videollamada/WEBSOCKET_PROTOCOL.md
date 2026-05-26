# Protocolo WebSocket - Especificación Detallada

> Documento técnico detallado del protocolo de comunicación WebSocket entre cliente y servidor para la señalización WebRTC.

---

## 1. Conexión

### 1.1 URL

```
wss://{host}/ws/call/{callId}
```

| Parámetro | Descripción | Ejemplo |
|-----------|-------------|---------|
| `host` | Tu dominio + puerto | `api.tudominio.com:8080` |
| `callId` | ID de la llamada (ObjectId de MongoDB) | `507f1f77bcf86cd799439011` |

### 1.2 Headers

```
Authorization: Bearer {jwt_token}
```

El JWT token DEBE contener al menos:
- `userId`: ID del usuario autenticado
- `role`: "doctor" o "patient" (opcional pero recomendado)

### 1.3 Flujo de Conexión

```
Cliente ──► Servidor: WS CONNECT /ws/call/{callId}
                    + Header: Authorization: Bearer {token}
                    
Servidor ──► Cliente: Validar JWT
                     Validar que userId sea doctor o patient de esta cita
                     
Servidor ──► Cliente: {type: "state", status: "ready|impossible"}
                     {type: "peer_joined", userId: "..."} (si el otro ya está)
```

---

## 2. Tipos de Mensajes

### 2.1 Cliente → Servidor

#### `state`
**Descripción**: Solicitar el estado actual de la sesión WebRTC

```json
{
  "type": "state"
}
```

**Respuesta del servidor**:
```json
{
  "type": "state",
  "status": "ready"
}
```

---

#### `offer`
**Descripción**: Enviar SDP Offer (solo el offerer, generalmente el doctor)

```json
{
  "type": "offer",
  "sdp": "v=0\r\no=- 1234567890 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=group:BUNDLE 0 1\r\n..."
}
```

**Cuándo enviar**: Después de que ambos peers están conectados (`status: ready`) y el doctor quiere iniciar la llamada.

**Respuesta del servidor**: Reenvía al OTRO peer sin modificar

---

#### `answer`
**Descripción**: Enviar SDP Answer (el answerer, generalmente el paciente)

```json
{
  "type": "answer", 
  "sdp": "v=0\r\no=- 9876543210 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\n..."
}
```

**Cuándo enviar**: Después de recibir y procesar el offer del doctor.

**Respuesta del servidor**: Reenvía al OTRO peer sin modificar

---

#### `ice`
**Descripción**: Enviar ICE Candidate descubierto por WebRTC

```json
{
  "type": "ice",
  "candidate": "candidate:842163049 1 udp 1677729535 192.168.1.100 54321 typ srflx raddr 0.0.0.0 rport 0 generation 0 ufrag abc123 network-id 1 network-cost 50",
  "sdpMid": "0",
  "sdpMLineIndex": 0
}
```

**Cuándo enviar**: WebRTC genera múltiples ICE candidates durante el proceso de conexión. Cada uno debe enviarse al servidor.

**Respuesta del servidor**: Reenvía al OTRO peer sin modificar

---

#### `hangup`
**Descripción**: Finalizar la llamada

```json
{
  "type": "hangup"
}
```

**Cuándo enviar**: Cuando el usuario toca el botón "Colgar".

**Respuesta del servidor**: 
```json
{
  "type": "call_ended",
  "reason": "hangup"
}
```

---

### 2.2 Servidor → Cliente

#### `state`
**Descripción**: Notificar el estado actual de la sesión

```json
{
  "type": "state",
  "status": "ready"
}
```

**Valores posibles de `status`**:
- `impossible`: Menos de 2 peers conectados
- `ready`: 2 peers conectados, listos para iniciar
- `creating`: Offer enviado, esperando answer
- `active`: Answer recibido, conexión P2P establecida

---

#### `peer_joined`
**Descripción**: Notificar que el otro peer se conectó

```json
{
  "type": "peer_joined",
  "userId": "12345678"
}
```

**Cuándo ocurre**: Cuando el segundo peer se conecta al WebSocket.

---

#### `peer_left`
**Descripción**: Notificar que el otro peer se desconectó

```json
{
  "type": "peer_left",
  "userId": "12345678"
}
```

**Cuándo ocurre**: Cuando el otro peer cierra el WebSocket (cuelga, se desconecta, app en background, etc.)

---

#### `offer`
**Descripción**: Reenviar SDP Offer del otro peer

```json
{
  "type": "offer",
  "sdp": "v=0\r\no=- 1234567890 2 IN IP4 127.0.0.1\r\n..."
}
```

**Cuándo ocurre**: El doctor envió un offer, el servidor lo retransmite al paciente.

---

#### `answer`
**Descripción**: Reenviar SDP Answer del otro peer

```json
{
  "type": "answer",
  "sdp": "v=0\r\no=- 9876543210 2 IN IP4 127.0.0.1\r\n..."
}
```

**Cuándo ocurre**: El paciente envió un answer, el servidor lo retransmite al doctor.

---

#### `ice`
**Descripción**: Reenviar ICE Candidate del otro peer

```json
{
  "type": "ice",
  "candidate": "candidate:842163049 1 udp 1677729535...",
  "sdpMid": "0",
  "sdpMLineIndex": 0
}
```

**Cuándo ocurre**: Periódicamente durante el proceso de establecimiento de conexión P2P.

---

#### `call_ended`
**Descripción**: Notificar que la llamada terminó

```json
{
  "type": "call_ended",
  "reason": "hangup"
}
```

**Valores posibles de `reason`**:
- `hangup`: Uno de los peers colgó
- `missed`: Timeout de 45 segundos, nadie contestó
- `error`: Error técnico

---

## 3. Diagramas de Secuencia

### 3.1 Flujo Completo - Doctor inicia, Paciente contesta

```
DOCTOR APP              SERVIDOR              PACIENTE APP
    │                       │                       │
    │  POST /api/appointments/{id}/call            │
    │──────────────────────►│                       │
    │                       │                       │
    │  {callId, wsUrl}      │                       │
    │◄──────────────────────│                       │
    │                       │                       │
    │  WS CONNECT /ws/call/{callId}                 │
    │──────────────────────►│                       │
    │                       │                       │
    │  {type:"state", status:"impossible"}         │
    │◄──────────────────────│                       │
    │                       │                       │
    │                       │  FCM Push Notification│
    │                       │──────────────────────►│
    │                       │  "Dr. Pérez llama"    │
    │                       │                       │
    │                       │                       │
    │                       │  WS CONNECT           │
    │                       │◄──────────────────────│
    │                       │                       │
    │  {type:"peer_joined", userId:"patient123"}    │
    │◄──────────────────────│                       │
    │                       │                       │
    │                       │  {type:"peer_joined", userId:"doctor456"}
    │                       │──────────────────────►│
    │                       │                       │
    │  {type:"state", status:"ready"}               │
    │◄──────────────────────│                       │
    │                       │  {type:"state", status:"ready"}
    │                       │──────────────────────►│
    │                       │                       │
    │  [DOCTOR toca "Llamar"]                       │
    │                       │                       │
    │  {type:"offer", sdp:"..."}                     │
    │──────────────────────►│                       │
    │                       │                       │
    │                       │  {type:"offer", sdp:"..."}
    │                       │──────────────────────►│
    │                       │                       │
    │                       │                       │
    │                       │  {type:"answer", sdp:"..."}
    │                       │◄──────────────────────│
    │                       │                       │
    │  {type:"answer", sdp:"..."}                   │
    │◄──────────────────────│                       │
    │                       │                       │
    │  [Intercambio de ICE candidates]              │
    │◄─────────────────────►│◄─────────────────────►│
    │                       │                       │
    │  [Conexión P2P establecida]                   │
    │◄══════════════════════╪══════════════════════►│
    │                       │                       │
    │  [Video + Audio fluye directamente P2P]      │
    │◄══════════════════════╪══════════════════════►│
    │                       │                       │
    │  [Cualquiera cuelga]                          │
    │                       │                       │
    │  {type:"hangup"}      │                       │
    │──────────────────────►│                       │
    │                       │                       │
    │  {type:"call_ended", reason:"hangup"}         │
    │◄──────────────────────│                       │
    │                       │  {type:"call_ended", reason:"hangup"}
    │                       │──────────────────────►│
```

### 3.2 Flujo - Paciente no contesta (Missed)

```
DOCTOR APP              SERVIDOR              PACIENTE APP
    │                       │                       │
    │  POST /api/appointments/{id}/call            │
    │──────────────────────►│                       │
    │                       │                       │
    │  WS CONNECT           │                       │
    │──────────────────────►│                       │
    │                       │                       │
    │  {type:"state", status:"impossible"}         │
    │◄──────────────────────│                       │
    │                       │                       │
    │                       │  FCM Push Notification  │
    │                       │──────────────────────►│
    │                       │  "Dr. Pérez llama"    │
    │                       │                       │
    │                       │                       │
    │  [Esperando... 45 segundos]                  │
    │                       │                       │
    │                       │  [Paciente NO abre app]│
    │                       │                       │
    │  {type:"call_ended", reason:"missed"}         │
    │◄──────────────────────│                       │
    │                       │                       │
    │  [Doctor ve "No contestó"]                    │
```

### 3.3 Flujo - Paciente rechaza (Declined)

```
DOCTOR APP              SERVIDOR              PACIENTE APP
    │                       │                       │
    │  POST /api/appointments/{id}/call            │
    │──────────────────────►│                       │
    │                       │                       │
    │  WS CONNECT           │                       │
    │──────────────────────►│                       │
    │                       │                       │
    │                       │  FCM Push             │
    │                       │──────────────────────►│
    │                       │                       │
    │                       │                       │
    │                       │  WS CONNECT           │
    │                       │◄──────────────────────│
    │                       │                       │
    │  {type:"state", status:"ready"}               │
    │◄──────────────────────│                       │
    │                       │  {type:"state", status:"ready"}
    │                       │──────────────────────►│
    │                       │                       │
    │                       │                       │
    │                       │  [PACIENTE toca "Rechazar"]
    │                       │                       │
    │                       │  POST /api/calls/{id}/decline
    │                       │◄──────────────────────│
    │                       │                       │
    │  {type:"call_ended", reason:"hangup"}         │
    │◄──────────────────────│                       │
    │                       │                       │
    │  [Doctor ve "Llamada rechazada"]              │
```

---

## 4. Estados y Transiciones

### 4.1 Estados de la Sesión WebRTC

```
                    ┌─────────────┐
         ┌─────────►│  IMPOSSIBLE │◄────────────────┐
         │          │  (< 2 peers)│                 │
         │          └──────┬──────┘                 │
         │                 │ 2do peer conecta        │
         │                 ▼                       │
         │          ┌─────────────┐                 │
         │          │    READY    │                 │
         │          │  (2 peers)  │                 │
         │          └──────┬──────┘                 │
         │                 │ Doctor envía offer    │
         │                 ▼                       │
         │          ┌─────────────┐                 │
         │          │  CREATING   │                 │
         │          │(offer enviado)│                │
         │          └──────┬──────┘                 │
         │                 │ Paciente envía answer │
         │                 ▼                       │
         │          ┌─────────────┐                 │
         └─────────►│   ACTIVE    │─────────────────┘
                    │ (P2P ready) │  Peer se desconecta
                    └─────────────┘
```

### 4.2 Estados de la Llamada (HistorialLlamadas)

```
┌─────────┐     ┌─────────┐     ┌─────────┐
│ RINGING │────►│ ACTIVE  │────►│  ENDED  │
└────┬────┘     └─────────┘     └─────────┘
     │
     │ (45s timeout)
     ▼
┌─────────┐
│ MISSED  │
└─────────┘
     │
     │ (paciente rechaza)
     ▼
┌─────────┐
│DECLINED │
└─────────┘
```

---

## 5. Códigos de Error WebSocket

### 5.1 Close Reasons

| Código | Razón | Descripción |
|--------|-------|-------------|
| 1000 | NORMAL | Cierre normal |
| 1001 | GOING_AWAY | Cliente se va (navegación, app cerrada) |
| 1006 | ABNORMAL | Cierre anormal (sin close frame) |
| 1008 | POLICY_VIOLATION | Violación de política (JWT inválido) |

### 5.2 Errores del Servidor

```json
// Room no encontrado
{
  "type": "error",
  "code": "ROOM_NOT_FOUND",
  "message": "Call session does not exist"
}

// Usuario no autorizado
{
  "type": "error",
  "code": "UNAUTHORIZED",
  "message": "User is not a participant of this call"
}

// Room llena (más de 2 peers)
{
  "type": "error",
  "code": "ROOM_FULL",
  "message": "Call already has 2 participants"
}

// Estado inválido
{
  "type": "error",
  "code": "INVALID_STATE",
  "message": "Cannot perform action in current state"
}
```

---

## 6. Consideraciones de Seguridad

### 6.1 Autenticación

- El JWT debe validarse en CADA conexión WebSocket
- El `userId` del JWT debe coincidir con `doctorId` o `patientId` de la cita
- Rechazar conexiones no autorizadas inmediatamente con close code 1008

### 6.2 Rate Limiting

- Máximo 1 intento de llamada por cita cada 5 minutos
- Máximo 3 llamadas `missed` por cita antes de bloquear

### 6.3 Validaciones

```
1. ¿JWT válido y no expirado?
2. ¿El userId existe en el JWT?
3. ¿El callId existe en la base de datos?
4. ¿El userId es doctorId O patientId de esta cita?
5. ¿La cita está en estado "confirmada"?
6. ¿No hay ya una llamada ACTIVE para esta cita?
```

---

## 7. Ejemplos de Mensajes Completos

### 7.1 SDP Offer Real (simplificado)

```json
{
  "type": "offer",
  "sdp": "v=0\r\no=- 1234567890123456789 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=group:BUNDLE 0 1\r\na=extmap-allow-mixed\r\na=msid-semantic: WMS stream_id\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111 63 9 0 8 13 110 126\r\nc=IN IP4 0.0.0.0\r\na=rtcp:9 IN IP4 0.0.0.0\r\na=ice-ufrag:abc123\r\na=ice-pwd:xyz789\r\na=fingerprint:sha-256 AB:CD:EF:12:34:56:78:90...\r\na=setup:actpass\r\na=mid:0\r\na=sendrecv\r\na=rtcp-mux\r\na=rtpmap:111 opus/48000/2\r\na=rtcp-fb:111 transport-cc\r\na=fmtp:111 minptime=10;useinbandfec=1\r\na=rtpmap:63 red/48000/2\r\na=fmtp:63 111/111\r\na=rtpmap:9 G722/8000\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:8 PCMA/8000\r\na=rtpmap:13 CN/8000\r\na=rtpmap:110 telephone-event/48000\r\na=rtpmap:126 telephone-event/8000\r\na=ssrc:1234567890 cname:user123\r\na=ssrc:1234567890 msid:stream_id audio_track_id\r\na=ssrc:1234567890 mslabel:stream_id\r\na=ssrc:1234567890 label:audio_track_id\r\nm=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 100 101 35 36 125 124 127\r\nc=IN IP4 0.0.0.0\r\na=rtcp:9 IN IP4 0.0.0.0\r\na=ice-ufrag:abc123\r\na=ice-pwd:xyz789\r\na=fingerprint:sha-256 AB:CD:EF:12:34:56:78:90...\r\na=setup:actpass\r\na=mid:1\r\na=sendrecv\r\na=rtcp-mux\r\na=rtcp-rsize\r\na=rtpmap:96 VP8/90000\r\na=rtcp-fb:96 goog-remb\r\na=rtcp-fb:96 transport-cc\r\na=rtcp-fb:96 ccm fir\r\na=rtcp-fb:96 nack\r\na=rtcp-fb:96 nack pli\r\na=rtpmap:97 rtx/90000\r\na=fmtp:97 apt=96\r\na=rtpmap:98 VP9/90000\r\na=rtcp-fb:98 goog-remb\r\na=rtcp-fb:98 transport-cc\r\na=rtcp-fb:98 ccm fir\r\na=rtcp-fb:98 nack\r\na=rtcp-fb:98 nack pli\r\na=fmtp:98 profile-id=0\r\na=rtpmap:99 rtx/90000\r\na=fmtp:99 apt=98\r\na=rtpmap:100 H264/90000\r\na=rtcp-fb:100 goog-remb\r\na=rtcp-fb:100 transport-cc\r\na=rtcp-fb:100 ccm fir\r\na=rtcp-fb:100 nack\r\na=rtcp-fb:100 nack pli\r\na=fmtp:100 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42001f\r\na=rtpmap:101 rtx/90000\r\na=fmtp:101 apt=100\r\na=rtpmap:35 AV1/90000\r\na=rtcp-fb:35 goog-remb\r\na=rtcp-fb:35 transport-cc\r\na=rtcp-fb:35 ccm fir\r\na=rtcp-fb:35 nack\r\na=rtcp-fb:35 nack pli\r\na=rtpmap:36 rtx/90000\r\na=fmtp:36 apt=35\r\na=rtpmap:125 red/90000\r\na=rtpmap:124 rtx/90000\r\na=fmtp:124 apt=125\r\na=rtpmap:127 ulpfec/90000\r\na=ssrc:9876543210 cname:user123\r\na=ssrc:9876543210 msid:stream_id video_track_id\r\na=ssrc:9876543210 mslabel:stream_id\r\na=ssrc:9876543210 label:video_track_id\r\na=ssrc:1357924680 cname:user123\r\na=ssrc:1357924680 msid:stream_id video_track_id\r\na=ssrc:1357924680 mslabel:stream_id\r\na=ssrc:1357924680 label:video_track_id\r\na=ssrc-group:FID 9876543210 1357924680\r\na=ssrc:2468013579 cname:user123\r\na=ssrc:2468013579 msid:stream_id video_track_id\r\na=ssrc:2468013579 mslabel:stream_id\r\na=ssrc:2468013579 label:video_track_id\r\n"
}
```

### 7.2 ICE Candidate Real

```json
{
  "type": "ice",
  "candidate": "candidate:842163049 1 udp 1677729535 192.168.1.100 54321 typ srflx raddr 0.0.0.0 rport 0 generation 0 ufrag abc123 network-id 1 network-cost 50",
  "sdpMid": "0",
  "sdpMLineIndex": 0
}
```

**Tipos de candidates**:
- `typ host`: IP local (192.168.x.x, 10.x.x.x)
- `typ srflx`: IP pública descubierta vía STUN
- `typ relay`: IP de servidor TURN (relay)

---

## 8. Notas de Implementación

### 8.1 WebRTC no es parte del servidor

El servidor **SOLO retransmite** mensajes. No procesa SDP, no negocia codecs, no maneja streams de video/audio.

```
┌─────────────────────────────────────────────────────────────┐
│                     SERVIDOR DE SEÑALIZACIÓN                │
│                                                             │
│   Peer A ──────► SessionManager ──────► Peer B            │
│                                                             │
│   Solo retransmite sin modificar:                           │
│   - offer → answer → ICE candidates                        │
│                                                             │
│   No sabe qué es SDP, no procesa video/audio               │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 Orden de mensajes es CRÍTICO

```
✅ CORRECTO:
   1. offer
   2. answer
   3. ice candidates (intercambio bidireccional)

❌ INCORRECTO:
   1. ice candidates (antes de offer/answer)
   2. answer (sin haber recibido offer)
```

### 8.3 Múltiples ICE Candidates

Una sesión WebRTC típica intercambia **10-20 ICE candidates** por peer. Cada uno es un mensaje WebSocket separado.

---

## 9. Referencias

- **RFC 4566**: SDP (Session Description Protocol)
- **RFC 5245**: ICE (Interactive Connectivity Establishment)
- **RFC 8445**: ICE bis (actualización)
- **WebRTC Spec**: https://www.w3.org/TR/webrtc/
- **Ktor WebSockets**: https://ktor.io/docs/websocket.html

---

*Especificación completa del protocolo WebSocket para señalización WebRTC*
