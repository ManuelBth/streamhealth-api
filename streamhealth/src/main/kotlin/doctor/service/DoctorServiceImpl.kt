package com.betha.doctor.service

import com.betha.common.document.Rol
import com.betha.common.document.UserDocument
import com.betha.doctor.document.DoctorDocument
import com.betha.doctor.dto.CreateDoctorProfileRequest
import com.betha.doctor.dto.DoctorResponse
import com.betha.doctor.dto.UpdateDoctorProfileRequest
import com.betha.doctor.dto.toDoctorResponse
import com.betha.doctor.repository.DoctorRepository
import com.betha.user.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Doctor service implementation
 */
class DoctorServiceImpl(
    private val doctorRepository: DoctorRepository,
    private val userRepository: UserRepository
) : DoctorService {

    companion object {
        private val log = LoggerFactory.getLogger(DoctorServiceImpl::class.java)
    }

    override suspend fun getAllDoctors(): List<DoctorResponse> = withContext(Dispatchers.IO) {
        log.info("Getting all doctors")

        val doctors = doctorRepository.findAll()
        doctors.mapNotNull { doctor ->
            val user = userRepository.findByIdNumber(doctor.userId)
            user?.let {
                doctor.toDoctorResponse(
                    idNumber = it.idNumber,
                    nombres = it.nombres,
                    apellidos = it.apellidos,
                    edad = it.edad,
                    sexo = it.sexo.name,
                    residencia = it.residencia
                )
            }
        }
    }

    override suspend fun getDoctorByUserId(idNumber: String): DoctorResponse? = withContext(Dispatchers.IO) {
        log.info("Getting doctor by idNumber: {}", idNumber)

        val doctor = doctorRepository.findByUserId(idNumber) ?: return@withContext null
        val user = userRepository.findByIdNumber(doctor.userId) ?: return@withContext null

        doctor.toDoctorResponse(
            idNumber = user.idNumber,
            nombres = user.nombres,
            apellidos = user.apellidos,
            edad = user.edad,
            sexo = user.sexo.name,
            residencia = user.residencia
        )
    }

    override suspend fun createDoctorProfile(idNumber: String, request: CreateDoctorProfileRequest): DoctorResponse? = withContext(Dispatchers.IO) {
        log.info("Creating doctor profile for idNumber: {}", idNumber)

        // Find existing user by idNumber
        val user = userRepository.findByIdNumber(idNumber)
            ?: throw IllegalArgumentException("Usuario no encontrado")

        // Validate user is a doctor
        if (user.rol != Rol.DOCTOR) {
            throw IllegalArgumentException("Solo los doctores pueden crear un perfil profesional")
        }

        // Check if doctor profile already exists
        val existingDoctor = doctorRepository.findByUserId(idNumber)
        if (existingDoctor != null) {
            throw IllegalArgumentException("El perfil profesional ya existe. Usa PUT para actualizar.")
        }

        // Create doctor document
        val doctor = DoctorDocument(
            userId = idNumber,
            titulo = request.titulo,
            universidad = request.universidad,
            especialidades = request.especialidades,
            doctorados = request.doctorados,
            licencia = request.licencia,
            telefono = request.telefono,
            direccion = request.direccion
        )

        // Save in repository
        val savedDoctor = doctorRepository.save(doctor)

        savedDoctor.toDoctorResponse(
            idNumber = user.idNumber,
            nombres = user.nombres,
            apellidos = user.apellidos,
            edad = user.edad,
            sexo = user.sexo.name,
            residencia = user.residencia
        )
    }

    override suspend fun updateDoctorProfile(idNumber: String, request: UpdateDoctorProfileRequest): DoctorResponse? = withContext(Dispatchers.IO) {
        log.info("Updating doctor profile for idNumber: {}", idNumber)

        // Find existing user
        val user = userRepository.findByIdNumber(idNumber)
            ?: throw IllegalArgumentException("Usuario no encontrado")

        // Validate user is a doctor
        if (user.rol != Rol.DOCTOR) {
            throw IllegalArgumentException("Solo los doctores pueden actualizar un perfil profesional")
        }

        // Find existing doctor profile
        val existingDoctor = doctorRepository.findByUserId(idNumber)
            ?: throw IllegalArgumentException("No existe un perfil profesional. Usa POST para crear uno.")

        // Update doctor profile (partial update)
        val updatedDoctor = existingDoctor.copy(
            titulo = request.titulo ?: existingDoctor.titulo,
            universidad = request.universidad ?: existingDoctor.universidad,
            especialidades = request.especialidades ?: existingDoctor.especialidades,
            doctorados = request.doctorados ?: existingDoctor.doctorados,
            licencia = request.licencia ?: existingDoctor.licencia,
            telefono = request.telefono ?: existingDoctor.telefono,
            direccion = request.direccion ?: existingDoctor.direccion,
            updatedAt = System.currentTimeMillis()
        )

        // Update in repository
        val result = doctorRepository.updateByUserId(idNumber, updatedDoctor)
            ?: throw IllegalArgumentException("Error al actualizar el perfil profesional")

        result.toDoctorResponse(
            idNumber = user.idNumber,
            nombres = user.nombres,
            apellidos = user.apellidos,
            edad = user.edad,
            sexo = user.sexo.name,
            residencia = user.residencia
        )
    }
}