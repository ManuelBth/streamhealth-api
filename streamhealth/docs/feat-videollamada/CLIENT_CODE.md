# Código del Cliente WebRTC

> Código de referencia para implementar el cliente WebRTC en tu app (Android, iOS o Web). Incluye ejemplos en Kotlin (Android) y JavaScript (Web).

---

## 1. Cliente Android (Kotlin)

### 1.1 Dependencias Gradle

```groovy
// build.gradle (app level)
dependencies {
    // WebRTC oficial de Google
    implementation 'org.webrtc:google-webrtc:1.0.32006'
    
    // WebSocket
    implementation 'io.ktor:ktor-client-websockets:2.3.7'
    implementation 'io.ktor:ktor-client-cio:2.3.7'
    implementation 'io.ktor:ktor-client-json:2.3.7'
    implementation 'io.ktor:ktor-client-serialization:2.3.7'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // JSON
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0'
}
```

### 1.2 WebRTC Manager

```kotlin
package com.tuempresa.videocall

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.*
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import java.util.*

class WebRTCManager(
    private val context: Context,
    private val signalingUrl: String,  // wss://api.tudominio.com/ws/call/{callId}
    private val jwtToken: String
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocketSession: DefaultClientWebSocketSession? = null
    
    // WebRTC core
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    
    // Callbacks para la UI
    var onLocalVideoTrack: ((VideoTrack) -> Unit)? = null
    var onRemoteVideoTrack: ((VideoTrack) -> Unit)? = null
    var onCallConnected: (() -> Unit)? = null
    var onCallEnded: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    // Estados
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Inicializa WebRTC y se conecta al servidor de señalización
     */
    fun initialize() {
        scope.launch {
            try {
                initPeerConnectionFactory()
                connectToSignalingServer()
            } catch (e: Exception) {
                onError?.invoke("Error initializing: ${e.message}")
            }
        }
    }
    
    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        
        PeerConnectionFactory.initialize(options)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
    }
    
    private suspend fun connectToSignalingServer() {
        val client = HttpClient {
            install(WebSockets)
        }
        
        webSocketSession = client.webSocketSession {
            url(signalingUrl)
            header("Authorization", "Bearer $jwtToken")
        }
        
        // Escuchar mensajes
        scope.launch {
            try {
                for (frame in webSocketSession!!.incoming) {
                    when (frame) {
                        is Frame.Text -> handleSignalingMessage(frame.readText())
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                println("WebSocket error: ${e.message}")
                onCallEnded?.invoke()
            }
        }
    }
    
    private suspend fun handleSignalingMessage(message: String) {
        println("Received: $message")
        
        val wsMessage = try {
            json.decodeFromString<WebSocketMessage>(message)
        } catch (e: Exception) {
            println("Error parsing message: $e")
            return
        }
        
        when (wsMessage.type) {
            "state" -> handleStateMessage(wsMessage)
            "peer_joined" -> handlePeerJoined()
            "peer_left" -> handlePeerLeft()
            "offer" -> handleOffer(wsMessage)
            "answer" -> handleAnswer(wsMessage)
            "ice" -> handleIceCandidate(wsMessage)
            "call_ended" -> handleCallEnded()
        }
    }
    
    /**
     * El DOCTOR inicia la llamada (envía Offer)
     */
    fun startCall() {
        scope.launch {
            try {
                createPeerConnection()
                
                // Crear oferta
                val constraints = MediaConstraints()
                val offer = peerConnection!!.createOffer(constraints)
                peerConnection!!.setLocalDescription(offer)
                
                // Enviar al servidor
                sendMessage(OfferMessage(sdp = offer.description))
            } catch (e: Exception) {
                onError?.invoke("Error starting call: ${e.message}")
            }
        }
    }
    
    /**
     * El PACIENTE responde la llamada
     */
    fun answerCall() {
        // El paciente solo necesita estar conectado al WS
        // El offer llegará vía handleOffer()
    }
    
    private fun handleStateMessage(message: WebSocketMessage) {
        println("Session state: ${(message as StateMessage).status}")
    }
    
    private fun handlePeerJoined() {
        println("Peer joined!")
    }
    
    private fun handlePeerLeft() {
        println("Peer left!")
        onCallEnded?.invoke()
    }
    
    private suspend fun handleOffer(message: WebSocketMessage) {
        val offerMsg = message as OfferMessage
        
        createPeerConnection()
        
        val offer = SessionDescription(SessionDescription.Type.OFFER, offerMsg.sdp)
        peerConnection!!.setRemoteDescription(offer)
        
        // Crear answer
        val constraints = MediaConstraints()
        val answer = peerConnection!!.createAnswer(constraints)
        peerConnection!!.setLocalDescription(answer)
        
        // Enviar answer
        sendMessage(AnswerMessage(sdp = answer.description))
    }
    
    private suspend fun handleAnswer(message: WebSocketMessage) {
        val answerMsg = message as AnswerMessage
        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerMsg.sdp)
        peerConnection!!.setRemoteDescription(answer)
        onCallConnected?.invoke()
    }
    
    private suspend fun handleIceCandidate(message: WebSocketMessage) {
        val iceMsg = message as IceMessage
        val candidate = IceCandidate(
            iceMsg.sdpMid ?: "",
            iceMsg.sdpMLineIndex ?: 0,
            iceMsg.candidate
        )
        peerConnection!!.addIceCandidate(candidate)
    }
    
    private fun handleCallEnded() {
        println("Call ended by server")
        onCallEnded?.invoke()
        cleanup()
    }
    
    private fun createPeerConnection() {
        val constraints = MediaConstraints()
        
        peerConnection = peerConnectionFactory!!.createPeerConnection(
            iceServers,
            constraints,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            println("ICE Connected!")
                            onCallConnected?.invoke()
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.FAILED -> {
                            onCallEnded?.invoke()
                        }
                        else -> {}
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        scope.launch {
                            sendMessage(
                                IceMessage(
                                    candidate = it.sdp,
                                    sdpMid = it.sdpMid,
                                    sdpMLineIndex = it.sdpMLineIndex
                                )
                            )
                        }
                    }
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate?>?) {}
                override fun onAddStream(stream: MediaStream?) {
                    stream?.videoTracks?.firstOrNull()?.let { track ->
                        remoteVideoTrack = track
                        scope.launch(Dispatchers.Main) {
                            onRemoteVideoTrack?.invoke(track)
                        }
                    }
                }
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(dataChannel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream?>?) {
                    receiver?.track()?.let { track ->
                        when (track.kind()) {
                            "video" -> {
                                remoteVideoTrack = track as VideoTrack
                                scope.launch(Dispatchers.Main) {
                                    onRemoteVideoTrack?.invoke(track)
                                }
                            }
                            "audio" -> {
                                remoteAudioTrack = track as AudioTrack
                            }
                        }
                    }
                }
            }
        )
        
        // Agregar stream local (cámara + micrófono)
        addLocalStream()
    }
    
    private fun addLocalStream() {
        // Video
        val videoSource = peerConnectionFactory!!.createVideoSource(false)
        val videoCapturer = createCameraCapturer()
        videoCapturer?.startCapture(1280, 720, 30)
        
        localVideoTrack = peerConnectionFactory!!.createVideoTrack("video0", videoSource)
        val localStream = peerConnectionFactory!!.createLocalMediaStream("stream0")
        localStream.addTrack(localVideoTrack)
        
        // Audio
        val audioSource = peerConnectionFactory!!.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("audio0", audioSource)
        localStream.addTrack(localAudioTrack)
        
        peerConnection!!.addStream(localStream)
        
        onLocalVideoTrack?.invoke(localVideoTrack!!)
    }
    
    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        
        // Preferir cámara frontal
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        // Fallback a cualquier cámara
        for (deviceName in deviceNames) {
            return enumerator.createCapturer(deviceName, null)
        }
        
        return null
    }
    
    private suspend fun sendMessage(message: WebSocketMessage) {
        try {
            val jsonStr = json.encodeToString(WebSocketMessage.serializer(), message)
            webSocketSession?.send(Frame.Text(jsonStr))
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
        }
    }
    
    /**
     * Colgar la llamada
     */
    fun hangUp() {
        scope.launch {
            sendMessage(HangupMessage())
            cleanup()
        }
    }
    
    /**
     * Limpiar recursos
     */
    fun cleanup() {
        scope.launch {
            localVideoTrack?.dispose()
            localAudioTrack?.dispose()
            remoteVideoTrack?.dispose()
            remoteAudioTrack?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnectionFactory?.dispose()
            webSocketSession?.close()
            scope.cancel()
        }
    }
    
    // Modelos de mensajes (copiar del servidor)
    @kotlinx.serialization.Serializable
    sealed class WebSocketMessage {
        abstract val type: String
    }
    
    @kotlinx.serialization.Serializable
    data class StateMessage(
        override val type: String = "state",
        val status: String
    ) : WebSocketMessage()
    
    @kotlinx.serialization.Serializable
    data class OfferMessage(
        override val type: String = "offer",
        val sdp: String
    ) : WebSocketMessage()
    
    @kotlinx.serialization.Serializable
    data class AnswerMessage(
        override val type: String = "answer",
        val sdp: String
    ) : WebSocketMessage()
    
    @kotlinx.serialization.Serializable
    data class IceMessage(
        override val type: String = "ice",
        val candidate: String,
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = null
    ) : WebSocketMessage()
    
    @kotlinx.serialization.Serializable
    data class HangupMessage(
        override val type: String = "hangup"
    ) : WebSocketMessage()
}
```

### 1.3 Activity de Videollamada (Doctor)

```kotlin
package com.tuempresa.videocall

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class DoctorCallActivity : AppCompatActivity() {
    
    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var btnEndCall: Button
    private var webRTCManager: WebRTCManager? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_call)
        
        localView = findViewById(R.id.localView)
        remoteView = findViewById(R.id.remoteView)
        btnEndCall = findViewById(R.id.btnEndCall)
        
        // Obtener datos del intent
        val callId = intent.getStringExtra("callId") ?: return
        val token = intent.getStringExtra("token") ?: return
        val wsUrl = "wss://api.tudominio.com/ws/call/$callId"
        
        webRTCManager = WebRTCManager(this, wsUrl, token).apply {
            onLocalVideoTrack = { track ->
                localView.init(null, null)
                track.addSink(localView)
            }
            onRemoteVideoTrack = { track ->
                remoteView.init(null, null)
                track.addSink(remoteView)
            }
            onCallConnected = {
                // Mostrar UI de "Conectado"
                runOnUiThread {
                    // Actualizar UI
                }
            }
            onCallEnded = {
                finish()
            }
            onError = { error ->
                runOnUiThread {
                    // Mostrar error
                    finish()
                }
            }
            initialize()
        }
        
        // El doctor inicia la llamada automáticamente al conectarse
        webRTCManager?.startCall()
        
        btnEndCall.setOnClickListener {
            webRTCManager?.hangUp()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        webRTCManager?.cleanup()
        localView.release()
        remoteView.release()
    }
}
```

### 1.4 Activity de Llamada Entrante (Paciente)

```kotlin
package com.tuempresa.videocall

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class IncomingCallActivity : AppCompatActivity() {
    
    private lateinit var tvCallerName: TextView
    private lateinit var btnAccept: Button
    private lateinit var btnDecline: Button
    private var webRTCManager: WebRTCManager? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)
        
        tvCallerName = findViewById(R.id.tvCallerName)
        btnAccept = findViewById(R.id.btnAccept)
        btnDecline = findViewById(R.id.btnDecline)
        
        // Datos del intent (vienen del push notification)
        val callId = intent.getStringExtra("callId") ?: return
        val token = intent.getStringExtra("token") ?: return
        val doctorName = intent.getStringExtra("doctorName") ?: "Doctor"
        val wsUrl = "wss://api.tudominio.com/ws/call/$callId"
        
        tvCallerName.text = "Dr. $doctorName está llamando..."
        
        // Conectar al WebSocket pero NO iniciar WebRTC todavía
        webRTCManager = WebRTCManager(this, wsUrl, token).apply {
            initialize()
        }
        
        btnAccept.setOnClickListener {
            // Contestar: iniciar WebRTC
            acceptCall()
        }
        
        btnDecline.setOnClickListener {
            // Rechazar llamada
            declineCall(callId, token)
            finish()
        }
    }
    
    private fun acceptCall() {
        // Ocultar UI de incoming, mostrar UI de videollamada
        setContentView(R.layout.activity_patient_call)
        
        val localView = findViewById<SurfaceViewRenderer>(R.id.localView)
        val remoteView = findViewById<SurfaceViewRenderer>(R.id.remoteView)
        val btnEndCall = findViewById<Button>(R.id.btnEndCall)
        
        webRTCManager?.apply {
            onLocalVideoTrack = { track ->
                localView.init(null, null)
                track.addSink(localView)
            }
            onRemoteVideoTrack = { track ->
                remoteView.init(null, null)
                track.addSink(remoteView)
            }
            onCallConnected = {
                runOnUiThread {
                    // Conectado
                }
            }
            onCallEnded = {
                finish()
            }
        }
        
        // El paciente "contesta" al estar listo para recibir el offer
        webRTCManager?.answerCall()
        
        btnEndCall.setOnClickListener {
            webRTCManager?.hangUp()
        }
    }
    
    private fun declineCall(callId: String, token: String) {
        // Hacer POST a /api/calls/{callId}/decline
        // Implementar con tu cliente HTTP
    }
    
    override fun onDestroy() {
        super.onDestroy()
        webRTCManager?.cleanup()
    }
}
```

---

## 2. Cliente Web (JavaScript)

### 2.1 WebRTC Client

```javascript
// webrtc-client.js

class WebRTCClient {
    constructor(signalingUrl, token) {
        this.signalingUrl = signalingUrl;
        this.token = token;
        this.ws = null;
        this.peerConnection = null;
        this.localStream = null;
        this.remoteStream = null;
        
        // Callbacks
        this.onLocalStream = null;
        this.onRemoteStream = null;
        this.onCallConnected = null;
        this.onCallEnded = null;
        this.onError = null;
    }
    
    async initialize() {
        try {
            await this.connectSignaling();
        } catch (e) {
            console.error("Error initializing:", e);
            this.onError?.(e.message);
        }
    }
    
    async connectSignaling() {
        return new Promise((resolve, reject) => {
            this.ws = new WebSocket(this.signalingUrl);
            this.ws.headers = { 'Authorization': `Bearer ${this.token}` };
            
            this.ws.onopen = () => {
                console.log("Connected to signaling server");
                resolve();
            };
            
            this.ws.onerror = (e) => {
                reject(new Error("WebSocket error"));
            };
            
            this.ws.onmessage = (event) => {
                this.handleMessage(JSON.parse(event.data));
            };
            
            this.ws.onclose = () => {
                console.log("WebSocket closed");
                this.onCallEnded?.();
            };
        });
    }
    
    async handleMessage(message) {
        console.log("Received:", message);
        
        switch (message.type) {
            case 'state':
                console.log("State:", message.status);
                break;
            case 'peer_joined':
                console.log("Peer joined:", message.userId);
                break;
            case 'peer_left':
                console.log("Peer left");
                this.onCallEnded?.();
                break;
            case 'offer':
                await this.handleOffer(message);
                break;
            case 'answer':
                await this.handleAnswer(message);
                break;
            case 'ice':
                await this.handleIceCandidate(message);
                break;
            case 'call_ended':
                this.onCallEnded?.();
                break;
        }
    }
    
    async startCall() {
        try {
            await this.createPeerConnection();
            
            const offer = await this.peerConnection.createOffer();
            await this.peerConnection.setLocalDescription(offer);
            
            this.sendMessage({ type: 'offer', sdp: offer.sdp });
        } catch (e) {
            console.error("Error starting call:", e);
            this.onError?.(e.message);
        }
    }
    
    async answerCall() {
        // El paciente espera el offer en handleOffer
    }
    
    async handleOffer(message) {
        try {
            await this.createPeerConnection();
            
            await this.peerConnection.setRemoteDescription(
                new RTCSessionDescription({ type: 'offer', sdp: message.sdp })
            );
            
            const answer = await this.peerConnection.createAnswer();
            await this.peerConnection.setLocalDescription(answer);
            
            this.sendMessage({ type: 'answer', sdp: answer.sdp });
        } catch (e) {
            console.error("Error handling offer:", e);
        }
    }
    
    async handleAnswer(message) {
        try {
            await this.peerConnection.setRemoteDescription(
                new RTCSessionDescription({ type: 'answer', sdp: message.sdp })
            );
            this.onCallConnected?.();
        } catch (e) {
            console.error("Error handling answer:", e);
        }
    }
    
    async handleIceCandidate(message) {
        try {
            await this.peerConnection.addIceCandidate(
                new RTCIceCandidate({
                    candidate: message.candidate,
                    sdpMid: message.sdpMid,
                    sdpMLineIndex: message.sdpMLineIndex
                })
            );
        } catch (e) {
            console.error("Error adding ICE:", e);
        }
    }
    
    async createPeerConnection() {
        const config = {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' }
            ]
        };
        
        this.peerConnection = new RTCPeerConnection(config);
        
        // Obtener stream local
        this.localStream = await navigator.mediaDevices.getUserMedia({
            video: true,
            audio: true
        });
        
        this.localStream.getTracks().forEach(track => {
            this.peerConnection.addTrack(track, this.localStream);
        });
        
        this.onLocalStream?.(this.localStream);
        
        // Eventos
        this.peerConnection.onicecandidate = (event) => {
            if (event.candidate) {
                this.sendMessage({
                    type: 'ice',
                    candidate: event.candidate.candidate,
                    sdpMid: event.candidate.sdpMid,
                    sdpMLineIndex: event.candidate.sdpMLineIndex
                });
            }
        };
        
        this.peerConnection.ontrack = (event) => {
            this.remoteStream = event.streams[0];
            this.onRemoteStream?.(this.remoteStream);
        };
        
        this.peerConnection.onconnectionstatechange = () => {
            console.log("Connection state:", this.peerConnection.connectionState);
            if (this.peerConnection.connectionState === 'connected') {
                this.onCallConnected?.();
            }
        };
    }
    
    sendMessage(message) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message));
        }
    }
    
    hangUp() {
        this.sendMessage({ type: 'hangup' });
        this.cleanup();
    }
    
    cleanup() {
        this.localStream?.getTracks().forEach(track => track.stop());
        this.peerConnection?.close();
        this.ws?.close();
    }
}

// Uso (Doctor)
async function startDoctorCall(callId, token) {
    const client = new WebRTCClient(
        `wss://api.tudominio.com/ws/call/${callId}`,
        token
    );
    
    client.onLocalStream = (stream) => {
        document.getElementById('localVideo').srcObject = stream;
    };
    
    client.onRemoteStream = (stream) => {
        document.getElementById('remoteVideo').srcObject = stream;
    };
    
    client.onCallConnected = () => {
        console.log("Call connected!");
    };
    
    client.onCallEnded = () => {
        console.log("Call ended");
        window.location.href = '/dashboard';
    };
    
    await client.initialize();
    client.startCall(); // El doctor inicia
}

// Uso (Paciente)
async function answerPatientCall(callId, token) {
    const client = new WebRTCClient(
        `wss://api.tudominio.com/ws/call/${callId}`,
        token
    );
    
    client.onLocalStream = (stream) => {
        document.getElementById('localVideo').srcObject = stream;
    };
    
    client.onRemoteStream = (stream) => {
        document.getElementById('remoteVideo').srcObject = stream;
    };
    
    await client.initialize();
    // El paciente solo se conecta, el offer llegará del doctor
}
```

### 2.2 HTML para Videollamada

```html
<!DOCTYPE html>
<html>
<head>
    <title>Videollamada Médica</title>
    <style>
        .video-container {
            display: flex;
            gap: 10px;
        }
        video {
            width: 400px;
            height: 300px;
            background: #000;
        }
        #localVideo {
            transform: scaleX(-1); /* Espejo para cámara frontal */
        }
        .controls {
            margin-top: 20px;
        }
        .btn {
            padding: 10px 20px;
            margin: 5px;
            border: none;
            border-radius: 5px;
            cursor: pointer;
        }
        .btn-end {
            background: #ff4444;
            color: white;
        }
    </style>
</head>
<body>
    <h2>Videollamada Médica</h2>
    
    <div class="video-container">
        <div>
            <p>Tú</p>
            <video id="localVideo" autoplay muted playsinline></video>
        </div>
        <div>
            <p>Doctor/Paciente</p>
            <video id="remoteVideo" autoplay playsinline></video>
        </div>
    </div>
    
    <div class="controls">
        <button class="btn btn-end" onclick="hangUp()">Colgar</button>
    </div>
    
    <script src="webrtc-client.js"></script>
    <script>
        // Obtener callId y token de la URL o de tu app
        const urlParams = new URLSearchParams(window.location.search);
        const callId = urlParams.get('callId');
        const token = localStorage.getItem('jwt_token');
        
        let webrtcClient;
        
        async function init() {
            webrtcClient = new WebRTCClient(
                `wss://api.tudominio.com/ws/call/${callId}`,
                token
            );
            
            webrtcClient.onLocalStream = (stream) => {
                document.getElementById('localVideo').srcObject = stream;
            };
            
            webrtcClient.onRemoteStream = (stream) => {
                document.getElementById('remoteVideo').srcObject = stream;
            };
            
            webrtcClient.onCallConnected = () => {
                console.log("Connected!");
            };
            
            webrtcClient.onCallEnded = () => {
                alert("Llamada finalizada");
                window.location.href = '/';
            };
            
            await webrtcClient.initialize();
            
            // Si es doctor, iniciar la llamada
            const isDoctor = /* obtener de tu app */ true;
            if (isDoctor) {
                webrtcClient.startCall();
            }
        }
        
        function hangUp() {
            webrtcClient?.hangUp();
        }
        
        init();
    </script>
</body>
</html>
```

---

## 3. Push Notifications (Firebase Cloud Messaging)

### 3.1 Enviar desde el Servidor

```kotlin
// En tu CallService, cuando el doctor inicia la llamada:
fun enviarNotificacionLlamada(patientId: String, doctorName: String, callId: String) {
    // Obtener FCM token del paciente desde tu DB
    val fcmToken = obtenerFcmToken(patientId)
    
    val message = Message.builder()
        .setToken(fcmToken)
        .setNotification(
            Notification.builder()
                .setTitle("Llamada entrante")
                .setBody("Dr. $doctorName está llamándote")
                .build()
        )
        .setData(mapOf(
            "type" to "incoming_call",
            "callId" to callId,
            "doctorName" to doctorName
        ))
        .setAndroidConfig(
            AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(
                    AndroidNotification.builder()
                        .setChannelId("incoming_calls")
                        .setSound("ringtone")
                        .setPriority(AndroidNotification.Priority.HIGH)
                        .build()
                )
                .build()
        )
        .setApnsConfig(
            ApnsConfig.builder()
                .setAps(
                    Aps.builder()
                        .setSound("ringtone.caf")
                        .setBadge(1)
                        .build()
                )
                .build()
        )
        .build()
    
    FirebaseMessaging.getInstance().send(message)
}
```

### 3.2 Recibir en Android

```kotlin
class CallFirebaseService : FirebaseMessagingService() {
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        
        if (data["type"] == "incoming_call") {
            val callId = data["callId"] ?: return
            val doctorName = data["doctorName"] ?: "Doctor"
            
            // Mostrar pantalla de llamada entrante (incluso con app cerrada)
            val intent = Intent(this, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("callId", callId)
                putExtra("doctorName", doctorName)
                putExtra("token", obtenerTokenActual())
            }
            startActivity(intent)
            
            // También mostrar notificación
            showIncomingCallNotification(callId, doctorName)
        }
    }
    
    private fun showIncomingCallNotification(callId: String, doctorName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Crear canal si es necesario (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "incoming_calls",
                "Llamadas entrantes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, "incoming_calls")
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Llamada entrante")
            .setContentText("Dr. $doctorName está llamándote")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setOngoing(true)
            .build()
        
        notificationManager.notify(callId.hashCode(), notification)
    }
}
```

---

## 4. Checklist de Implementación Cliente

### Android
- [ ] Agregar permisos en `AndroidManifest.xml` (CÁMARA, MICRÓFONO, INTERNET)
- [ ] Implementar `WebRTCManager`
- [ ] Crear `DoctorCallActivity` y `IncomingCallActivity`
- [ ] Configurar Firebase Cloud Messaging
- [ ] Crear canal de notificación para llamadas
- [ ] Manejar deep links para abrir pantalla de llamada
- [ ] Probar en diferentes dispositivos y versiones de Android

### iOS
- [ ] Usar GoogleWebRTC framework
- [ ] Implementar Push Notifications con APNS
- [ ] Crear pantalla de llamada entrante (CallKit para integración nativa)
- [ ] Manejar permisos de cámara y micrófono

### Web
- [ ] Implementar `WebRTCClient` en JavaScript
- [ ] Solicitar permisos de cámara/micrófono
- [ ] Manejar diferentes navegadores (Chrome, Safari, Firefox)
- [ ] Responsive design para móvil y desktop

---

*Código listo para integrar en tus apps cliente*
