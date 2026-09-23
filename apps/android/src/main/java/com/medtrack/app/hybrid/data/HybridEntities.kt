package com.medtrack.app.hybrid.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hybrid_inference_requests",
    indices = [Index(value = ["ownerAccountId", "requestId"], unique = true)]
)
data class HybridInferenceRequestEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val accountId: String,
    val deviceId: String,
    val requestId: String,
    val purpose: String,
    val status: String,
    val clientVersion: String,
    val commandSchemaVersion: String,
    val contextDigest: String,
    val inputDigest: String,
    val jobId: String? = null,
    val patientId: String? = null,
    val admissionId: String? = null,
    val draftText: String,
    val contextJson: String,
    val errorCode: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "hybrid_proposals",
    indices = [Index(value = ["ownerAccountId", "proposalId"], unique = true)]
)
data class HybridProposalEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val accountId: String,
    val deviceId: String,
    val proposalId: String,
    val jobId: String?,
    val requestId: String,
    val status: String,
    val contextDigest: String,
    val payloadDigest: String,
    val bundleJson: String,
    val createdAt: Long,
    val updatedAt: Long
)
