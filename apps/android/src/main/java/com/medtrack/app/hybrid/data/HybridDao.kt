package com.medtrack.app.hybrid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HybridDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRequest(row: HybridInferenceRequestEntity)

    @Update
    suspend fun updateRequest(row: HybridInferenceRequestEntity)

    @Query(
        """
        SELECT * FROM hybrid_inference_requests
        WHERE ownerAccountId = :ownerAccountId AND requestId = :requestId
        LIMIT 1
        """
    )
    suspend fun requestByClientId(ownerAccountId: String, requestId: String): HybridInferenceRequestEntity?

    @Query(
        """
        SELECT * FROM hybrid_inference_requests
        WHERE ownerAccountId = :ownerAccountId AND status IN ('QUEUED', 'SUBMITTED')
        ORDER BY createdAt ASC
        """
    )
    suspend fun pendingRequests(ownerAccountId: String): List<HybridInferenceRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProposal(row: HybridProposalEntity)

    @Query(
        """
        SELECT * FROM hybrid_proposals
        WHERE ownerAccountId = :ownerAccountId AND status = 'PENDING_REVIEW'
        ORDER BY createdAt DESC
        """
    )
    suspend fun pendingProposals(ownerAccountId: String): List<HybridProposalEntity>

    @Query("SELECT * FROM hybrid_proposals WHERE id = :id LIMIT 1")
    suspend fun getProposal(id: String): HybridProposalEntity?

    @Query(
        """
        DELETE FROM hybrid_proposals
        WHERE ownerAccountId = :ownerAccountId AND status IN ('DISCARDED', 'PENDING_REVIEW')
        """
    )
    suspend fun clearOpenProposals(ownerAccountId: String)
}
