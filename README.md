# StreamHealth API

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Ktor-3.4.0-087CFA?logo=ktor&logoColor=white" alt="Ktor">
  <img src="https://img.shields.io/badge/JDK-21-007396?logo=openjdk&logoColor=white" alt="JDK 21">
  <img src="https://img.shields.io/badge/MongoDB-4.11.0-47A248?logo=mongodb&logoColor=white" alt="MongoDB">
  <img src="https://img.shields.io/badge/Gradle-9.1.0-02303A?logo=gradle&logoColor=white" alt="Gradle">
</p>

<p align="center">
  <b>Backend API para plataforma de telemedicina</b><br>
  <i>Proyecto universitario — Universidad del Quindío, Ingeniería Electrónica</i>
</p>

---

## 📋 Tabla de Contenidos

- [Descripción](#-descripción)
- [Stack Tecnológico](#-stack-tecnológico)
- [Arquitectura](#-arquitectura)
- [Módulos Principales](#-módulos-principales)
- [API Endpoints](#-api-endpoints)
- [Cómo Empezar](#-cómo-empezar)
  - [Prerrequisitos](#prerrequisitos)
  - [Configuración](#configuración)
  - [Ejecutar Localmente](#ejecutar-localmente)
- [Documentación API](#-documentación-api)
- [Variables de Entorno](#-variables-de-entorno)
- [Testing](#-testing)
- [Estructura del Proyecto](#-estructura-del-proyecto)
- [Notas de Desarrollo](#-notas-de-desarrollo)
- [Autores](#-autores)

---

## 🩺 Descripción

**StreamHealth (Betha)** es una API REST desarrollada en **Kotlin con Ktor** para una plataforma de telemedicina. Permite la gestión integral de consultas médicas virtuales, conectando pacientes con profesionales de la salud.

### Funcionalidades principales

- **Autenticación segura** con JWT
- **Gestión de usuarios** (pacientes y doctores)
- **Agendamiento de citas** médicas
- **Historial clínico electrónico**
- **Prescripciones médicas digitales**
- **Videollamadas** vía WebRTC (infraestructura de signaling implementada)

---

## 🛠 Stack Tecnológico

| Capa | Tecnología | Versión |
|------|-----------|---------|
| **Lenguaje** | Kotlin | 2.3.20 |
| **Framework** | Ktor | 3.4.0 |
| **JVM** | Java | 21 |
| **Base de Datos** | MongoDB | 4.11.0 (KMongo) |
| **Inyección de Dependencias** | Koin | 4.0.0 |
| **Autenticación** | JWT (jjwt) | 0.12.6 |
| **Hash de Contraseñas** | jBCrypt | 0.4 |
| **Serialización** | kotlinx-serialization-json | 1.7.3 |
| **Logging** | Logback | 1.5.21 |
| **Build Tool** | Gradle | 9.1.0 |

---

## 🏗 Arquitectura

La API sigue una arquitectura **modular por dominio** con inyección de dependencias via Koin:

```
┌─────────────────────────────────────┐
│           Ktor Server               │
│         (Netty Engine)              │
├─────────────────────────────────────┤
│  Routing  │  Security  │  Config   │
├─────────────────────────────────────┤
│  Auth  │  User  │  Doctor         │
│  Schedule  │  MedicalHistory     │
│  Prescription  │  Call (WebRTC)  │
├─────────────────────────────────────┤
│     Repositories (KMongo)           │
├─────────────────────────────────────┤
│           MongoDB                   │
└─────────────────────────────────────┘
```

Cada módulo encapsula su propio dominio:
- `Controller` → maneja HTTP requests/responses
- `Service` → lógica de negocio
- `Repository` → acceso a datos
- `Model` → data classes

---

## 📦 Módulos Principales

### 🔐 Autenticación (`/auth`)
- Registro de usuarios (pacientes y doctores)
- Login con JWT
- Validación de tokens

### 👤 Usuarios (`/user`)
- Perfil de usuario
- Actualización de datos personales

### 👨‍⚕️ Doctores (`/doctors`)
- Perfiles profesionales
- Búsqueda de especialistas
- Gestión de especialidad y disponibilidad

### 📅 Citas (`/schedule`)
- Pacientes: crear, consultar, cancelar citas
- Doctores: confirmar, completar, gestionar citas

### 📋 Historial Médico (`/medical-history`)
- Crear registros clínicos
- Consultar historial por paciente
- CRUD completo de registros

### 💊 Prescripciones (`/prescriptions`)
- Emitir recetas médicas
- Consultar prescripciones por paciente
- Gestión de medicamentos e indicaciones

### 📹 Videollamadas (`/schedule/*/call`)
- Iniciar llamada desde una cita
- Unirse, finalizar o rechazar llamada
- WebRTC signaling infrastructure (REST + WebSocket)

---

## 🔌 API Endpoints

Todos los endpoints (excepto auth) requieren header:
```
Authorization: Bearer <jwt_token>
```

Base URL: `/api/v1`

### Autenticación
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/auth/login` | Iniciar sesión |
| `POST` | `/auth/register` | Registrar nuevo usuario |

### Usuarios
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/user/me` | Perfil del usuario actual |
| `GET` | `/user/{userId}` | Obtener usuario por ID |
| `PUT` | `/user/me` | Actualizar perfil |

### Doctores
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/doctors` | Listar todos los doctores |
| `GET` | `/doctors/{idNumber}` | Perfil de doctor |
| `POST` | `/doctors` | Crear perfil de doctor |
| `PUT` | `/doctors` | Actualizar perfil de doctor |

### Citas
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/schedule/patient` | Crear cita (paciente) |
| `GET` | `/schedule/patient/{idNumber}` | Mis citas (paciente) |
| `PUT` | `/schedule/patient/{appointmentId}` | Actualizar cita (paciente) |
| `DELETE` | `/schedule/patient/{appointmentId}` | Eliminar cita (paciente) |
| `GET` | `/schedule/doctor/{idNumber}` | Citas del doctor |
| `PUT` | `/schedule/doctor/{appointmentId}` | Actualizar estado (doctor) |

### Historial Médico
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/medical-history` | Crear registro |
| `GET` | `/medical-history/patient/{patientId}` | Historial por paciente |
| `GET` | `/medical-history/{historyId}` | Obtener registro |
| `PUT` | `/medical-history/{historyId}` | Actualizar registro |
| `DELETE` | `/medical-history/{historyId}` | Eliminar registro |

### Prescripciones
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/prescriptions` | Crear prescripción |
| `GET` | `/prescriptions/patient/{patientId}` | Prescripciones por paciente |
| `GET` | `/prescriptions/{prescriptionId}` | Obtener prescripción |
| `PUT` | `/prescriptions/{prescriptionId}` | Actualizar prescripción |
| `DELETE` | `/prescriptions/{prescriptionId}` | Eliminar prescripción |

### Videollamadas
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/schedule/{appointmentId}/call` | Iniciar llamada |
| `POST` | `/schedule/calls/{callId}/join` | Unirse a llamada |
| `POST` | `/schedule/calls/{callId}/end` | Finalizar llamada |
| `POST` | `/schedule/calls/{callId}/decline` | Rechazar llamada |
| `GET` | `/schedule/{appointmentId}/calls` | Historial de llamadas |

---

## 🚀 Cómo Empezar

### Prerrequisitos

- **JDK 21** o superior
- **MongoDB** ejecutándose localmente (o URI de MongoDB Atlas)
- (Opcional) **Gradle** — el wrapper está incluido

### Configuración

1. **Clonar el repositorio:**
```bash
git clone <repo-url>
cd StreamHealt-api/streamhealth
```

2. **Configurar MongoDB:**

Asegurate de tener MongoDB corriendo localmente:
```bash
mongod --dbpath /ruta/a/tu/db
```

O configurá una URI remota en `src/main/resources/application.yaml`.

3. **Configurar variables de entorno (opcional pero recomendado para producción):**

```bash
export JWT_SECRET="tu-super-secreto-minimo-256-bits"
export MONGODB_URI="mongodb://localhost:27017"
export APP_PORT=8080
```

### Ejecutar Localmente

**Con Gradle:**
```bash
./gradlew run
```

**O construir el fat JAR:**
```bash
./gradlew build
java -jar build/libs/streamhealth-1.0.0-SNAPSHOT.jar
```

**Verificar que está corriendo:**
```bash
curl http://localhost:8080/
```

---

## 📖 Documentación API

La API expone documentación interactiva via Swagger UI:

- **Swagger UI:** `http://localhost:8080/swagger`
- **OpenAPI Spec:** `src/main/resources/openapi/documentation.yaml`

---

## 🔧 Variables de Entorno

| Variable | Descripción | Default |
|----------|-------------|---------|
| `APP_PORT` | Puerto del servidor | `8080` |
| `APP_HOST` | Host del servidor | `0.0.0.0` |
| `JWT_SECRET` | Secreto para firmar JWT | *(hardcoded en dev)* |
| `JWT_EXPIRATION` | Duración del token en ms | `86400000` (24h) |
| `MONGODB_URI` | URI de conexión a MongoDB | `mongodb://localhost:27017` |

> ⚠️ **IMPORTANTE:** Cambiá el `JWT_SECRET` por una variable de entorno en producción. El valor por defecto del repo es solo para desarrollo.

---

## 🧪 Testing

El proyecto usa **Kotlin Test** + **Ktor Test Host**.

```bash
# Ejecutar todos los tests
./gradlew test
```

Tests actuales:
- `ServerTest.kt` — Verifica que el servidor levanta
- `CallApiTest.kt` — Tests unitarios del módulo de llamadas
- `CallWebSocketTest.kt` — Tests del signaling WebSocket

> 💡 **Nota:** La cobertura de tests es mínima actualmente. Es un excelente punto de partida para contribuciones.

---

## 📁 Estructura del Proyecto

```
streamhealth/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── wrapper/
├── src/
│   ├── main/
│   │   ├── kotlin/com/betha/
│   │   │   ├── main.kt                 # Entry point
│   │   │   ├── Routing.kt              # Configuración de rutas
│   │   │   ├── Security.kt             # JWT verifier
│   │   │   ├── Serialization.kt        # JSON config
│   │   │   ├── auth/                   # Login & Register
│   │   │   ├── user/                   # Gestión de usuarios
│   │   │   ├── doctor/                 # Perfiles de doctores
│   │   │   ├── schedule/               # Citas médicas
│   │   │   ├── medicalHistory/         # Historial clínico
│   │   │   ├── prescription/           # Prescripciones
│   │   │   ├── call/                   # Video calls (WebRTC)
│   │   │   └── common/                 # Utilidades, config, seguridad
│   │   └── resources/
│   │       ├── application.yaml        # Config principal
│   │       └── openapi/
│   │           └── documentation.yaml  # Spec OpenAPI
│   └── test/
│       └── kotlin/
│           ├── ServerTest.kt
│           ├── CallApiTest.kt
│           └── CallWebSocketTest.kt
└── docs/
    └── feat-videollamada/              # Docs del feature de videollamadas
        ├── PRD.md
        ├── IMPLEMENTATION_GUIDE.md
        └── WEBSOCKET_PROTOCOL.md
```

---

## 📝 Notas de Desarrollo

### Videollamadas WebRTC
La infraestructura de signaling está implementada pero **el endpoint WebSocket no está cableado en `Routing.kt`**. Para activar las videollamadas en tiempo real, seguí la guía en:

📄 `docs/feat-videollamada/IMPLEMENTATION_GUIDE.md`

### CORS
El servidor tiene CORS habilitado para `anyHost()`. Revisá esto antes de deployar a producción.

### Código no utilizado
Hay paquetes duplicados sin uso (`recetas/`, `historialMedico/`) que no están conectados a Koin ni al routing. Podés ignorarlos o eliminarlos.

---

## 👥 Autores

**Proyecto universitario** desarrollado para el curso de **Laboratorio 5 — Aplicaciones Móviles** de la **Universidad del Quindío**, programa de **Ingeniería Electrónica**.

---

<p align="center">
  <i>Hecho con ❤️ y Kotlin</i>
</p>
