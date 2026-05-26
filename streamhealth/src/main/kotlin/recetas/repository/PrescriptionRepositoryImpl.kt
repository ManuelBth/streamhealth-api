package com.betha.recetas.repository

import com.betha.common.config.ConfigLoader
import com.betha.recetas.document.PrescriptionDocument
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.conversions.Bson

class PrescriptionRepositoryImpl : PrescriptionRepository {

    private val database = MongoClients
        .create(ConfigLoader.get().mongodb.uri)
        .getDatabase("streamhealth")

    private val collection: MongoCollection<Document>
        get() = database.getCollection("prescription", Document::class.java)

    override suspend fun create(document: PrescriptionDocument): PrescriptionDocument = withContext(Dispatchers.IO) {
        val doc = document.toDocument()
        collection.insertOne(doc)

        val insertedId = doc.getObjectId("_id").toString()
        document.copy(_id = insertedId)
    }

    override suspend fun findById(id: String): PrescriptionDocument? = withContext(Dispatchers.IO) {
        val document = collection.find(Filters.eq("_id", id)).first()
        document?.let { PrescriptionDocument.fromDocument(it) }
    }

    override suspend fun findByPrescriptionId(prescriptionId: String): PrescriptionDocument? = withContext(Dispatchers.IO) {
        val document = collection.find(Filters.eq("prescriptionId", prescriptionId)).first()
        document?.let { PrescriptionDocument.fromDocument(it) }
    }

    override suspend fun findByPatientId(patientId: String): List<PrescriptionDocument> = withContext(Dispatchers.IO) {
        val documents = collection.find(Filters.eq("patientId", patientId)).into(mutableListOf<Document>())
        documents.map { PrescriptionDocument.fromDocument(it) }
    }

    override suspend fun findByDoctorId(doctorId: String): List<PrescriptionDocument> = withContext(Dispatchers.IO) {
        val documents = collection.find(Filters.eq("doctorId", doctorId)).into(mutableListOf<Document>())
        documents.map { PrescriptionDocument.fromDocument(it) }
    }

    override suspend fun findByMedicalHistoryId(medicalHistoryId: String): List<PrescriptionDocument> = withContext(Dispatchers.IO) {
        val documents = collection.find(Filters.eq("medicalHistoryId", medicalHistoryId)).into(mutableListOf<Document>())
        documents.map { PrescriptionDocument.fromDocument(it) }
    }

    override suspend fun update(prescriptionId: String, updates: Map<String, Any>): PrescriptionDocument? = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) {
            return@withContext findByPrescriptionId(prescriptionId)
        }

        val updateOps = mutableListOf<Bson>()
        updates.forEach { (key, value) ->
            updateOps.add(Updates.set(key, value))
        }

        val result = collection.updateOne(
            Filters.eq("prescriptionId", prescriptionId),
            Updates.combine(updateOps),
            UpdateOptions().upsert(false)
        )

        if (result.modifiedCount > 0) {
            findByPrescriptionId(prescriptionId)
        } else {
            null
        }
    }

    override suspend fun delete(prescriptionId: String): Boolean = withContext(Dispatchers.IO) {
        val result = collection.deleteOne(Filters.eq("prescriptionId", prescriptionId))
        result.deletedCount > 0
    }
}