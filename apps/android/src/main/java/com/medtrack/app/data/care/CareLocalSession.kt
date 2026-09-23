package com.medtrack.app.data.care

import com.medtrack.app.data.care.command.CareWorkspace

/**
 * Local clinician workspace until MT-012 Google sign-in lands.
 * Debug seed and the Compose shell share these stable IDs.
 */
object CareLocalSession {
    const val OWNER_ACCOUNT_ID = "00000000-0000-4000-8000-000000000001"
    const val ACTOR_PERSON_ID = "00000000-0000-4000-8000-000000000002"
    const val HOSPITAL_ID = "00000000-0000-4000-8000-000000000010"
    const val HOSPITAL_CODE = "CITY"
    const val HOSPITAL_NAME = "City Hospital"
    const val TIME_ZONE = "Asia/Kolkata"

    fun workspace(): CareWorkspace = CareWorkspace(
        ownerAccountId = OWNER_ACCOUNT_ID,
        actorPersonId = ACTOR_PERSON_ID
    )
}
