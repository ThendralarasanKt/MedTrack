package com.medtrack.app.data.care.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Idempotent map from a legacy Int-keyed row to care-model objects (or a deferred
 * destination until PM-007+ clinical tables exist). Source fields are preserved
 * as JSON; unknown episode boundaries stay uncertain.
 */
@Entity(
    tableName = "care_legacy_links",
    indices = [
        Index(
            value = ["ownerAccountId", "legacyTable", "legacyId", "careObjectType"],
            unique = true
        ),
        Index(value = ["carePatientId"])
    ]
)
data class CareLegacyLinkEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val legacyTable: String,
    val legacyId: Int,
    val careObjectType: String,
    val careObjectId: String? = null,
    val carePatientId: String? = null,
    val careEpisodeId: String? = null,
    val mappingStatus: String,
    val destinationHint: String? = null,
    val uncertaintyNote: String? = null,
    val sourceSnapshotJson: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)
