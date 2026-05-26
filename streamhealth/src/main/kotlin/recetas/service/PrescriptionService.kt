package com.betha.recetas.service

import com.betha.recetas.dto.CreatePrescriptionRequest
import com.betha.recetas.dto.PrescriptionResponse
import com.betha.recetas.dto.UpdatePrescriptionRequest

interface PrescriptionService {
    suspend fun createPrescription(request: CreatePrescriptionRequest): PrescriptionResponse

    suspend fun getById(id: String): PrescriptionResponse?

    suspend fun getByPrescriptionId(prescriptionId: String): PrescriptionResponse?

    suspend fun getByPatientId(patientId: String): List<PrescriptionResponse>

    suspend fun getByDoctorId(doctorId: String): List<PrescriptionResponse>

    suspend fun getByMedicalHistoryId(medicalHistoryId: String): List<PrescriptionResponse>

    suspend fun updatePrescription(prescriptionId: String, request: UpdatePrescriptionRequest): PrescriptionResponse?

    suspend fun deletePrescription(prescriptionId: String): Boolean
}