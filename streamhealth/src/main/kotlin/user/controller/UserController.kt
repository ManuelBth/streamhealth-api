package com.betha.user.controller

import com.betha.auth.service.AuthService
import com.betha.user.dto.UpdateUserRequest
import com.betha.user.service.UserService
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*

/**
 * User controller with profile routes
 * 
 * Endpoints:
 * - GET /api/v1/user/me - Get current user's profile (requires JWT auth)
 * - GET /api/v1/user/{userId} - Get specific user profile (requires JWT auth)
 * - PUT /api/v1/user/me - Update own profile (only own profile)
 * 
 * @see UserResponse for response structure
 * @see UpdateUserRequest for update fields
 */
fun Routing.userController(
    userService: UserService,
    authService: AuthService
) {
    route("/api/v1/user") {
        /**
         * Get current user profile
         * 
         * Retrieves the profile of the authenticated user based on JWT token
         * 
         * @header Authorization Bearer JWT token
         * @return UserResponse with user profile data
         */
        get("/me") {
            try {
                val authHeader = call.request.headers["Authorization"]
                    ?: throw IllegalArgumentException("Token requerido")
                
                val token = authHeader.removePrefix("Bearer ")
                val userInfo = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")
                
                // Use idNumber from token to get profile
                val user = userService.getUserByIdNumber(userInfo.id)
                    ?: throw IllegalArgumentException("Usuario no encontrado")
                
                call.respond(user)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.Unauthorized,
                    message = mapOf("error" to (e.message ?: "Error de autenticación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * Get user profile by ID
         * 
         * Retrieves a specific user's profile by their ID
         * 
         * @param userId Path parameter - The unique identifier of the user
         * @header Authorization Bearer JWT token
         * @return UserResponse with user profile data
         */
        get("/{userId}") {
            try {
                val authHeader = call.request.headers["Authorization"]
                    ?: throw IllegalArgumentException("Token requerido")
                
                val token = authHeader.removePrefix("Bearer ")
                val userInfo = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")
                
                val userId = call.parameters["userId"]
                    ?: throw IllegalArgumentException("ID de usuario requerido")
                
                val user = userService.getUserByIdNumber(userId)
                    ?: throw IllegalArgumentException("Usuario no encontrado")
                
                call.respond(user)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.Unauthorized,
                    message = mapOf("error" to (e.message ?: "Error de autenticación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * Update user profile
         * 
         * Updates the authenticated user's own profile
         * Users can only update their own profile
         * 
         * @param UpdateUserRequest Optional fields: nombres, apellidos, sexo, residencia
         * @header Authorization Bearer JWT token
         * @return UserResponse with updated profile data
         */
        put("/me") {
            try {
                val authHeader = call.request.headers["Authorization"]
                    ?: throw IllegalArgumentException("Token requerido")
                
                val token = authHeader.removePrefix("Bearer ")
                val userInfo = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")
                
                val request = call.receive<UpdateUserRequest>()
                val user = userService.updateUserByIdNumber(userInfo.id, request)
                    ?: throw IllegalArgumentException("Usuario no encontrado")
                
                call.respond(user)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.BadRequest,
                    message = mapOf("error" to (e.message ?: "Error de validación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * Update user profile by ID (for admins or specific lookups)
         */
        put("/{userId}") {
            try {
                val authHeader = call.request.headers["Authorization"]
                    ?: throw IllegalArgumentException("Token requerido")
                
                val token = authHeader.removePrefix("Bearer ")
                val userInfo = authService.validateToken(token)
                    ?: throw IllegalArgumentException("Token inválido")
                
                val targetUserId = call.parameters["userId"]
                    ?: throw IllegalArgumentException("ID de usuario requerido")
                
                if (userInfo.id != targetUserId) {
                    throw IllegalArgumentException("No puedes actualizar el perfil de otro usuario")
                }
                
                val request = call.receive<UpdateUserRequest>()
                val user = userService.updateUser(targetUserId, request)
                    ?: throw IllegalArgumentException("Usuario no encontrado")
                
                call.respond(user)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.BadRequest,
                    message = mapOf("error" to (e.message ?: "Error de validación"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }
    }
}