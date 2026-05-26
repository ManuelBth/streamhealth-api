package com.betha.auth.service

import com.betha.auth.dto.AuthResponse
import com.betha.auth.dto.LoginRequest
import com.betha.auth.dto.RegisterRequest
import com.betha.auth.dto.UserInfo
import com.betha.auth.repository.AuthRepository
import com.betha.common.document.Rol
import com.betha.common.document.UserDocument
import com.betha.common.security.JWTService
import com.betha.common.security.PasswordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Authentication service implementation
 */
class AuthServiceImpl(
    private val authRepository: AuthRepository,
    private val passwordService: PasswordService,
    private val jwtService: JWTService
) : AuthService {

    companion object {
        private val log = LoggerFactory.getLogger(AuthServiceImpl::class.java)
    }

    override suspend fun login(request: LoginRequest): AuthResponse = withContext(Dispatchers.IO) {
        log.info("Login attempt for user: {}", request.id)
        
        // Find user by idNumber (PRD login uses "id" field)
        val user = authRepository.findByIdNumber(request.id)
            ?: throw IllegalArgumentException("Credenciales inválidas")

        // Verify password
        if (!passwordService.verify(request.password, user.passwordHash)) {
            log.warn("Login failed for user: {} - invalid credentials", request.id)
            throw IllegalArgumentException("Credenciales inválidas")
        }

        // Generate JWT token
        val token = jwtService.generate(user.idNumber, user.rol)

        log.info("Login successful for user: {}, rol: {}", user.idNumber, user.rol)

        // Build response (per PRD: id, nombres, apellidos, rol)
        AuthResponse(
            token = token,
            user = UserInfo(
                id = user.idNumber,
                nombres = user.nombres,
                apellidos = user.apellidos,
                rol = user.rol
            )
        )
    }

    override suspend fun register(request: RegisterRequest): AuthResponse = withContext(Dispatchers.IO) {
        // Validate idNumber doesn't exist
        if (authRepository.existsByIdNumber(request.idNumber)) {
            throw IllegalArgumentException("El idNumber ya está registrado")
        }

        // Validate password minimum length (PRD: min 6 chars)
        if (request.password.length < 6) {
            throw IllegalArgumentException("La contraseña debe tener al menos 6 caracteres")
        }

        // Hash password
        val passwordHash = passwordService.hash(request.password)

        // Create user document (per PRD fields)
        val user = UserDocument(
            idNumber = request.idNumber,
            passwordHash = passwordHash,
            nombres = request.nombres,
            apellidos = request.apellidos,
            edad = request.edad,
            sexo = request.sexo,
            residencia = request.residencia,
            rol = request.rol,
            login = true
        )

        // Save user
        val savedUser = authRepository.save(user)

        log.info("User registered successfully: {}, rol: {}", savedUser.idNumber, savedUser.rol)

        // Generate JWT token
        val token = jwtService.generate(savedUser.idNumber, savedUser.rol)

        // Build response
        AuthResponse(
            token = token,
            user = UserInfo(
                id = savedUser.idNumber,
                nombres = savedUser.nombres,
                apellidos = savedUser.apellidos,
                rol = savedUser.rol
            )
        )
    }

    override suspend fun validateToken(token: String): UserInfo? = withContext(Dispatchers.IO) {
        val decodedJWT = jwtService.verify(token) ?: return@withContext null

        // Get user from database using idNumber from token
        val user = authRepository.findByIdNumber(decodedJWT.userId) ?: return@withContext null

        UserInfo(
            id = user.idNumber,
            nombres = user.nombres,
            apellidos = user.apellidos,
            rol = user.rol
        )
    }
}