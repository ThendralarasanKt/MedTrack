package com.medtrack.app.hybrid

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medtrack.app.data.care.CareLocalSession
import com.medtrack.app.data.care.entity.CareEventParticipantEntity
import com.medtrack.app.data.care.entity.CareHospitalEntity
import com.medtrack.app.data.care.entity.CareHospitalEpisodeEntity
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.entity.CareReminderScheduleEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.hybrid.account.AccountBindingStore
import com.medtrack.app.hybrid.account.AccountKind
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.hybrid.account.BoundAccount
import com.medtrack.app.hybrid.profile.ProfileCache
import com.medtrack.app.hybrid.profile.ProfileLocalMapper
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileLocalMapperTest {
    private lateinit var db: AppDatabase
    private lateinit var cache: ProfileCache
    private lateinit var mapper: ProfileLocalMapper
    private val owner = CareLocalSession.OWNER_ACCOUNT_ID
    private val actor = CareLocalSession.ACTOR_PERSON_ID
    private val hospitalId = CareLocalSession.HOSPITAL_ID

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cache = ProfileCache(context).apply { inMemoryOnly = true }
        val bindings = AccountBindingStore(context).apply { inMemoryOnly = true }
        mapper = ProfileLocalMapper(db.careDao(), cache, AccountSession(bindings))
        val now = System.currentTimeMillis()
        db.careDao().insertPerson(
            CarePersonEntity(
                id = actor,
                ownerAccountId = owner,
                displayName = "Dr. Ankita",
                identityState = CareEnums.IdentityState.CONFIRMED.name,
                createdAt = now,
                createdBy = actor
            )
        )
        db.careDao().insertHospital(
            CareHospitalEntity(
                id = hospitalId,
                ownerAccountId = owner,
                code = CareLocalSession.HOSPITAL_CODE,
                name = CareLocalSession.HOSPITAL_NAME,
                timeZoneId = CareLocalSession.TIME_ZONE,
                status = CareEnums.ActiveStatus.ACTIVE.name,
                createdAt = now,
                createdBy = actor
            )
        )
        db.careDao().insertEventParticipant(
            CareEventParticipantEntity(
                id = CareIds.newId(),
                ownerAccountId = owner,
                eventId = "evt-1",
                personId = actor,
                role = "ATTENDING",
                createdAt = now,
                createdBy = actor
            )
        )
        db.careDao().insertEpisode(
            CareHospitalEpisodeEntity(
                id = "ep-1",
                ownerAccountId = owner,
                patientId = "pat-1",
                hospitalId = hospitalId,
                kind = CareEnums.EpisodeKind.INPATIENT.name,
                status = CareEnums.EpisodeStatus.ACTIVE.name,
                startedAt = ClinicalTime.instant(now, CareLocalSession.TIME_ZONE),
                createdAt = now,
                createdBy = actor
            )
        )
        db.careWorkDao().insertReminderSchedule(
            CareReminderScheduleEntity(
                id = "rem-1",
                ownerAccountId = owner,
                triggerAt = now + 60_000,
                zoneId = CareLocalSession.TIME_ZONE,
                revision = 1,
                state = "SCHEDULED",
                precision = "INSTANT",
                platformRequestKey = "key-1",
                createdAt = now,
                createdBy = actor
            )
        )
        cache.saveConfirmed(
            accountId = owner,
            userId = "user-ankita",
            email = null,
            profileJson = JSONObject().put("version", 1),
            accessJson = null
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun displayNameChangeDoesNotRewriteHistory() = runBlocking {
        val beforeParticipants = db.careDao().participantsForPerson(actor)
        val beforeEpisodes = db.careDao().episodesForOwner(owner)
        val beforeReminders = db.careDao().remindersForOwner(owner)
        mapper.apply(
            bound(),
            JSONObject()
                .put("displayName", "Dr. Ankita Patel")
                .put("version", 2)
                .put(
                    "specialties",
                    JSONArray().put(JSONObject().put("specialtyId", "spec-nephrology").put("isPrimary", true))
                )
                .put(
                    "affiliations",
                    JSONArray().put(JSONObject().put("hospitalId", "hosp-city").put("isPrimary", true))
                )
        )
        assertEquals("Dr. Ankita Patel", db.careDao().getPerson(actor)?.displayName)
        assertEquals(beforeParticipants, db.careDao().participantsForPerson(actor))
        assertEquals(beforeEpisodes.map { it.hospitalId }, db.careDao().episodesForOwner(owner).map { it.hospitalId })
        assertEquals(beforeReminders.map { it.id }, db.careDao().remindersForOwner(owner).map { it.id })
        val extraHospital = db.careDao().findHospitalByCode(owner, "hosp-city")
        assertTrue(extraHospital != null)
        assertNotEquals(hospitalId, extraHospital?.id)
    }

    private fun bound() = BoundAccount(
        userId = "user-ankita",
        accountId = owner,
        ownerAccountId = owner,
        actorPersonId = actor,
        authIssuer = "medtrack-test",
        authSubject = "synthetic-ankita",
        deviceId = "dev",
        displayName = "Dr. Ankita",
        onlineAuthorized = true,
        kind = AccountKind.SYNTHETIC
    )
}
