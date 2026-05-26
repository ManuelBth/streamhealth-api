package com.betha.historialMedico.service

import com.betha.common.document.Rol
import com.betha.historialMedico.document.MedicalHistoryDocument
import com.betha.historialMedico.dto.CreateMedicalHistoryRequest
import com.betha.historialMedico.dto.MedicalHistoryResponse
import com.betha.historialMedico.dto.UpdateMedicalHistoryRequest
import com.betha.historialMedico.dto.toMedicalHistoryResponse
import com.betha.historialMedico.repository.MedicalHistoryRepository
import com.betha.user.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import kotlin.random.Random

/**
 * Medical history service implementation
 */
class MedicalHistoryServiceImpl(
    private val medicalHistoryRepository: MedicalHistoryRepository,
    private val userRepository: UserRepository
) : MedicalHistoryService {

    companion object {
        private val log = LoggerFactory.getLogger(MedicalHistoryServiceImpl::class.java)
    }

    private fun generateMedicalHistoryId(): String {
        val date = LocalDate.now().format(ofPattern("yyyyMMdd"))
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "MH-$date-$random"
    }

    override suspend fun createMedicalHistory(request: CreateMedicalHistoryRequest): MedicalHistoryResponse = withContext(Dispatchers.IO) {
        log.info("Creating medical history for patient: {} with doctor: {}", request.patientId, request.doctorId)

        val doctor = userRepository.findByIdNumber(request.doctorId)
            ?: throw IllegalArgumentException("Doctor no encontrado")
        if (doctor.rol != Rol.DOCTOR) {
            throw IllegalArgumentException("El usuario no es un doctor válido")
        }

        val patient = userRepository.findByIdNumber(request.patientId)
            ?: throw IllegalArgumentException("Paciente no encontrado")
        if (patient.rol != Rol.PACIENTE) {
            throw IllegalArgumentException("El usuario no es un paciente válido")
        }

        val now = Instant.now().toEpochMilli()
        val document = MedicalHistoryDocument(
            medicalHistoryId = generateMedicalHistoryId(),
            patientId = request.patientId,
            doctorId = request.doctorId,
            appointmentId = request.appointmentId,
            diagnostico = request.diagnostico,
            observaciones = request.observaciones,
            createdAt = now,
            updatedAt = now
        )

        val saved = medicalHistoryRepository.create(document)
        saved.toMedicalHistoryResponse()
    }

    override suspend fun getById(id: String): MedicalHistoryResponse? = withContext(Dispatchers.IO) {
        log.info("Getting medical history by MongoDB ID: {}", id)

        val document = medicalHistoryRepository.findById(id)
        document?.toMedicalHistoryResponse()
    }

    override suspend fun getByMedicalHistoryId(medicalHistoryId: String): MedicalHistoryResponse? = withContext(Dispatchers.IO) {
        log.info("Getting medical history by compound ID: {}", medicalHistoryId)

        val document = medicalHistoryRepository.findByMedicalHistoryId(medicalHistoryId)
        document?.toMedicalHistoryResponse()
    }

    override suspend fun getByPatientId(patientId: String): List<MedicalHistoryResponse> = withContext(Dispatchers.IO) {
        log.info("Getting medical histories for patient: {}", patientId)

        val patient = userRepository.findByIdNumber(patientId)
            ?: throw IllegalArgumentException("Paciente no encontrado")

        val documents = medicalHistoryRepository.findByPatientId(patientId)
        documents.map { it.toMedicalHistoryResponse() }
    }

    override suspend fun getByDoctorId(doctorId: String): List<MedicalHistoryResponse> = withContext(Dispatchers.IO) {
        log.info("Getting medical histories for doctor: {}", doctorId)

        val doctor = userRepository.findByIdNumber(doctorId)
            ?: throw IllegalArgumentException("Doctor no encontrado")
        if (doctor.rol != Rol.DOCTOR) {
            throw IllegalArgumentException("El usuario no es un doctor válido")
        }

        val documents = medicalHistoryRepository.findByDoctorId(doctorId)
        documents.map { it.toMedicalHistoryResponse() }
    }

    override suspend fun updateMedicalHistory(medicalHistoryId: String, request: UpdateMedicalHistoryRequest): MedicalHistoryResponse? = withContext(Dispatchers.IO) {
        log.info("Updating medical history: {}", medicalHistoryId)

        val existing = medicalHistoryRepository.findByMedicalHistoryId(medicalHistoryId)
            ?: throw IllegalArgumentException("Historial médico no encontrado")

        val updates = mutableMapOf<String, Any>()
        request.diagnostico?.let { updates["diagnostico"] = it }
        request.observaciones?.let { updates["observaciones"] = it }
        request.recetas?.let { updates["recetas"] = it }

        val result = medicalHistoryRepository.update(medicalHistoryId, updates)
            ?: throw IllegalArgumentException("Error al actualizar el historial médico")

        result.toMedicalHistoryResponse()
    }

    override suspend fun deleteMedicalHistory(medicalHistoryId: String): Boolean = withContext(Dispatchers.IO) {
        log.info("Deleting medical history: {}", medicalHistoryId)

        val existing = medicalHistoryRepository.findByMedicalHistoryId(medicalHistoryId)
            ?: throw IllegalArgumentException("Historial médico no encontrado")

        medicalHistoryRepository.delete(medicalHistoryId)
    }
}