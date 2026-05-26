package com.betha.recetas.service

import com.betha.common.document.Rol
import com.betha.historialMedico.repository.MedicalHistoryRepository
import com.betha.recetas.document.PrescriptionDocument
import com.betha.recetas.dto.CreatePrescriptionRequest
import com.betha.recetas.dto.PrescriptionResponse
import com.betha.recetas.dto.UpdatePrescriptionRequest
import com.betha.recetas.dto.toPrescriptionResponse
import com.betha.recetas.repository.PrescriptionRepository
import com.betha.user.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import kotlin.random.Random

class PrescriptionServiceImpl(
    private val prescriptionRepository: PrescriptionRepository,
    private val userRepository: UserRepository,
    private val medicalHistoryRepository: MedicalHistoryRepository
) : PrescriptionService {

    companion object {
        private val log = LoggerFactory.getLogger(PrescriptionServiceImpl::class.java)
    }

    private fun generatePrescriptionId(): String {
        val date = LocalDate.now().format(ofPattern("yyyyMMdd"))
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "RX-$date-$random"
    }

    override suspend fun createPrescription(request: CreatePrescriptionRequest): PrescriptionResponse = withContext(Dispatchers.IO) {
        log.info("Creating prescription for patient: {} with doctor: {}", request.patientId, request.doctorId)

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

        val medicalHistory = medicalHistoryRepository.findByMedicalHistoryId(request.medicalHistoryId)
            ?: throw IllegalArgumentException("Historial médico no encontrado")

        val document = PrescriptionDocument(
            prescriptionId = generatePrescriptionId(),
            patientId = request.patientId,
            doctorId = request.doctorId,
            medicalHistoryId = request.medicalHistoryId,
            medicamento = request.medicamento,
            cantidad = request.cantidad,
            frecuencia = request.frecuencia,
            recomendaciones = request.recomendaciones,
            fechaValidacion = request.fechaValidacion,
            createdAt = System.currentTimeMillis().toString()
        )

        val saved = prescriptionRepository.create(document)
        saved.toPrescriptionResponse()
    }

    override suspend fun getById(id: String): PrescriptionResponse? = withContext(Dispatchers.IO) {
        log.info("Getting prescription by MongoDB ID: {}", id)

        val document = prescriptionRepository.findById(id)
        document?.toPrescriptionResponse()
    }

    override suspend fun getByPrescriptionId(prescriptionId: String): PrescriptionResponse? = withContext(Dispatchers.IO) {
        log.info("Getting prescription by compound ID: {}", prescriptionId)

        val document = prescriptionRepository.findByPrescriptionId(prescriptionId)
        document?.toPrescriptionResponse()
    }

    override suspend fun getByPatientId(patientId: String): List<PrescriptionResponse> = withContext(Dispatchers.IO) {
        log.info("Getting prescriptions for patient: {}", patientId)

        val patient = userRepository.findByIdNumber(patientId)
            ?: throw IllegalArgumentException("Paciente no encontrado")

        val documents = prescriptionRepository.findByPatientId(patientId)
        documents.map { it.toPrescriptionResponse() }
    }

    override suspend fun getByDoctorId(doctorId: String): List<PrescriptionResponse> = withContext(Dispatchers.IO) {
        log.info("Getting prescriptions for doctor: {}", doctorId)

        val doctor = userRepository.findByIdNumber(doctorId)
            ?: throw IllegalArgumentException("Doctor no encontrado")
        if (doctor.rol != Rol.DOCTOR) {
            throw IllegalArgumentException("El usuario no es un doctor válido")
        }

        val documents = prescriptionRepository.findByDoctorId(doctorId)
        documents.map { it.toPrescriptionResponse() }
    }

    override suspend fun getByMedicalHistoryId(medicalHistoryId: String): List<PrescriptionResponse> = withContext(Dispatchers.IO) {
        log.info("Getting prescriptions for medical history: {}", medicalHistoryId)

        val documents = prescriptionRepository.findByMedicalHistoryId(medicalHistoryId)
        documents.map { it.toPrescriptionResponse() }
    }

    override suspend fun updatePrescription(prescriptionId: String, request: UpdatePrescriptionRequest): PrescriptionResponse? = withContext(Dispatchers.IO) {
        log.info("Updating prescription: {}", prescriptionId)

        val existing = prescriptionRepository.findByPrescriptionId(prescriptionId)
            ?: throw IllegalArgumentException("Receta no encontrada")

        val updates = mutableMapOf<String, Any>()
        request.medicamento?.let { updates["medicamento"] = it }
        request.cantidad?.let { updates["cantidad"] = it }
        request.frecuencia?.let { updates["frecuencia"] = it }
        request.recomendaciones?.let { updates["recomendaciones"] = it }
        request.fechaValidacion?.let { updates["fechaValidacion"] = it.toString() }

        val result = prescriptionRepository.update(prescriptionId, updates)
            ?: throw IllegalArgumentException("Error al actualizar la receta")

        result.toPrescriptionResponse()
    }

    override suspend fun deletePrescription(prescriptionId: String): Boolean = withContext(Dispatchers.IO) {
        log.info("Deleting prescription: {}", prescriptionId)

        val existing = prescriptionRepository.findByPrescriptionId(prescriptionId)
            ?: throw IllegalArgumentException("Receta no encontrada")

        prescriptionRepository.delete(prescriptionId)
    }
}