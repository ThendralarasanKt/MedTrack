package com.medtrack.app.data.care.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medtrack.app.data.care.entity.*

@Dao
interface CareTherapyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedicationDefinition(row: CareMedicationDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedicationHistoryItem(row: CareMedicationHistoryItemEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedicationOrder(row: CareMedicationOrderEntity)

    @Update
    suspend fun updateMedicationOrder(row: CareMedicationOrderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedicationOrderEvent(row: CareMedicationOrderEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedicationAdministration(row: CareMedicationAdministrationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProcedureOrder(row: CareProcedureOrderEntity)

    @Update
    suspend fun updateProcedureOrder(row: CareProcedureOrderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProcedureEvent(row: CareProcedureEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvestigationOrder(row: CareInvestigationOrderEntity)

    @Update
    suspend fun updateInvestigationOrder(row: CareInvestigationOrderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSpecimen(row: CareSpecimenEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrderSpecimenLink(row: CareOrderSpecimenLinkEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImagingStudy(row: CareImagingStudyEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(row: CareDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDiagnosticReport(row: CareDiagnosticReportEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReportOrderLink(row: CareReportOrderLinkEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReportDocumentLink(row: CareReportDocumentLinkEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReportReview(row: CareReportReviewEntity)

    @Query("SELECT * FROM care_medication_definitions WHERE id = :id LIMIT 1")
    suspend fun getMedicationDefinition(id: String): CareMedicationDefinitionEntity?

    @Query("SELECT * FROM care_medication_orders WHERE id = :id LIMIT 1")
    suspend fun getMedicationOrder(id: String): CareMedicationOrderEntity?

    @Query(
        """
        SELECT * FROM care_medication_orders
        WHERE admissionId = :admissionId
        ORDER BY createdAt ASC
        """
    )
    suspend fun ordersForAdmission(admissionId: String): List<CareMedicationOrderEntity>

    @Query("SELECT * FROM care_medication_order_events WHERE orderId = :orderId ORDER BY createdAt ASC")
    suspend fun orderEvents(orderId: String): List<CareMedicationOrderEventEntity>

    @Query("SELECT * FROM care_medication_administrations WHERE orderId = :orderId")
    suspend fun administrationsForOrder(orderId: String): List<CareMedicationAdministrationEntity>

    @Query("SELECT * FROM care_procedure_orders WHERE id = :id LIMIT 1")
    suspend fun getProcedureOrder(id: String): CareProcedureOrderEntity?

    @Query("SELECT * FROM care_procedure_events WHERE orderId = :orderId")
    suspend fun procedureEventsForOrder(orderId: String): List<CareProcedureEventEntity>

    @Query("SELECT * FROM care_investigation_orders WHERE id = :id LIMIT 1")
    suspend fun getInvestigationOrder(id: String): CareInvestigationOrderEntity?

    @Query(
        """
        SELECT * FROM care_investigation_orders
        WHERE admissionId = :admissionId
        ORDER BY createdAt DESC
        """
    )
    suspend fun investigationOrdersForAdmission(admissionId: String): List<CareInvestigationOrderEntity>

    @Query("SELECT * FROM care_diagnostic_reports WHERE id = :id LIMIT 1")
    suspend fun getDiagnosticReport(id: String): CareDiagnosticReportEntity?

    @Query(
        """
        SELECT * FROM care_diagnostic_reports
        WHERE admissionId = :admissionId
        ORDER BY createdAt DESC
        """
    )
    suspend fun diagnosticReportsForAdmission(admissionId: String): List<CareDiagnosticReportEntity>

    @Query("SELECT * FROM care_report_document_links WHERE reportId = :reportId ORDER BY sequence ASC")
    suspend fun documentsForReport(reportId: String): List<CareReportDocumentLinkEntity>

    @Query("SELECT * FROM care_report_reviews WHERE reportId = :reportId ORDER BY createdAt ASC")
    suspend fun reviewsForReport(reportId: String): List<CareReportReviewEntity>

    @Query("SELECT * FROM care_documents WHERE id = :id LIMIT 1")
    suspend fun getDocument(id: String): CareDocumentEntity?
}
