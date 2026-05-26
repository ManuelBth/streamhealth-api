package com.betha.historialMedico.repository

import com.betha.common.config.ConfigLoader
import com.betha.historialMedico.document.MedicalHistoryDocument
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.conversions.Bson

/**
 * Medical history repository implementation using KMongo
 */
class MedicalHistoryRepositoryImpl : MedicalHistoryRepository {

    private val database = MongoClients
        .create(ConfigLoader.get().mongodb.uri)
        .getDatabase("streamhealth")

    private val collection: MongoCollection<Document>
        get() = database.getCollection("medicalHistory", Document::class.java)

    override suspend fun create(document: MedicalHistoryDocument): MedicalHistoryDocument = withContext(Dispatchers.IO) {
        val doc = document.toDocument()
        collection.insertOne(doc)

        val insertedId = doc.getObjectId("_id").toString()
        document.copy(id = insertedId)
    }

    override suspend fun findById(id: String): MedicalHistoryDocument? = withContext(Dispatchers.IO) {
        val document = collection.find(Filters.eq("_id", id)).first()
        document?.let { MedicalHistoryDocument.fromDocument(it) }
    }

    override suspend fun findByMedicalHistoryId(medicalHistoryId: String): MedicalHistoryDocument? = withContext(Dispatchers.IO) {
        val document = collection.find(Filters.eq("medicalHistoryId", medicalHistoryId)).first()
        document?.let { MedicalHistoryDocument.fromDocument(it) }
    }

    override suspend fun findByPatientId(patientId: String): List<MedicalHistoryDocument> = withContext(Dispatchers.IO) {
        val documents = collection.find(Filters.eq("patientId", patientId)).into(mutableListOf<Document>())
        documents.map { MedicalHistoryDocument.fromDocument(it) }
    }

    override suspend fun findByDoctorId(doctorId: String): List<MedicalHistoryDocument> = withContext(Dispatchers.IO) {
        val documents = collection.find(Filters.eq("doctorId", doctorId)).into(mutableListOf<Document>())
        documents.map { MedicalHistoryDocument.fromDocument(it) }
    }

    override suspend fun update(medicalHistoryId: String, updates: Map<String, Any>): MedicalHistoryDocument? = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) {
            return@withContext findByMedicalHistoryId(medicalHistoryId)
        }

        val updateOps = mutableListOf<Bson>()
        updates.forEach { (key, value) ->
            updateOps.add(Updates.set(key, value))
        }
        updateOps.add(Updates.set("updatedAt", System.currentTimeMillis()))

        val result = collection.updateOne(
            Filters.eq("medicalHistoryId", medicalHistoryId),
            Updates.combine(updateOps),
            UpdateOptions().upsert(false)
        )

        if (result.modifiedCount > 0) {
            findByMedicalHistoryId(medicalHistoryId)
        } else {
            null
        }
    }

    override suspend fun delete(medicalHistoryId: String): Boolean = withContext(Dispatchers.IO) {
        val result = collection.deleteOne(Filters.eq("medicalHistoryId", medicalHistoryId))
        result.deletedCount > 0
    }
}