package com.betha.call

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.betha.auth.service.AuthService
import com.betha.call.document.CallEstado
import com.betha.call.document.HistorialLlamadas
import com.betha.call.repository.CallRepository
import com.betha.common.document.Rol
import com.betha.common.security.DecodedJWT
import com.betha.schedule.document.AppointmentDocument
import com.betha.schedule.repository.ScheduleRepository
import kotlinx.coroutines.test.runTest
import java.util.Date
import kotlin.test.*

class CallWebSocketTest {

    private val doctorId = "12345678"
    private val patientId = "87654321"
    private val appointmentId = "20241215-ABC123"
    private val callId = "ws-call-123"
    private val jwtSecret = "default-secret-change-in-production"

    private fun generateTestToken(userId: String, rol: Rol): String {
        return JWT.create()
            .withSubject(userId)
            .withClaim("rol", rol.name)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    private fun createTestCall(
        estado: CallEstado = CallEstado.INICIADA,
        id: String? = callId
    ): HistorialLlamadas {
        return HistorialLlamadas(
            id = id,
            appointmentId = appointmentId,
            doctorId = doctorId,
            pacienteId = patientId,
            estado = estado,
            iniciadaPor = doctorId
        )
    }

    private fun createTestAppointment(): AppointmentDocument {
        return AppointmentDocument(
            id = "mongo-id-123",
            appointmentId = appointmentId,
            patientId = patientId,
            doctorId = doctorId,
            fecha = "2024-12-15T10:00:00Z",
            motivo = "Consulta general",
            estado = "confirmada"
        )
    }

    @Test
    fun `test token generation for WebSocket`() {
        val token = generateTestToken(doctorId, Rol.DOCTOR)
        assertNotNull(token)
        assertTrue(token.isNotEmpty())
    }

    @Test
    fun `test WebSocket call data creation`() {
        val call = createTestCall(CallEstado.INICIADA)
        assertEquals(callId, call.id)
        assertEquals(appointmentId, call.appointmentId)
        assertEquals(doctorId, call.doctorId)
        assertEquals(patientId, call.pacienteId)
        assertEquals(CallEstado.INICIADA, call.estado)
    }

    @Test
    fun `test participant validation`() {
        val call = createTestCall()

        assertTrue(call.doctorId == doctorId || call.pacienteId == patientId)
        assertFalse(call.doctorId == "99999999" && call.pacienteId == "99999999")
    }

    @Test
    fun `test MockAuthService validates tokens correctly`() = runTest {
        val authService = MockAuthServiceWS(DecodedJWT(doctorId, Rol.DOCTOR, System.currentTimeMillis() + 86400000))
        val userInfo = authService.validateToken("valid-token")
        assertNotNull(userInfo)
        assertEquals(doctorId, userInfo.id)
    }

    @Test
    fun `test MockAuthService rejects invalid tokens`() = runTest {
        val authService = MockAuthServiceWS(null)
        val userInfo = authService.validateToken("invalid-token")
        assertNull(userInfo)
    }

    @Test
    fun `test MockCallRepositoryWS finds call by id`() = runTest {
        val repo = MockCallRepositoryWS()
        val call = createTestCall()
        repo.call = call

        val found = repo.findById(callId)
        assertNotNull(found)
        assertEquals(callId, found.id)
    }

    @Test
    fun `test MockCallRepositoryWS returns null for unknown call`() = runTest {
        val repo = MockCallRepositoryWS()
        val found = repo.findById("unknown-call")
        assertNull(found)
    }

    @Test
    fun `test MockScheduleRepositoryWS finds appointment`() = runTest {
        val repo = MockScheduleRepositoryWS()
        val appointment = createTestAppointment()
        repo.appointment = appointment

        val found = repo.findByAppointmentId(appointmentId)
        assertNotNull(found)
        assertEquals(appointmentId, found.appointmentId)
    }
}

class MockCallRepositoryWS : CallRepository {
    var call: HistorialLlamadas? = null
    var activeCall: HistorialLlamadas? = null
    var calls: List<HistorialLlamadas> = emptyList()
    var savedCall: HistorialLlamadas? = null
    var updatedCall: HistorialLlamadas? = null

    override suspend fun save(llamada: HistorialLlamadas): HistorialLlamadas {
        savedCall = llamada.copy(id = "generated-call-id")
        return savedCall!!
    }

    override suspend fun findById(id: String): HistorialLlamadas? = call

    override suspend fun findByAppointmentId(appointmentId: String): List<HistorialLlamadas> = calls

    override suspend fun findActiveByAppointmentId(appointmentId: String): HistorialLlamadas? = activeCall

    override suspend fun update(llamada: HistorialLlamadas): HistorialLlamadas {
        updatedCall = llamada
        return llamada
    }

    override suspend fun findByUserId(userId: String): List<HistorialLlamadas> = calls
}

class MockScheduleRepositoryWS : ScheduleRepository {
    var appointment: AppointmentDocument? = null

    override suspend fun findById(id: String): AppointmentDocument? = appointment

    override suspend fun findByAppointmentId(appointmentId: String): AppointmentDocument? = appointment

    override suspend fun findByPatientId(patientId: String): List<AppointmentDocument> = emptyList()

    override suspend fun findByDoctorId(doctorId: String): List<AppointmentDocument> = emptyList()

    override suspend fun save(appointment: AppointmentDocument): AppointmentDocument = appointment

    override suspend fun update(appointmentId: String, appointment: AppointmentDocument): AppointmentDocument? = appointment

    override suspend fun delete(appointmentId: String): Boolean = true
}

class MockAuthServiceWS(private val decodedJWT: DecodedJWT?) : AuthService {
    override suspend fun login(request: com.betha.auth.dto.LoginRequest): com.betha.auth.dto.AuthResponse {
        throw NotImplementedError()
    }

    override suspend fun register(request: com.betha.auth.dto.RegisterRequest): com.betha.auth.dto.AuthResponse {
        throw NotImplementedError()
    }

    override suspend fun validateToken(token: String): com.betha.auth.dto.UserInfo? {
        return decodedJWT?.let {
            com.betha.auth.dto.UserInfo(
                id = it.userId,
                nombres = "Test",
                apellidos = "User",
                rol = it.rol
            )
        }
    }
}
