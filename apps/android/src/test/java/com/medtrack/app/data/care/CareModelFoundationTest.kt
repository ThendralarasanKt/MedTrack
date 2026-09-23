package com.medtrack.app.data.care

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.data.care.command.CareCommandService
import com.medtrack.app.data.care.command.CareWorkspace
import com.medtrack.app.data.care.command.CreatePatientAndAdmissionRequest
import com.medtrack.app.data.care.command.RecordTransferRequest
import com.medtrack.app.data.care.entity.CareClinicalTeamEntity
import com.medtrack.app.data.care.entity.CareDepartmentEntity
import com.medtrack.app.data.care.entity.CareHospitalEntity
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.entity.CarePhysicalLocationEntity
import com.medtrack.app.data.care.entity.CareProfessionalRoleEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CareModelFoundationTest {
    private lateinit var db: AppDatabase
    private lateinit var commands: CareCommandService
    private lateinit var workspace: CareWorkspace

    private val ownerId = CareIds.newId()
    private val doctorId = CareIds.newId()
    private val hospitalId = CareIds.newId()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        commands = CareCommandService(db, db.careDao())
        workspace = CareWorkspace(ownerAccountId = ownerId, actorPersonId = doctorId)

        val now = System.currentTimeMillis()
        db.careDao().insertPerson(
            CarePersonEntity(
                id = doctorId,
                ownerAccountId = ownerId,
                displayName = "Dr Ankita",
                identityState = CareEnums.IdentityState.CONFIRMED.name,
                createdAt = now,
                createdBy = doctorId
            )
        )
        db.careDao().insertHospital(
            CareHospitalEntity(
                id = hospitalId,
                ownerAccountId = ownerId,
                code = "SYN",
                name = "Synthetic Hospital",
                timeZoneId = "Asia/Kolkata",
                status = CareEnums.ActiveStatus.ACTIVE.name,
                createdAt = now,
                createdBy = doctorId
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun twoWardsCanShareBedCodeTwelve() = runBlocking {
        val wardA = insertWard("WA", "Ward A")
        val wardB = insertWard("WB", "Ward B")
        val bedA = insertBed(wardA, "12", "Bed 12 A")
        val bedB = insertBed(wardB, "12", "Bed 12 B")
        assertNotEquals(bedA, bedB)
        assertNotNull(db.careDao().findLocation(hospitalId, wardA, CareEnums.LocationKind.BED.name, "12"))
        assertNotNull(db.careDao().findLocation(hospitalId, wardB, CareEnums.LocationKind.BED.name, "12"))
    }

    @Test
    fun locationChangeWithoutConsultantChange() = runBlocking {
        val dept = insertDepartment("MED", "Medicine")
        val team = insertTeam("TM1", "Medicine Team 1", dept)
        val ward = insertWard("W1", "Ward 1")
        val bed1 = insertBed(ward, "101", "Bed 101")
        val bed2 = insertBed(ward, "202", "Bed 202")

        val created = commands.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest(
                patientDisplayName = "Synthetic Patient",
                hospitalId = hospitalId,
                teamId = team
            )
        )
        val t0 = ClinicalTime.instant(1_700_000_000_000L)
        commands.recordTransfer(
            workspace,
            RecordTransferRequest(
                admissionId = created.admissionId,
                effectiveAt = t0,
                locationId = bed1,
                primaryDepartmentId = dept,
                primaryTeamId = team,
                consultantPersonId = doctorId
            )
        )
        commands.recordTransfer(
            workspace,
            RecordTransferRequest(
                admissionId = created.admissionId,
                effectiveAt = ClinicalTime.instant(1_700_000_100_000L),
                locationId = bed2
            )
        )

        val location = db.careDao().currentLocationAssignment(created.admissionId)
        val teamAssignment = db.careDao().currentPrimaryTeam(created.admissionId)
        assertEquals(bed2, location?.locationId)
        assertEquals(team, teamAssignment?.teamId)
        assertEquals(doctorId, teamAssignment?.consultantPersonId)
    }

    @Test
    fun consultantChangeWithoutBedMove() = runBlocking {
        val dept = insertDepartment("MED", "Medicine")
        val team1 = insertTeam("TM1", "Team 1", dept)
        val team2 = insertTeam("TM2", "Team 2", dept)
        val ward = insertWard("W1", "Ward 1")
        val bed = insertBed(ward, "101", "Bed 101")
        val otherDoctor = CareIds.newId()
        val now = System.currentTimeMillis()
        db.careDao().insertPerson(
            CarePersonEntity(
                id = otherDoctor,
                ownerAccountId = ownerId,
                displayName = "Dr Other",
                identityState = CareEnums.IdentityState.CONFIRMED.name,
                createdAt = now,
                createdBy = doctorId
            )
        )

        val created = commands.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest(
                patientDisplayName = "Stay Put Patient",
                hospitalId = hospitalId
            )
        )
        commands.recordTransfer(
            workspace,
            RecordTransferRequest(
                admissionId = created.admissionId,
                effectiveAt = ClinicalTime.instant(1_700_000_000_000L),
                locationId = bed,
                primaryDepartmentId = dept,
                primaryTeamId = team1,
                consultantPersonId = doctorId
            )
        )
        commands.recordTransfer(
            workspace,
            RecordTransferRequest(
                admissionId = created.admissionId,
                effectiveAt = ClinicalTime.instant(1_700_000_200_000L),
                primaryTeamId = team2,
                consultantPersonId = otherDoctor
            )
        )

        assertEquals(bed, db.careDao().currentLocationAssignment(created.admissionId)?.locationId)
        assertEquals(team2, db.careDao().currentPrimaryTeam(created.admissionId)?.teamId)
        assertEquals(otherDoctor, db.careDao().currentPrimaryTeam(created.admissionId)?.consultantPersonId)
    }

    @Test
    fun nurseShiftWithoutDepartmentChange() = runBlocking {
        val dept = insertDepartment("MED", "Medicine")
        val ward = insertWard("W1", "Ward 1")
        val bed = insertBed(ward, "101", "Bed 101")
        val nurse1 = insertNurse("Nurse One")
        val nurse2 = insertNurse("Nurse Two")

        val created = commands.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest(
                patientDisplayName = "Nursing Patient",
                hospitalId = hospitalId
            )
        )
        commands.recordTransfer(
            workspace,
            RecordTransferRequest(
                admissionId = created.admissionId,
                effectiveAt = ClinicalTime.instant(1_700_000_000_000L),
                locationId = bed,
                primaryDepartmentId = dept,
                nursingPersonId = nurse1.personId,
                nursingProfessionalRoleId = nurse1.roleId
            )
        )
        commands.recordTransfer(
            workspace,
            RecordTransferRequest(
                admissionId = created.admissionId,
                effectiveAt = ClinicalTime.instant(1_700_000_300_000L),
                nursingPersonId = nurse2.personId,
                nursingProfessionalRoleId = nurse2.roleId
            )
        )

        assertEquals(dept, db.careDao().currentPrimaryDepartment(created.admissionId)?.departmentId)
        val nursing = db.careDao().currentNursingAssignments(created.admissionId)
        assertEquals(1, nursing.size)
        assertEquals(nurse2.personId, nursing.first().personId)
    }

    @Test
    fun primaryAndReferralInvolvementWithoutDuplicateEpisode() = runBlocking {
        val created = commands.createPatientAndAdmission(
            workspace,
            CreatePatientAndAdmissionRequest(
                patientDisplayName = "Shared Patient",
                hospitalId = hospitalId,
                involvementRole = CareEnums.InvolvementRole.PRIMARY_TEAM
            )
        )
        val specialist = CareIds.newId()
        val now = System.currentTimeMillis()
        db.careDao().insertPerson(
            CarePersonEntity(
                id = specialist,
                ownerAccountId = ownerId,
                displayName = "Specialist",
                identityState = CareEnums.IdentityState.CONFIRMED.name,
                createdAt = now,
                createdBy = doctorId
            )
        )
        commands.addInvolvement(
            workspace = workspace,
            admissionId = created.admissionId,
            personId = specialist,
            role = CareEnums.InvolvementRole.REFERRAL,
            startsAt = ClinicalTime.instant(1_700_000_400_000L),
            reason = "Medicine opinion"
        )
        commands.endInvolvement(
            workspace = workspace,
            admissionId = created.admissionId,
            involvementId = created.involvementId,
            endedAt = ClinicalTime.instant(1_700_000_500_000L)
        )

        val episode = db.careDao().getEpisode(created.admissionId)
        assertEquals(CareEnums.EpisodeStatus.ACTIVE.name, episode?.status)
        val involvements = db.careDao().involvementsForAdmission(created.admissionId)
        assertEquals(2, involvements.size)
        assertTrue(involvements.any { it.role == CareEnums.InvolvementRole.REFERRAL.name })
        assertEquals(
            CareEnums.InvolvementStatus.ENDED.name,
            involvements.first { it.id == created.involvementId }.status
        )
        assertNotNull(db.careDao().getActiveInpatientEpisode(created.patientId))
    }

    @Test
    fun unassignedIntakeDoesNotFabricatePatient() = runBlocking {
        val intakeId = commands.createUnassignedIntake(
            workspace,
            summary = "Medicine opinion needed in ICU, bed 12",
            identityHintsJson = """{"bed":"12","wardHint":"ICU"}"""
        )
        val intake = db.careDao().getIntake(intakeId)
        assertEquals(CareEnums.IntakeStatus.UNRESOLVED.name, intake?.status)
        assertNull(intake?.resolvedPatientId)
        assertNull(intake?.resolvedAdmissionId)
    }

    private data class NurseRefs(val personId: String, val roleId: String)

    private suspend fun insertNurse(name: String): NurseRefs {
        val personId = CareIds.newId()
        val roleId = CareIds.newId()
        val now = System.currentTimeMillis()
        db.careDao().insertPerson(
            CarePersonEntity(
                id = personId,
                ownerAccountId = ownerId,
                displayName = name,
                identityState = CareEnums.IdentityState.CONFIRMED.name,
                createdAt = now,
                createdBy = doctorId
            )
        )
        db.careDao().insertProfessionalRole(
            CareProfessionalRoleEntity(
                id = roleId,
                ownerAccountId = ownerId,
                personId = personId,
                professionCode = "NURSE",
                validFrom = ClinicalTime.unknown(),
                createdAt = now,
                createdBy = doctorId
            )
        )
        return NurseRefs(personId, roleId)
    }

    private suspend fun insertDepartment(code: String, name: String): String {
        val id = CareIds.newId()
        db.careDao().insertDepartment(
            CareDepartmentEntity(
                id = id,
                ownerAccountId = ownerId,
                hospitalId = hospitalId,
                code = code,
                name = name,
                status = CareEnums.ActiveStatus.ACTIVE.name,
                createdAt = System.currentTimeMillis(),
                createdBy = doctorId
            )
        )
        return id
    }

    private suspend fun insertTeam(code: String, name: String, departmentId: String): String {
        val id = CareIds.newId()
        db.careDao().insertClinicalTeam(
            CareClinicalTeamEntity(
                id = id,
                ownerAccountId = ownerId,
                hospitalId = hospitalId,
                code = code,
                name = name,
                status = CareEnums.ActiveStatus.ACTIVE.name,
                departmentId = departmentId,
                createdAt = System.currentTimeMillis(),
                createdBy = doctorId
            )
        )
        return id
    }

    private suspend fun insertWard(code: String, label: String): String {
        val id = CareIds.newId()
        db.careDao().insertPhysicalLocation(
            CarePhysicalLocationEntity(
                id = id,
                ownerAccountId = ownerId,
                hospitalId = hospitalId,
                parentLocationId = null,
                kind = CareEnums.LocationKind.WARD.name,
                code = code,
                label = label,
                status = CareEnums.ActiveStatus.ACTIVE.name,
                structureState = CareEnums.StructureState.INCOMPLETE.name,
                createdAt = System.currentTimeMillis(),
                createdBy = doctorId
            )
        )
        return id
    }

    private suspend fun insertBed(wardId: String, code: String, label: String): String {
        val id = CareIds.newId()
        db.careDao().insertPhysicalLocation(
            CarePhysicalLocationEntity(
                id = id,
                ownerAccountId = ownerId,
                hospitalId = hospitalId,
                parentLocationId = wardId,
                kind = CareEnums.LocationKind.BED.name,
                code = code,
                label = label,
                status = CareEnums.ActiveStatus.ACTIVE.name,
                structureState = CareEnums.StructureState.COMPLETE.name,
                createdAt = System.currentTimeMillis(),
                createdBy = doctorId
            )
        )
        return id
    }
}
