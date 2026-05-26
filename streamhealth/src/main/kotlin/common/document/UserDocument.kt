package com.betha.common.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bson.types.ObjectId

/**
 * Enum representing biological sex
 */
enum class Sexo {
    MASCULINO,
    FEMENINO,
    OTRO
}

/**
 * Enum representing user roles in the system
 */
enum class Rol {
    PACIENTE,
    DOCTOR,
    ADMIN
}

/**
 * User document for MongoDB persistence
 * Represents a user in the StreamHealth telemedicine platform
 */
@Serializable
data class UserDocument(
    val id: String? = null, // MongoDB ObjectId as String
    val idNumber: String, // Cédula de identidad
    val passwordHash: String, // BCrypt hashed password
    val nombres: String,
    val apellidos: String,
    val edad: Int,
    val sexo: Sexo,
    val residencia: String? = null, // Dirección de residencia
    val rol: Rol,
    val login: Boolean = false, // Indicates if user is currently authenticated
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns the full name of the user
     */
    fun fullName(): String = "$nombres $apellidos"

    /**
     * Check if user is a doctor
     */
    fun isDoctor(): Boolean = rol == Rol.DOCTOR

    /**
     * Check if user is a patient
     */
    fun isPatient(): Boolean = rol == Rol.PACIENTE

    /**
     * Check if user is an admin
     */
    fun isAdmin(): Boolean = rol == Rol.ADMIN

    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromDocument(document: Document): UserDocument = json.decodeFromString(document.toJson())
    }
}