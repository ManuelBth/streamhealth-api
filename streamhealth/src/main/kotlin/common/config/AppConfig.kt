package com.betha.common.config

import io.ktor.server.config.*
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Application configuration loaded from application.yaml
 */
data class AppConfig(
    val jwt: JwtConfig,
    val mongodb: MongoDBConfig,
    val app: AppServerConfig
)

/**
 * JWT configuration
 */
data class JwtConfig(
    val secret: String,
    val expiration: Long // in milliseconds
) {
    companion object {
        const val DEFAULT_EXPIRATION = 24 * 60 * 60 * 1000L // 24 hours in ms
    }
}

/**
 * MongoDB configuration
 */
data class MongoDBConfig(
    val uri: String
)

/**
 * Application server configuration
 */
data class AppServerConfig(
    val host: String,
    val port: Int
)

/**
 * Loads configuration from application.yaml
 */
object ConfigLoader {
    private var config: AppConfig? = null

    /**
     * Load configuration from the application.yaml file
     */
    fun load(): AppConfig {
        config?.let { return it }

        val appConfig = try {
            loadFromYaml()
        } catch (e: Exception) {
            // Fallback to environment variables or defaults
            loadFromEnvOrDefaults()
        }

        config = appConfig
        return appConfig
    }

    private fun loadFromYaml(): AppConfig {
        // Try to load from KTor's config system
        val config3 = ApplicationConfig("application.yaml")

        val jwtSecret = config3.propertyOrNull("jwt.secret")?.getString()
            ?: "default-secret-change-in-production"
        val jwtExpiration = config3.propertyOrNull("jwt.expiration")?.getString()?.toLongOrNull()
            ?: JwtConfig.DEFAULT_EXPIRATION

        val mongodbUri = config3.propertyOrNull("mongodb.uri")?.getString()
            ?: "mongodb://localhost:27017"

        val host = config3.propertyOrNull("app.host")?.getString()
            ?: "0.0.0.0"
        val port = config3.propertyOrNull("app.port")?.getString()?.toIntOrNull()
            ?: 8080

        return AppConfig(
            jwt = JwtConfig(jwtSecret, jwtExpiration),
            mongodb = MongoDBConfig(mongodbUri),
            app = AppServerConfig(host, port)
        )
    }

    private fun loadFromEnvOrDefaults(): AppConfig {
        // Default configuration using environment variables
        return AppConfig(
            jwt = JwtConfig(
                secret = System.getenv("JWT_SECRET") ?: "default-secret-change-in-production",
                expiration = System.getenv("JWT_EXPIRATION")?.toLongOrNull() ?: JwtConfig.DEFAULT_EXPIRATION
            ),
            mongodb = MongoDBConfig(
                uri = System.getenv("MONGODB_URI") ?: "mongodb://localhost:27017"
            ),
            app = AppServerConfig(
                host = System.getenv("APP_HOST") ?: "0.0.0.0",
                port = System.getenv("APP_PORT")?.toIntOrNull() ?: 8080
            )
        )
    }

    /**
     * Get the current configuration instance
     */
    fun get(): AppConfig = config ?: load()
}