package com.medtrack.app.data.care.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medtrack.app.data.care.entity.*

@Dao
interface CareWorkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReferral(row: CareReferralEntity)

    @Update
    suspend fun updateReferral(row: CareReferralEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReferralMilestone(row: CareReferralMilestoneEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConsultationAdvice(row: CareConsultationAdviceEntity)

    @Update
    suspend fun updateConsultationAdvice(row: CareConsultationAdviceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAdviceDecisionLink(row: CareAdviceDecisionLinkEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClinicalQuestion(row: CareClinicalQuestionEntity)

    @Update
    suspend fun updateClinicalQuestion(row: CareClinicalQuestionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuestionResponse(row: CareQuestionResponseEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommunicationEvent(row: CareCommunicationEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommunicationRecipient(row: CareCommunicationRecipientEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommunicationSubject(row: CareCommunicationSubjectEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCareTask(row: CareTaskEntity)

    @Update
    suspend fun updateCareTask(row: CareTaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTaskAssignment(row: CareTaskAssignmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTaskClinicalLink(row: CareTaskClinicalLinkEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTaskResponse(row: CareTaskResponseEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminderSchedule(row: CareReminderScheduleEntity)

    @Update
    suspend fun updateReminderSchedule(row: CareReminderScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNotificationAttempt(row: CareNotificationAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchedulingOutbox(row: CareSchedulingOutboxEntity)

    @Query("SELECT * FROM care_referrals WHERE id = :id LIMIT 1")
    suspend fun getReferral(id: String): CareReferralEntity?

    @Query("SELECT * FROM care_referral_milestones WHERE referralId = :referralId ORDER BY createdAt ASC")
    suspend fun milestonesForReferral(referralId: String): List<CareReferralMilestoneEntity>

    @Query("SELECT * FROM care_consultation_advice WHERE id = :id LIMIT 1")
    suspend fun getAdvice(id: String): CareConsultationAdviceEntity?

    @Query("SELECT * FROM care_clinical_questions WHERE id = :id LIMIT 1")
    suspend fun getQuestion(id: String): CareClinicalQuestionEntity?

    @Query("SELECT * FROM care_question_responses WHERE questionId = :questionId ORDER BY createdAt ASC")
    suspend fun responsesForQuestion(questionId: String): List<CareQuestionResponseEntity>

    @Query("SELECT * FROM care_communication_events WHERE id = :id LIMIT 1")
    suspend fun getCommunication(id: String): CareCommunicationEventEntity?

    @Query("SELECT * FROM care_communication_recipients WHERE communicationId = :communicationId")
    suspend fun recipientsForCommunication(communicationId: String): List<CareCommunicationRecipientEntity>

    @Query("SELECT * FROM care_tasks WHERE id = :id LIMIT 1")
    suspend fun getCareTask(id: String): CareTaskEntity?

    @Query(
        """
        SELECT * FROM care_tasks
        WHERE ownerAccountId = :ownerAccountId AND status IN ('PENDING', 'IN_PROGRESS')
        ORDER BY createdAt ASC
        """
    )
    suspend fun openTasksForOwner(ownerAccountId: String): List<CareTaskEntity>

    @Query("SELECT * FROM care_task_responses WHERE taskId = :taskId ORDER BY createdAt ASC")
    suspend fun responsesForTask(taskId: String): List<CareTaskResponseEntity>

    @Query("SELECT * FROM care_reminder_schedules WHERE careTaskId = :taskId ORDER BY revision ASC")
    suspend fun remindersForTask(taskId: String): List<CareReminderScheduleEntity>

    @Query("SELECT * FROM care_scheduling_outbox WHERE scheduleId = :scheduleId ORDER BY createdAt ASC")
    suspend fun outboxForSchedule(scheduleId: String): List<CareSchedulingOutboxEntity>

    @Query(
        """
        SELECT * FROM care_scheduling_outbox
        WHERE ownerAccountId = :ownerAccountId AND state = 'PENDING'
        ORDER BY createdAt ASC
        """
    )
    suspend fun pendingOutbox(ownerAccountId: String): List<CareSchedulingOutboxEntity>

    @Update
    suspend fun updateSchedulingOutbox(row: CareSchedulingOutboxEntity)

    @Query("SELECT * FROM care_reminder_schedules WHERE id = :id LIMIT 1")
    suspend fun getReminderSchedule(id: String): CareReminderScheduleEntity?

    @Query(
        """
        SELECT * FROM care_reminder_schedules
        WHERE ownerAccountId = :ownerAccountId AND state = 'ACTIVE'
        """
    )
    suspend fun activeReminders(ownerAccountId: String): List<CareReminderScheduleEntity>

    @Query(
        """
        SELECT * FROM care_hospital_episodes
        WHERE ownerAccountId = :ownerAccountId AND status = 'ACTIVE'
        ORDER BY createdAt ASC
        """
    )
    suspend fun activeEpisodes(ownerAccountId: String): List<CareHospitalEpisodeEntity>

    @Query(
        """
        SELECT * FROM care_tasks
        WHERE admissionId = :admissionId
        ORDER BY createdAt ASC
        """
    )
    suspend fun tasksForAdmission(admissionId: String): List<CareTaskEntity>

    @Query(
        """
        SELECT * FROM care_referrals
        WHERE ownerAccountId = :ownerAccountId AND status = 'OPEN'
        ORDER BY createdAt DESC
        """
    )
    suspend fun openReferrals(ownerAccountId: String): List<CareReferralEntity>
}
