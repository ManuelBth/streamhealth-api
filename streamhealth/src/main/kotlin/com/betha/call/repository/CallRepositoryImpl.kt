package com.betha.call.repository

import com.betha.call.document.CallEstado
import com.betha.call.document.HistorialLlamadas
import com.betha.common.config.ConfigLoader
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document

/**
 * Call repository implementation using KMongo
 */
class CallRepositoryImpl : CallRepository {

    private val database = MongoClients
        .create(ConfigLoader.get().mongodb.uri)
        .getDatabase("streamhealth")

    private val collection: MongoCollection<Document>
        get() = database.getCollection("historial_llamadas", Document::class.java)

    init {
        collection.createIndex(Indexes.ascending("pacienteId"))
        collection.createIndex(Indexes.ascending("doctorId"))
    }

    override suspend fun save(llamada: HistorialLlamadas): HistorialLlamadas = withContext(Dispatchers.IO) {
        val document = llamada.toDocument()
        collection.insertOne(document)

        val insertedId = document.getObjectId("_id").toString()
        llamada.copy(id = insertedId)
    }

    override suspend fun findById(id: String): HistorialLlamadas? = withContext(Dispatchers.IO) {
        val document = collection.find(Filters.eq("_id", id)).first()
        document?.let { HistorialLlamadas.fromDocument(it) }
    }

    override suspend fun findByAppointmentId(appointmentId: String): List<HistorialLlamadas> = withContext(Dispatchers.IO) {
        val documents = collection.find(Filters.eq("appointmentId", appointmentId)).into(mutableListOf<Document>())
        documents.map { HistorialLlamadas.fromDocument(it) }
    }

    override suspend fun findActiveByAppointmentId(appointmentId: String): HistorialLlamadas? = withContext(Dispatchers.IO) {
        val filter = Filters.and(
            Filters.eq("appointmentId", appointmentId),
            Filters.or(
                Filters.eq("estado", CallEstado.INICIADA.name),
                Filters.eq("estado", CallEstado.EN_CURSO.name)
            )
        )
        val document = collection.find(filter).first()
        document?.let { HistorialLlamadas.fromDocument(it) }
    }

    override suspend fun update(llamada: HistorialLlamadas): HistorialLlamadas? = withContext(Dispatchers.IO) {
        val id = llamada.id ?: return@withContext null

        val result = collection.updateOne(
            Filters.eq("_id", id),
            Updates.combine(
                Updates.set("appointmentId", llamada.appointmentId),
                Updates.set("doctorId", llamada.doctorId),
                Updates.set("pacienteId", llamada.pacienteId),
                Updates.set("estado", llamada.estado.name),
                Updates.set("iniciadaPor", llamada.iniciadaPor),
                Updates.set("fechaInicio", llamada.fechaInicio?.toString()),
                Updates.set("fechaFin", llamada.fechaFin?.toString()),
                Updates.set("updatedAt", System.currentTimeMillis())
            ),
            UpdateOptions().upsert(false)
        )

        if (result.modifiedCount > 0) {
            findById(id)
        } else {
            null
        }
    }

    override suspend fun findByUserId(userId: String): List<HistorialLlamadas> = withContext(Dispatchers.IO) {
        val filter = Filters.or(
            Filters.eq("doctorId", userId),
            Filters.eq("pacienteId", userId)
        )
        val documents = collection.find(filter).into(mutableListOf<Document>())
        documents.map { HistorialLlamadas.fromDocument(it) }
    }
}