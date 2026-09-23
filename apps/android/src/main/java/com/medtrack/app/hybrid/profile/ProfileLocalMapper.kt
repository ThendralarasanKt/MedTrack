package com.medtrack.app.hybrid.profile

import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.entity.CareHospitalEntity
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.entity.CareProfessionalRoleEntity
import com.medtrack.app.data.care.entity.CareReferenceConceptEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.hybrid.account.BoundAccount
import com.medtrack.app.hybrid.account.AccountSession
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

fun interface ProfileSyncCallback {
    suspend fun onProfileConfirmed(account: BoundAccount, profileJson: JSONObject)
}

/**
 * Applies confirmed cloud profile details to the local clinician Person.
 * Never rewrites event participants, admissions, locations or reminders.
 */
@Singleton
class ProfileLocalMapper @Inject constructor(
    private val careDao: CareDao,
    private val cache: ProfileCache,
    private val accountSession: AccountSession
) {
    suspend fun apply(account: BoundAccount, profileJson: JSONObject) {
        val name = profileJson.optString("displayName").trim().ifBlank { account.displayName }
        val now = System.currentTimeMillis()
        val person = careDao.getPerson(account.actorPersonId) ?: CarePersonEntity(
            id = account.actorPersonId,
            ownerAccountId = account.ownerAccountId,
            displayName = name.ifBlank { "Clinician" },
            identityState = CareEnums.IdentityState.CONFIRMED.name,
            createdAt = now,
            createdBy = account.actorPersonId
        ).also { careDao.insertPerson(it) }
        if (name.isNotBlank() && name != person.displayName) {
            careDao.updatePerson(person.copy(displayName = name, version = person.version + 1))
        }
        cache.putLink(account.userId, "Person:${account.userId}", person.id, profileJson.optInt("version"))
        applyPrimarySpecialty(account, profileJson)
        applyPrimaryHospital(account, profileJson)
    }

    private suspend fun applyPrimarySpecialty(account: BoundAccount, profileJson: JSONObject) {
        val primary = firstPrimary(profileJson.optJSONArray("specialties") ?: JSONArray()) ?: return
        val specialtyId = primary.optString("specialtyId").ifBlank { return }
        val concept = ensureSpecialtyConcept(account.ownerAccountId, account.actorPersonId, specialtyId)
        val now = System.currentTimeMillis()
        val open = careDao.rolesForPerson(account.actorPersonId).firstOrNull { it.validTo == null }
        if (open?.specialtyConceptId == concept.id) {
            cache.putLink(account.userId, "Specialty:$specialtyId", open.id, profileJson.optInt("version"))
            return
        }
        if (open != null) {
            careDao.updateProfessionalRole(
                open.copy(validTo = ClinicalTime.instant(now, accountSession.timeZone()), version = open.version + 1)
            )
        }
        val roleId = CareIds.newId()
        careDao.insertProfessionalRole(
            CareProfessionalRoleEntity(
                id = roleId,
                ownerAccountId = account.ownerAccountId,
                personId = account.actorPersonId,
                professionCode = profileJson.optString("professionCode").ifBlank { "physician" },
                validFrom = ClinicalTime.instant(now, accountSession.timeZone()),
                specialtyConceptId = concept.id,
                createdAt = now,
                createdBy = account.actorPersonId
            )
        )
        cache.putLink(account.userId, "Specialty:$specialtyId", roleId, profileJson.optInt("version"))
    }

    private suspend fun applyPrimaryHospital(account: BoundAccount, profileJson: JSONObject) {
        val primary = firstPrimary(profileJson.optJSONArray("affiliations") ?: JSONArray()) ?: return
        val hospitalId = primary.optString("hospitalId").ifBlank { return }
        val existing = careDao.findHospitalByCode(account.ownerAccountId, hospitalId)
        val localId = existing?.id ?: CareIds.newId().also { created ->
            careDao.insertHospital(
                CareHospitalEntity(
                    id = created,
                    ownerAccountId = account.ownerAccountId,
                    code = hospitalId,
                    name = hospitalId,
                    timeZoneId = accountSession.timeZone(),
                    status = CareEnums.ActiveStatus.ACTIVE.name,
                    createdAt = System.currentTimeMillis(),
                    createdBy = account.actorPersonId
                )
            )
        }
        cache.putLink(account.userId, "Hospital:$hospitalId", localId, profileJson.optInt("version"))
    }

    private suspend fun ensureSpecialtyConcept(
        ownerAccountId: String,
        actorPersonId: String,
        specialtyId: String
    ): CareReferenceConceptEntity {
        val existing = careDao.findReferenceConcept(ownerAccountId, "SPECIALTY", specialtyId)
        if (existing != null) return existing
        val created = CareReferenceConceptEntity(
            id = CareIds.newId(),
            ownerAccountId = ownerAccountId,
            domain = "SPECIALTY",
            system = "medtrack-catalogue",
            code = specialtyId,
            display = specialtyId,
            active = true,
            createdAt = System.currentTimeMillis(),
            createdBy = actorPersonId
        )
        careDao.insertReferenceConcept(created)
        return created
    }

    private fun firstPrimary(array: JSONArray): JSONObject? {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optBoolean("isPrimary")) return item
        }
        return null
    }
}
