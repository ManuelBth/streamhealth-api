
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.betha"
version = "1.0.0-SNAPSHOT"

application {
    mainClass.set("com.betha.MainKt")
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    // KTor
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation("io.ktor:ktor-server-cio:3.0.2")
    implementation(ktorLibs.server.openapi)
    implementation(ktorLibs.server.routingOpenapi)
    implementation(ktorLibs.server.cors)
    implementation("io.ktor:ktor-server-websockets:3.4.0")
    // Swagger UI
    implementation("io.ktor:ktor-server-swagger:3.0.2")

    // Logging
    implementation(libs.logback.classic)

    // MongoDB - KMongo
    implementation(libs.kmongo.coroutine)
    implementation(libs.kmongo.id)
    // Also need the core for KMongo
    implementation("org.litote.kmongo:kmongo-core:4.11.0")

    // DI - Koin
    implementation(libs.koin.ktor)

    // JWT
    implementation(libs.jjwt.api)
    implementation(libs.jjwt.impl)
    implementation(libs.jjwt.jackson)

    // Password Hashing - BCrypt
    implementation(libs.bcrypt)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // YAML Config
    implementation(libs.snakeyaml)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
