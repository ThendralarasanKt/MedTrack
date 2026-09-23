package com.medtrack.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Non-destructive care-model table creation, then drop of unused legacy tables.
 */
object CareMigrations {
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_accounts (
                  id TEXT NOT NULL PRIMARY KEY,
                  authSubject TEXT NOT NULL,
                  clinicianPersonId TEXT NOT NULL,
                  displayName TEXT NOT NULL,
                  ownerAccountId TEXT NOT NULL,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_persons (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  displayName TEXT NOT NULL,
                  identityState TEXT NOT NULL,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_care_persons_owner_name ON care_persons(ownerAccountId, displayName)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_patients (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  personId TEXT NOT NULL,
                  birthDate TEXT,
                  reportedAge TEXT,
                  ageUnit TEXT,
                  ageAsOf TEXT,
                  sexConceptId TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_care_patients_owner_person ON care_patients(ownerAccountId, personId)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_professional_roles (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  personId TEXT NOT NULL,
                  professionCode TEXT NOT NULL,
                  validFrom TEXT NOT NULL,
                  validTo TEXT,
                  specialtyConceptId TEXT,
                  registrationIssuer TEXT,
                  registrationNumber TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_person_contacts (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  personId TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  value TEXT NOT NULL,
                  status TEXT NOT NULL,
                  label TEXT,
                  verifiedAt INTEGER,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_hospitals (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  code TEXT NOT NULL,
                  name TEXT NOT NULL,
                  timeZoneId TEXT NOT NULL,
                  status TEXT NOT NULL,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_care_hospitals_owner_code ON care_hospitals(ownerAccountId, code)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_departments (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  hospitalId TEXT NOT NULL,
                  code TEXT NOT NULL,
                  name TEXT NOT NULL,
                  status TEXT NOT NULL,
                  parentDepartmentId TEXT,
                  specialtyConceptId TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_care_departments_hospital_code ON care_departments(hospitalId, code)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_clinical_units (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  departmentId TEXT NOT NULL,
                  code TEXT NOT NULL,
                  name TEXT NOT NULL,
                  status TEXT NOT NULL,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_care_clinical_units_dept_code ON care_clinical_units(departmentId, code)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_clinical_teams (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  hospitalId TEXT NOT NULL,
                  code TEXT NOT NULL,
                  name TEXT NOT NULL,
                  status TEXT NOT NULL,
                  departmentId TEXT,
                  clinicalUnitId TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_care_clinical_teams_hospital_code ON care_clinical_teams(hospitalId, code)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_team_memberships (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  teamId TEXT NOT NULL,
                  professionalRoleId TEXT NOT NULL,
                  roleCode TEXT NOT NULL,
                  validFrom TEXT NOT NULL,
                  validTo TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_physical_locations (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  hospitalId TEXT NOT NULL,
                  parentLocationId TEXT,
                  kind TEXT NOT NULL,
                  code TEXT NOT NULL,
                  label TEXT NOT NULL,
                  status TEXT NOT NULL,
                  structureState TEXT NOT NULL,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_care_physical_locations_siblings
                ON care_physical_locations(hospitalId, parentLocationId, kind, code)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_station_coverage (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  stationId TEXT NOT NULL,
                  coveredLocationId TEXT NOT NULL,
                  startsAt TEXT NOT NULL,
                  endsAt TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_department_location_use (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  departmentId TEXT NOT NULL,
                  locationId TEXT NOT NULL,
                  validFrom TEXT NOT NULL,
                  validTo TEXT,
                  purpose TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_reference_concepts (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  domain TEXT NOT NULL,
                  system TEXT NOT NULL,
                  code TEXT NOT NULL,
                  display TEXT NOT NULL,
                  active INTEGER NOT NULL,
                  terminologyVersion TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_care_reference_concepts_key
                ON care_reference_concepts(domain, system, code, terminologyVersion)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_concept_aliases (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  conceptId TEXT NOT NULL,
                  alias TEXT NOT NULL,
                  normalizedAlias TEXT NOT NULL,
                  language TEXT NOT NULL,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_patient_identifiers (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  patientId TEXT NOT NULL,
                  hospitalId TEXT NOT NULL,
                  system TEXT NOT NULL,
                  value TEXT NOT NULL,
                  status TEXT NOT NULL,
                  sourceItemId TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_care_patient_identifiers_lookup
                ON care_patient_identifiers(ownerAccountId, hospitalId, system, value)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_hospital_episodes (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  patientId TEXT NOT NULL,
                  hospitalId TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  externalId TEXT,
                  status TEXT NOT NULL,
                  startedAt TEXT NOT NULL,
                  endedAt TEXT,
                  eventId TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_care_hospital_episodes_patient_status ON care_hospital_episodes(ownerAccountId, patientId, status)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_care_hospital_episodes_kind_status ON care_hospital_episodes(patientId, kind, status)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_episode_links (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  fromEpisodeId TEXT NOT NULL,
                  toEpisodeId TEXT NOT NULL,
                  relationship TEXT NOT NULL,
                  eventId TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_involvements (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  admissionId TEXT NOT NULL,
                  personId TEXT NOT NULL,
                  teamId TEXT,
                  role TEXT NOT NULL,
                  reason TEXT,
                  status TEXT NOT NULL,
                  startsAt TEXT NOT NULL,
                  endsAt TEXT,
                  eventId TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_care_involvements_person_status ON care_involvements(ownerAccountId, personId, status)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_unassigned_intakes (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  summary TEXT NOT NULL,
                  identityHintsJson TEXT NOT NULL,
                  status TEXT NOT NULL,
                  resolvedPatientId TEXT,
                  resolvedAdmissionId TEXT,
                  resolutionNote TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            createAssignmentTable(db, "care_location_assignments", extra = "locationId TEXT NOT NULL, stationId TEXT, verification TEXT NOT NULL")
            createAssignmentTable(db, "care_department_assignments", extra = "departmentId TEXT NOT NULL, role TEXT NOT NULL")
            createAssignmentTable(
                db,
                "care_admission_team_assignments",
                extra = "teamId TEXT NOT NULL, consultantPersonId TEXT, role TEXT NOT NULL"
            )
            createAssignmentTable(
                db,
                "care_nursing_assignments",
                extra = "personId TEXT, professionalRoleId TEXT, stationId TEXT, role TEXT NOT NULL"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_care_location_assignments_current ON care_location_assignments(admissionId, endsAtJson)"
            )
        }

        private fun createAssignmentTable(db: SupportSQLiteDatabase, name: String, extra: String) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $name (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  admissionId TEXT NOT NULL,
                  $extra,
                  startsAt TEXT NOT NULL,
                  endsAt TEXT,
                  endsAtJson TEXT,
                  eventId TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS care_clinical_events (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  patientId TEXT NOT NULL,
                  admissionId TEXT,
                  eventType TEXT NOT NULL,
                  effectiveTime TEXT NOT NULL,
                  recordedAt INTEGER NOT NULL,
                  recorderPersonId TEXT NOT NULL,
                  verification TEXT NOT NULL,
                  recordState TEXT NOT NULL,
                  operationId TEXT NOT NULL,
                  supersedesEventId TEXT,
                  correctionReason TEXT,
                  createdAt INTEGER NOT NULL,
                  createdBy TEXT NOT NULL,
                  version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            // Index names must match Room's generated schema (see schemas/.../7.json).
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_clinical_events_ownerAccountId_patientId_recordedAt` ON `care_clinical_events` (`ownerAccountId`, `patientId`, `recordedAt`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_clinical_events_operationId` ON `care_clinical_events` (`operationId`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_event_participants` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `eventId` TEXT NOT NULL,
                  `personId` TEXT NOT NULL,
                  `role` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_care_event_participants_eventId_personId_role` ON `care_event_participants` (`eventId`, `personId`, `role`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_record_revisions` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `recordType` TEXT NOT NULL,
                  `recordId` TEXT NOT NULL,
                  `recordVersion` INTEGER NOT NULL,
                  `schemaVersion` INTEGER NOT NULL,
                  `payloadSnapshot` TEXT NOT NULL,
                  `eventId` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_care_record_revisions_recordType_recordId_recordVersion` ON `care_record_revisions` (`recordType`, `recordId`, `recordVersion`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_audit_entries` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `operationId` TEXT NOT NULL,
                  `actorPersonId` TEXT NOT NULL,
                  `action` TEXT NOT NULL,
                  `targetType` TEXT NOT NULL,
                  `targetId` TEXT NOT NULL,
                  `priorVersion` INTEGER,
                  `newVersion` INTEGER NOT NULL,
                  `recordedAt` INTEGER NOT NULL,
                  `reason` TEXT,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_audit_entries_operationId` ON `care_audit_entries` (`operationId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_audit_entries_targetType_targetId` ON `care_audit_entries` (`targetType`, `targetId`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_applied_operations` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `operationId` TEXT NOT NULL,
                  `requestHash` TEXT NOT NULL,
                  `committedAt` INTEGER NOT NULL,
                  `resultReferences` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_care_applied_operations_ownerAccountId_operationId` ON `care_applied_operations` (`ownerAccountId`, `operationId`)"
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_legacy_links` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `legacyTable` TEXT NOT NULL,
                  `legacyId` INTEGER NOT NULL,
                  `careObjectType` TEXT NOT NULL,
                  `careObjectId` TEXT,
                  `carePatientId` TEXT,
                  `careEpisodeId` TEXT,
                  `mappingStatus` TEXT NOT NULL,
                  `destinationHint` TEXT,
                  `uncertaintyNote` TEXT,
                  `sourceSnapshotJson` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_care_legacy_links_ownerAccountId_legacyTable_legacyId_careObjectType` ON `care_legacy_links` (`ownerAccountId`, `legacyTable`, `legacyId`, `careObjectType`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_legacy_links_carePatientId` ON `care_legacy_links` (`carePatientId`)"
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_encounters` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `admissionId` TEXT NOT NULL,
                  `kind` TEXT NOT NULL,
                  `reviewedAt` TEXT NOT NULL,
                  `summary` TEXT NOT NULL,
                  `eventId` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_encounters_ownerAccountId_admissionId` ON `care_encounters` (`ownerAccountId`, `admissionId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_encounters_eventId` ON `care_encounters` (`eventId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_clinical_notes` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `patientId` TEXT NOT NULL,
                  `admissionId` TEXT,
                  `encounterId` TEXT,
                  `kind` TEXT NOT NULL,
                  `text` TEXT NOT NULL,
                  `eventId` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_clinical_notes_ownerAccountId_patientId` ON `care_clinical_notes` (`ownerAccountId`, `patientId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_clinical_notes_encounterId` ON `care_clinical_notes` (`encounterId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_clinical_notes_eventId` ON `care_clinical_notes` (`eventId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_problems` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `patientId` TEXT NOT NULL,
                  `admissionId` TEXT,
                  `description` TEXT NOT NULL,
                  `codeSystem` TEXT,
                  `code` TEXT,
                  `certainty` TEXT NOT NULL,
                  `status` TEXT NOT NULL,
                  `onset` TEXT NOT NULL,
                  `resolvedAt` TEXT,
                  `eventId` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_problems_ownerAccountId_patientId_status` ON `care_problems` (`ownerAccountId`, `patientId`, `status`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_problems_eventId` ON `care_problems` (`eventId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_allergies` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `patientId` TEXT NOT NULL,
                  `substance` TEXT NOT NULL,
                  `reaction` TEXT,
                  `severity` TEXT,
                  `status` TEXT NOT NULL,
                  `eventId` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_allergies_ownerAccountId_patientId_status` ON `care_allergies` (`ownerAccountId`, `patientId`, `status`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_allergies_eventId` ON `care_allergies` (`eventId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_allergy_assessments` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `patientId` TEXT NOT NULL,
                  `result` TEXT NOT NULL,
                  `eventId` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_allergy_assessments_ownerAccountId_patientId` ON `care_allergy_assessments` (`ownerAccountId`, `patientId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_allergy_assessments_eventId` ON `care_allergy_assessments` (`eventId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_observations` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `patientId` TEXT NOT NULL,
                  `admissionId` TEXT,
                  `reportId` TEXT,
                  `specimenId` TEXT,
                  `encounterId` TEXT,
                  `name` TEXT NOT NULL,
                  `valueKind` TEXT NOT NULL,
                  `numericValue` TEXT,
                  `textValue` TEXT,
                  `booleanValue` INTEGER,
                  `unit` TEXT,
                  `referenceRangeText` TEXT,
                  `observedAt` TEXT NOT NULL,
                  `eventId` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_observations_ownerAccountId_patientId` ON `care_observations` (`ownerAccountId`, `patientId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_observations_encounterId` ON `care_observations` (`encounterId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_observations_eventId` ON `care_observations` (`eventId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_clinical_decisions` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `admissionId` TEXT NOT NULL,
                  `encounterId` TEXT,
                  `description` TEXT NOT NULL,
                  `rationale` TEXT,
                  `decidedAt` TEXT NOT NULL,
                  `status` TEXT NOT NULL,
                  `eventId` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_clinical_decisions_ownerAccountId_admissionId_status` ON `care_clinical_decisions` (`ownerAccountId`, `admissionId`, `status`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_clinical_decisions_encounterId` ON `care_clinical_decisions` (`encounterId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_clinical_decisions_eventId` ON `care_clinical_decisions` (`eventId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_decision_problem_links` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `decisionId` TEXT NOT NULL,
                  `problemId` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_care_decision_problem_links_decisionId_problemId` ON `care_decision_problem_links` (`decisionId`, `problemId`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_plan_revisions` (
                  `id` TEXT NOT NULL,
                  `ownerAccountId` TEXT NOT NULL,
                  `admissionId` TEXT NOT NULL,
                  `decisionId` TEXT,
                  `goals` TEXT NOT NULL,
                  `instructions` TEXT NOT NULL,
                  `effectiveAt` TEXT NOT NULL,
                  `previousRevisionId` TEXT,
                  `eventId` TEXT NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `createdBy` TEXT NOT NULL,
                  `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_plan_revisions_ownerAccountId_admissionId` ON `care_plan_revisions` (`ownerAccountId`, `admissionId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_plan_revisions_decisionId` ON `care_plan_revisions` (`decisionId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_plan_revisions_eventId` ON `care_plan_revisions` (`eventId`)"
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_medication_definitions` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `ingredient` TEXT, `strengthValue` TEXT, `strengthUnit` TEXT, `form` TEXT, `codeSystem` TEXT, `code` TEXT, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_definitions_ownerAccountId_displayName` ON `care_medication_definitions` (`ownerAccountId`, `displayName`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_medication_history_items` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `admissionId` TEXT, `drugText` TEXT NOT NULL, `regimenText` TEXT, `useStatus` TEXT NOT NULL, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_history_items_ownerAccountId_patientId` ON `care_medication_history_items` (`ownerAccountId`, `patientId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_history_items_eventId` ON `care_medication_history_items` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_medication_orders` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `medicationId` TEXT NOT NULL, `decisionId` TEXT, `prescriberId` TEXT, `orderedAt` TEXT NOT NULL, `doseValue` TEXT, `doseUnit` TEXT, `doseText` TEXT, `route` TEXT, `schedule` TEXT NOT NULL, `startsAt` TEXT NOT NULL, `endsAt` TEXT, `status` TEXT NOT NULL, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_orders_ownerAccountId_admissionId_status` ON `care_medication_orders` (`ownerAccountId`, `admissionId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_orders_medicationId` ON `care_medication_orders` (`medicationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_orders_eventId` ON `care_medication_orders` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_medication_order_events` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `orderId` TEXT NOT NULL, `action` TEXT NOT NULL, `reason` TEXT, `previousOrderVersion` INTEGER, `newOrderVersion` INTEGER NOT NULL, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_order_events_orderId` ON `care_medication_order_events` (`orderId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_order_events_eventId` ON `care_medication_order_events` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_medication_administrations` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `orderId` TEXT, `medicationId` TEXT NOT NULL, `outcome` TEXT NOT NULL, `doseValue` TEXT, `doseUnit` TEXT, `route` TEXT, `administeredAt` TEXT NOT NULL, `performerId` TEXT, `reason` TEXT, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_administrations_ownerAccountId_admissionId` ON `care_medication_administrations` (`ownerAccountId`, `admissionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_administrations_orderId` ON `care_medication_administrations` (`orderId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_medication_administrations_eventId` ON `care_medication_administrations` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_procedure_orders` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `decisionId` TEXT, `procedureName` TEXT NOT NULL, `requesterId` TEXT, `requestedAt` TEXT NOT NULL, `plannedAt` TEXT, `status` TEXT NOT NULL, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_procedure_orders_ownerAccountId_admissionId_status` ON `care_procedure_orders` (`ownerAccountId`, `admissionId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_procedure_orders_eventId` ON `care_procedure_orders` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_procedure_events` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `orderId` TEXT, `procedureName` TEXT NOT NULL, `outcome` TEXT NOT NULL, `performedAt` TEXT NOT NULL, `findings` TEXT, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_procedure_events_ownerAccountId_admissionId` ON `care_procedure_events` (`ownerAccountId`, `admissionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_procedure_events_orderId` ON `care_procedure_events` (`orderId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_procedure_events_eventId` ON `care_procedure_events` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_investigation_orders` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `decisionId` TEXT, `kind` TEXT NOT NULL, `testName` TEXT NOT NULL, `requesterId` TEXT, `requestedAt` TEXT NOT NULL, `requestGroupId` TEXT, `status` TEXT NOT NULL, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_investigation_orders_ownerAccountId_admissionId_status` ON `care_investigation_orders` (`ownerAccountId`, `admissionId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_investigation_orders_eventId` ON `care_investigation_orders` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_specimens` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `type` TEXT NOT NULL, `accession` TEXT, `collectedAt` TEXT NOT NULL, `collectorId` TEXT, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_specimens_ownerAccountId_admissionId` ON `care_specimens` (`ownerAccountId`, `admissionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_specimens_eventId` ON `care_specimens` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_order_specimen_links` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `orderId` TEXT NOT NULL, `specimenId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_order_specimen_links_orderId_specimenId` ON `care_order_specimen_links` (`orderId`, `specimenId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_imaging_studies` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `orderId` TEXT, `modality` TEXT NOT NULL, `bodySite` TEXT, `accession` TEXT, `performedAt` TEXT NOT NULL, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_imaging_studies_ownerAccountId_admissionId` ON `care_imaging_studies` (`ownerAccountId`, `admissionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_imaging_studies_orderId` ON `care_imaging_studies` (`orderId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_imaging_studies_eventId` ON `care_imaging_studies` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_documents` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT, `patientId` TEXT, `fileName` TEXT NOT NULL, `filePath` TEXT NOT NULL, `fileType` TEXT NOT NULL, `attachedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_documents_ownerAccountId_admissionId` ON `care_documents` (`ownerAccountId`, `admissionId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_diagnostic_reports` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `kind` TEXT NOT NULL, `issuer` TEXT, `reportedAt` TEXT NOT NULL, `status` TEXT NOT NULL, `previousReportId` TEXT, `missingPredecessorNote` TEXT, `imagingStudyId` TEXT, `narrative` TEXT, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_diagnostic_reports_ownerAccountId_admissionId_status` ON `care_diagnostic_reports` (`ownerAccountId`, `admissionId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_diagnostic_reports_eventId` ON `care_diagnostic_reports` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_report_order_links` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `reportId` TEXT NOT NULL, `orderId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_report_order_links_reportId_orderId` ON `care_report_order_links` (`reportId`, `orderId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_report_document_links` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `reportId` TEXT NOT NULL, `documentId` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_report_document_links_reportId_sequence` ON `care_report_document_links` (`reportId`, `sequence`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_report_reviews` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `reportId` TEXT NOT NULL, `reviewerPersonId` TEXT NOT NULL, `reviewedAt` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `decisionId` TEXT, `note` TEXT, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_report_reviews_reportId` ON `care_report_reviews` (`reportId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_report_reviews_eventId` ON `care_report_reviews` (`eventId`)")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_referrals` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `direction` TEXT NOT NULL, `requesterPersonId` TEXT, `requesterTeamId` TEXT, `requestedPersonId` TEXT, `requestedTeamId` TEXT, `requestedSpecialty` TEXT, `reason` TEXT NOT NULL, `requestedAt` TEXT NOT NULL, `priority` TEXT NOT NULL, `status` TEXT NOT NULL, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_referrals_ownerAccountId_admissionId_status` ON `care_referrals` (`ownerAccountId`, `admissionId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_referrals_eventId` ON `care_referrals` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_referral_milestones` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `referralId` TEXT NOT NULL, `kind` TEXT NOT NULL, `encounterId` TEXT, `note` TEXT, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_referral_milestones_referralId` ON `care_referral_milestones` (`referralId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_referral_milestones_eventId` ON `care_referral_milestones` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_consultation_advice` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `referralId` TEXT, `admissionId` TEXT NOT NULL, `advisorPersonId` TEXT, `text` TEXT NOT NULL, `advisedAt` TEXT NOT NULL, `disposition` TEXT NOT NULL, `reviewedBy` TEXT, `reviewedAt` INTEGER, `dispositionReason` TEXT, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_consultation_advice_ownerAccountId_admissionId` ON `care_consultation_advice` (`ownerAccountId`, `admissionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_consultation_advice_referralId` ON `care_consultation_advice` (`referralId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_consultation_advice_eventId` ON `care_consultation_advice` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_advice_decision_links` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `adviceId` TEXT NOT NULL, `decisionId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_advice_decision_links_adviceId_decisionId` ON `care_advice_decision_links` (`adviceId`, `decisionId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_clinical_questions` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `askerPersonId` TEXT, `text` TEXT NOT NULL, `askedAt` TEXT NOT NULL, `status` TEXT NOT NULL, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_clinical_questions_ownerAccountId_admissionId_status` ON `care_clinical_questions` (`ownerAccountId`, `admissionId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_clinical_questions_eventId` ON `care_clinical_questions` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_question_responses` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `questionId` TEXT NOT NULL, `responderPersonId` TEXT, `text` TEXT NOT NULL, `decisionId` TEXT, `answeredAt` TEXT NOT NULL, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_question_responses_questionId` ON `care_question_responses` (`questionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_question_responses_eventId` ON `care_question_responses` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_communication_events` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `senderPersonId` TEXT, `channel` TEXT NOT NULL, `state` TEXT NOT NULL, `communicatedAt` TEXT NOT NULL, `contentSummary` TEXT NOT NULL, `precedingCommunicationId` TEXT, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_communication_events_ownerAccountId_admissionId` ON `care_communication_events` (`ownerAccountId`, `admissionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_communication_events_eventId` ON `care_communication_events` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_communication_recipients` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `communicationId` TEXT NOT NULL, `personId` TEXT, `teamId` TEXT, `stationId` TEXT, `reportedLabel` TEXT, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_communication_recipients_communicationId` ON `care_communication_recipients` (`communicationId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_communication_subjects` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `communicationId` TEXT NOT NULL, `referralId` TEXT, `questionId` TEXT, `decisionId` TEXT, `medicationOrderId` TEXT, `careTaskId` TEXT, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_communication_subjects_communicationId` ON `care_communication_subjects` (`communicationId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_tasks` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `admissionId` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `instructions` TEXT, `status` TEXT NOT NULL, `priority` TEXT NOT NULL, `followUpOwnerPersonId` TEXT NOT NULL, `dueAt` INTEGER, `dueZoneId` TEXT, `waitingReason` TEXT, `completedAt` TEXT, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_tasks_ownerAccountId_admissionId_status` ON `care_tasks` (`ownerAccountId`, `admissionId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_tasks_followUpOwnerPersonId_status` ON `care_tasks` (`followUpOwnerPersonId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_tasks_eventId` ON `care_tasks` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_task_assignments` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `taskId` TEXT NOT NULL, `personId` TEXT, `teamId` TEXT, `stationId` TEXT, `startsAt` TEXT NOT NULL, `endsAt` TEXT, `eventId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_task_assignments_taskId` ON `care_task_assignments` (`taskId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_task_assignments_eventId` ON `care_task_assignments` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_task_clinical_links` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `taskId` TEXT NOT NULL, `referralId` TEXT, `questionId` TEXT, `decisionId` TEXT, `medicationOrderId` TEXT, `procedureOrderId` TEXT, `investigationOrderId` TEXT, `reportId` TEXT, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_task_clinical_links_taskId` ON `care_task_clinical_links` (`taskId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_task_responses` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `taskId` TEXT NOT NULL, `action` TEXT NOT NULL, `actorPersonId` TEXT NOT NULL, `note` TEXT, `performedAt` TEXT NOT NULL, `oldDueAt` INTEGER, `newDueAt` INTEGER, `eventId` TEXT NOT NULL, `operationId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_task_responses_taskId` ON `care_task_responses` (`taskId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_task_responses_operationId` ON `care_task_responses` (`operationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_task_responses_eventId` ON `care_task_responses` (`eventId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_reminder_schedules` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `careTaskId` TEXT, `intakeFollowUpId` TEXT, `triggerAt` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `state` TEXT NOT NULL, `precision` TEXT NOT NULL, `platformRequestKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_reminder_schedules_careTaskId` ON `care_reminder_schedules` (`careTaskId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_reminder_schedules_platformRequestKey` ON `care_reminder_schedules` (`platformRequestKey`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_notification_attempts` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `scheduleId` TEXT NOT NULL, `scheduleRevision` INTEGER NOT NULL, `firedAt` INTEGER, `postAttemptedAt` INTEGER, `status` TEXT NOT NULL, `reasonCode` TEXT, `interactedAt` INTEGER, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_notification_attempts_scheduleId` ON `care_notification_attempts` (`scheduleId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `care_scheduling_outbox` (
                  `id` TEXT NOT NULL, `ownerAccountId` TEXT NOT NULL, `scheduleId` TEXT NOT NULL, `scheduleRevision` INTEGER NOT NULL, `action` TEXT NOT NULL, `state` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `lastErrorCode` TEXT, `nextRetryAt` INTEGER, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `version` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_scheduling_outbox_scheduleId_scheduleRevision` ON `care_scheduling_outbox` (`scheduleId`, `scheduleRevision`)")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS follow_ups")
            db.execSQL("DROP TABLE IF EXISTS reports")
            db.execSQL("DROP TABLE IF EXISTS medicines")
            db.execSQL("DROP TABLE IF EXISTS tasks")
            db.execSQL("DROP TABLE IF EXISTS visits")
            db.execSQL("DROP TABLE IF EXISTS patients")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hybrid_inference_requests (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  accountId TEXT NOT NULL,
                  deviceId TEXT NOT NULL,
                  requestId TEXT NOT NULL,
                  purpose TEXT NOT NULL,
                  status TEXT NOT NULL,
                  clientVersion TEXT NOT NULL,
                  commandSchemaVersion TEXT NOT NULL,
                  contextDigest TEXT NOT NULL,
                  inputDigest TEXT NOT NULL,
                  jobId TEXT,
                  patientId TEXT,
                  admissionId TEXT,
                  draftText TEXT NOT NULL,
                  contextJson TEXT NOT NULL,
                  errorCode TEXT,
                  createdAt INTEGER NOT NULL,
                  updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_hybrid_inference_requests_ownerAccountId_requestId` ON hybrid_inference_requests(ownerAccountId, requestId)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hybrid_proposals (
                  id TEXT NOT NULL PRIMARY KEY,
                  ownerAccountId TEXT NOT NULL,
                  accountId TEXT NOT NULL,
                  deviceId TEXT NOT NULL,
                  proposalId TEXT NOT NULL,
                  jobId TEXT,
                  requestId TEXT NOT NULL,
                  status TEXT NOT NULL,
                  contextDigest TEXT NOT NULL,
                  payloadDigest TEXT NOT NULL,
                  bundleJson TEXT NOT NULL,
                  createdAt INTEGER NOT NULL,
                  updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_hybrid_proposals_ownerAccountId_proposalId` ON hybrid_proposals(ownerAccountId, proposalId)"
            )
        }
    }
}
