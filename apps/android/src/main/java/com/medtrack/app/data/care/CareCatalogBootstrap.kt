package com.medtrack.app.data.care

import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.entity.CareAccountEntity
import com.medtrack.app.data.care.entity.CareHospitalEntity
import com.medtrack.app.data.care.entity.CarePersonEntity
import com.medtrack.app.data.care.entity.CarePhysicalLocationEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import javax.inject.Inject
import javax.inject.Singleton

data class CareCatalog(
    val hospitalId: String,
    val wards: Map<String, String>,
    val beds: Map<String, String>
)

@Singleton
class CareCatalogBootstrap @Inject constructor(
    private val careDao: CareDao
) {
    suspend fun ensure(): CareCatalog {
        val owner = CareLocalSession.OWNER_ACCOUNT_ID
        val actor = CareLocalSession.ACTOR_PERSON_ID
        val hospitalId = CareLocalSession.HOSPITAL_ID
        val now = System.currentTimeMillis()

        if (careDao.getPerson(actor) == null) {
            careDao.insertPerson(
                CarePersonEntity(
                    id = actor,
                    ownerAccountId = owner,
                    displayName = "Dr. Ankita",
                    identityState = CareEnums.IdentityState.CONFIRMED.name,
                    createdAt = now,
                    createdBy = actor
                )
            )
        }
        if (careDao.accountForOwner(owner) == null) {
            careDao.insertAccount(
                CareAccountEntity(
                    id = CareIds.newId(),
                    authSubject = "synthetic-local",
                    clinicianPersonId = actor,
                    displayName = "Dr. Ankita",
                    ownerAccountId = owner,
                    createdAt = now,
                    createdBy = actor
                )
            )
        }
        if (careDao.findHospitalByCode(owner, CareLocalSession.HOSPITAL_CODE) == null) {
            careDao.insertHospital(
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
        }

        val wardIds = linkedMapOf<String, String>()
        WARDS.forEach { (code, label) ->
            val existing = careDao.findLocation(
                hospitalId,
                null,
                CareEnums.LocationKind.WARD.name,
                code
            )
            val id = existing?.id ?: CareIds.newId().also { wardId ->
                careDao.insertPhysicalLocation(
                    CarePhysicalLocationEntity(
                        id = wardId,
                        ownerAccountId = owner,
                        hospitalId = hospitalId,
                        parentLocationId = null,
                        kind = CareEnums.LocationKind.WARD.name,
                        code = code,
                        label = label,
                        status = CareEnums.ActiveStatus.ACTIVE.name,
                        structureState = CareEnums.StructureState.INCOMPLETE.name,
                        createdAt = now,
                        createdBy = actor
                    )
                )
            }
            wardIds[code] = id
        }

        val bedIds = linkedMapOf<String, String>()
        BEDS.forEach { bed ->
            val wardId = wardIds.getValue(bed.wardCode)
            val existing = careDao.findLocation(
                hospitalId,
                wardId,
                CareEnums.LocationKind.BED.name,
                bed.code
            )
            val id = existing?.id ?: CareIds.newId().also { bedId ->
                careDao.insertPhysicalLocation(
                    CarePhysicalLocationEntity(
                        id = bedId,
                        ownerAccountId = owner,
                        hospitalId = hospitalId,
                        parentLocationId = wardId,
                        kind = CareEnums.LocationKind.BED.name,
                        code = bed.code,
                        label = bed.label,
                        status = CareEnums.ActiveStatus.ACTIVE.name,
                        structureState = CareEnums.StructureState.COMPLETE.name,
                        createdAt = now,
                        createdBy = actor
                    )
                )
            }
            bedIds[bed.code] = id
        }

        return CareCatalog(
            hospitalId = hospitalId,
            wards = wardIds,
            beds = bedIds
        )
    }

    suspend fun findBed(codeOrLabel: String): CarePhysicalLocationEntity? {
        val catalog = ensure()
        val needle = codeOrLabel.trim()
        if (needle.isBlank()) return null
        val locations = careDao.locationsForHospital(
            CareLocalSession.OWNER_ACCOUNT_ID,
            catalog.hospitalId
        )
        return locations.firstOrNull { location ->
            location.kind == CareEnums.LocationKind.BED.name &&
                (location.code.equals(needle, ignoreCase = true) ||
                    location.label.equals(needle, ignoreCase = true) ||
                    location.label.contains(needle, ignoreCase = true) ||
                    needle.contains(location.code, ignoreCase = true))
        }
    }

    suspend fun bedLabels(): List<String> {
        val catalog = ensure()
        return careDao.locationsForHospital(
            CareLocalSession.OWNER_ACCOUNT_ID,
            catalog.hospitalId
        )
            .filter { it.kind == CareEnums.LocationKind.BED.name }
            .map { it.label }
            .sorted()
    }

    private data class BedSeed(val wardCode: String, val code: String, val label: String)

    companion object {
        private val WARDS = listOf(
            "ICU" to "Floor 3 • ICU",
            "F4" to "Floor 4 • Female Med",
            "F5" to "Floor 5 • Male Med"
        )
        private val BEDS = listOf(
            BedSeed("ICU", "04", "ICU Bed 04"),
            BedSeed("ICU", "12", "ICU Bed 12"),
            BedSeed("F4", "402", "Bed 402"),
            BedSeed("F4", "409", "Bed 409"),
            BedSeed("F5", "512", "Bed 512"),
            BedSeed("F5", "518", "Bed 518")
        )
    }
}
