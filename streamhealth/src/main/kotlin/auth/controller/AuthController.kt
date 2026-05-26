package com.betha.auth.controller

import com.betha.auth.dto.AuthResponse
import com.betha.auth.dto.LoginRequest
import com.betha.auth.dto.RegisterRequest
import com.betha.auth.service.AuthService
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Auth controller with login and register routes
 * 
 * Endpoints:
 * - POST /api/v1/auth/login - User login with credentials
 * - POST /api/v1/auth/register - User registration
 * 
 * @see LoginRequest for login credentials structure
 * @see RegisterRequest for registration fields
 * @see AuthResponse for response structure
 */
fun Routing.authController(authService: AuthService) {
    route("/api/v1/auth") {
        /**
         * User login endpoint
         * 
         * Authenticate user and return JWT token
         * 
         * @param LoginRequest id - User identifier/username
         * @param LoginRequest password - User password
         * @return AuthResponse with JWT token and user info
         */
        post("/login") {
            try {
                val request = call.receive<LoginRequest>()
                val response = authService.login(request)
                call.respond(response)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.Unauthorized,
                    message = mapOf("error" to (e.message ?: "Credenciales inválidas"))
                )
            } catch (e: Exception) {
                call.respond(
                    status = io.ktor.http.HttpStatusCode.InternalServerError,
                    message = mapOf("error" to (e.message ?: "Error interno"))
                )
            }
        }

        /**
         * User registration endpoint
         * 
         * Register a new user in the system
         * 
         * @param RegisterRequest User registration data (idNumber, nombres, apellidos, edad, sexo, residencia, password, rol)
         * @return AuthResponse with JWT token and user info
         */
        post("/register") {
            try {
                val request = call.receive<RegisterRequest>()
                val response = authService.register(request)
                call.respond(response)
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