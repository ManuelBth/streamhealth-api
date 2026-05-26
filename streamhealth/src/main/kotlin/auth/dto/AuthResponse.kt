package com.betha.auth.dto

import com.betha.common.document.Rol
import kotlinx.serialization.Serializable

/**
 * User info in auth response (PRD section 5.1)
 */
@Serializable
data class UserInfo(
    val id: String,
    val nombres: String,
    val apellidos: String,
    val rol: Rol
)

/**
 * Auth response DTO containing token and user (PRD section 5.1)
 */
@Serializable
data class AuthResponse(
    val token: String,
    val user: UserInfo
)