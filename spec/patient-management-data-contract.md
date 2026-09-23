# Patient management: concrete data-object contract

Status: Proposed implementation contract; no schema or application code changed  
Date: 2026-09-19  
Foundation: [Patient information model](patient-information-model.md)  
Downstream application: [Ankita's patient-management workflow](patient-management-workflows.md)

This defines logical persisted objects, required fields, relationships and invariants. Room table names may follow repository conventions, but these distinctions must survive implementation. The older six-entity schema is not sufficient. `PatientMedicalRecord` is a query aggregate over these objects, not another JSON record to maintain.

## 1. Field conventions and shared contracts

- Every persisted object has `id: UUID`, `ownerAccountId: UUID`, `createdAt: Instant`, `createdBy: PersonId`, `version: Int >= 1`. IDs are generated locally. Foreign keys are UUID references, not names or bed numbers.
- Fields listed below are required unless suffixed `?`. Lists are child/link rows, not comma-separated IDs. Enumerations are closed sets unless explicitly identified as local catalogues.
- `Instant` is a UTC instant. `ClinicalTime` is a value object with `precision: INSTANT | DATE | UNKNOWN`, `instant?`, `localDate?`, `zoneId?`, `originalText?`, `uncertaintyNote?`. INSTANT requires instant and known zone/offset; DATE requires localDate; UNKNOWN requires neither. Preserve unresolvable source time as originalText; never fabricate midnight. Required ClinicalTime may explicitly be UNKNOWN.
- Every clinical entry has `eventId -> ClinicalEvent`. Its effective time, recorder, source, verification and amendment history come from that event. Object-specific times below are additional milestones, not replacements for event time.
- Mutable objects use optimistic `version` checks. Clinical revisions retain the previous payload in immutable `RecordRevision` rows and append a new event. Lifecycle changes such as stopping an order have their own event rows. No routine hard deletion of patient care history.
- Enumerated `verification: REPORTED | VERIFIED` indicates the recorder's stated verification, not truth guaranteed by software. AI drafts have no clinical event until approved and committed. `recordState: ACTIVE | ENTERED_IN_ERROR` is separate from clinical status such as resolved or stopped.
- Every relation must stay within the same owner account. Clinical children must agree on patient/admission. Enforce this in database keys where possible and always in the shared transactional command layer.
- Nullable attribution means unknown. Do not automatically fill the authenticated doctor as the prescriber, performer or source author.
- Master-data `status` means ACTIVE/INACTIVE unless another enum is explicitly listed. Reference-valued fields use the catalogue below; legacy free-text route/specialty/test descriptions are preserved alongside optional resolved concept IDs rather than silently converted.
- Account is the ownership root: its ownerAccountId equals its own ID. Create Account and its clinician Person in a single bootstrap transaction with deferred relationship validation; existing authenticated identity supplies the initial creator. Other records inherit that owner, never infer it from a referenced name.
- `validFrom/validTo` and `startsAt/endsAt` have identical interval semantics: effective, half-open intervals with explicit ClinicalTime precision. Physical mappings as well as clinical assignments retain revisions and audit. Final schema naming should consistently use effectiveFrom/effectiveTo for these intervals.

### Shared catalogue and reconciliation objects

| Object | Required fields beyond shared fields | Rules |
| --- | --- | --- |
| ReferenceConcept | `domain`, `system`, `code`, `display`, `active: Boolean`; optional `terminologyVersion` | Unique domain/system/code/terminologyVersion, with explicit uniqueness for unversioned entries. Domains are defined in the foundational model. |
| ConceptAlias | `conceptId`, `alias`, `normalizedAlias`, `language` | Alias need not be globally unique; ambiguous matches require review. |
| EntityMergeDecision | `entityType`, `sourceId`, `survivingId`, `reason`, `reviewerPersonId`, `reviewedAt`, `operationId` | Allowlisted master types, same owner, compatible hospital where applicable, no cycles; preserve original references and historical snapshots. |

Concept IDs must match their expected domain. An unresolved drug/test/person name remains source text or draft; it must not silently introduce a new trusted master record. Display names may change without changing IDs. Hospital code, department code within hospital, team code within hospital and unit code within department have scoped uniqueness. PhysicalLocation follows the sibling uniqueness and incomplete-hierarchy rules in the foundational model.

## 2. Account, people, teams and hospital location

| Object | Fields beyond the shared fields | Relationships and rules |
| --- | --- | --- |
| Account | `authSubject: String`, `clinicianPersonId: PersonId`, `displayName: String` | Unique authSubject; maps authenticated identity to local owner. External colleagues are not accounts. |
| Person | `displayName: String`, `identityState: PROVISIONAL/CONFIRMED/REPORTED` | Shared human identity for patient and professional profiles; name is not unique. |
| ProfessionalRole | `personId`, `professionCode`, `validFrom: ClinicalTime`, `validTo?: ClinicalTime`, `specialtyConceptId?`, `registrationIssuer?: String`, `registrationNumber?: String` | Doctor/nurse/assistant typed roles; no duplicated person demographics. |
| PersonContact | `personId`, `kind: PHONE/EMAIL/OTHER`, `value`, `status: ACTIVE/INACTIVE`, `label?: String`, `verifiedAt?: Instant` | Multiple contacts; contact is not an identity key. |
| Hospital | `code`, `name: String`, `timeZoneId: String`, `status: ACTIVE/INACTIVE` | Local hospital catalogue; identifiers scoped to this issuer. |
| Department | `hospitalId`, `code`, `name`, `status`, `parentDepartmentId?`, `specialtyConceptId?` | Same-hospital acyclic organizational hierarchy; independent of wards. |
| ClinicalUnit | `departmentId`, `code`, `name`, `status` | Organizational unit, distinct from a physical ICU/ward. |
| HospitalAffiliation | `professionalRoleId`, `hospitalId`, `departmentId?`, `validFrom: ClinicalTime`, `validTo?: ClinicalTime`, `localStaffIdentifier?: String` | Professional affiliation, not patient responsibility. |
| ClinicalTeam | `hospitalId`, `code`, `name`, `status`, `departmentId?`, `clinicalUnitId?` | Independent of physical ward. Lead is a dated membership role. |
| TeamMembership | `teamId`, `professionalRoleId`, `roleCode`, `validFrom: ClinicalTime`, `validTo?: ClinicalTime` | Historical membership retained; end cannot precede known start. |
| PhysicalLocation | `hospitalId`, `parentLocationId?: LocationId`, `kind: BUILDING/FLOOR/WARD/NURSING_STATION/ROOM/BED`, `code`, `label: String`, `status: ACTIVE/INACTIVE`, `structureState: COMPLETE/INCOMPLETE` | Typed views and containment rules in foundational model; operational status does not imply occupancy. |
| DepartmentLocationUse | `departmentId`, `locationId`, `validFrom: ClinicalTime`, `validTo?: ClinicalTime`, `purpose?: String` | Many departments may use one ward; never assigns all its patients to a department. |
| StationCoverage | `stationId: LocationId`, `coveredLocationId: LocationId`, `startsAt: ClinicalTime`, `endsAt?: ClinicalTime` | Station must be NURSING_STATION; coverage can span rooms/beds without making the station their physical parent. |

## 3. Identity, admission and involvement

| Object | Fields | Relationships and rules |
| --- | --- | --- |
| Patient | `personId`, `birthDate?: Date`, `reportedAge?: Decimal`, `ageUnit?: YEARS/MONTHS/DAYS`, `ageAsOf?: Date`, `sexConceptId?` | Unique personId per owner. Name and identity state come from Person. Age requires unit/reference date or remains source text. Admission state does not live on Patient. |
| PatientIdentifier | `patientId`, `hospitalId`, `system: String`, `value: String`, `status: CURRENT/PREVIOUS/DISPUTED`, `sourceItemId?` | Unique non-disputed normalized hospital/system/value per account. Conflicts require review. Former IDs remain searchable. |
| HospitalEpisode | `patientId`, `hospitalId`, `kind: ER/INPATIENT`, `externalId?: String`, `status: ACTIVE/DISCHARGED/CANCELLED`, `startedAt: ClinicalTime`, `endedAt?: ClinicalTime`, `eventId` | This is the Admission contract's persisted episode object; `admissionId` elsewhere means an INPATIENT episode ID. One active inpatient episode per patient in the first-release workspace. ER and inpatient may coexist as linked episodes. |
| EpisodeLink | `fromEpisodeId`, `toEpisodeId`, `relationship: ER_TO_INPATIENT/RELATED`, `eventId` | Same patient; no self-link. Link does not copy orders or erase the ER episode. |
| DepartmentAssignment | `admissionId`, `departmentId`, `role: PRIMARY/CONSULTING`, `startsAt: ClinicalTime`, `endsAt?: ClinicalTime`, `eventId` | One current primary department, multiple consulting departments; separate from bed/ward assignment. |
| CareInvolvement | `admissionId`, `personId`, `teamId?`, `role: PRIMARY_TEAM/REFERRAL/ON_CALL`, `reason?: String`, `status: ACTIVE/ENDED`, `startsAt: ClinicalTime`, `endsAt?: ClinicalTime`, `eventId` | Multiple roles allowed. Ending this does not discharge the admission. Active census is derived from this doctor's active involvements. |
| AdmissionTeamAssignment | `admissionId`, `teamId`, `consultantPersonId?`, `role: PRIMARY/CONSULTING`, `startsAt: ClinicalTime`, `endsAt?: ClinicalTime`, `eventId` | One current primary assignment; consulting assignments can overlap. Historical attribution remains intact. |
| LocationAssignment | `admissionId`, `locationId`, `stationId?: LocationId`, `startsAt: ClinicalTime`, `endsAt?: ClinicalTime`, `verification`, `eventId` | One current location assignment per admission. Transfers end old and create new atomically. Conflicting confirmed current bed occupants require resolution; old unverified hints are not occupancy records. |
| NursingAssignment | `admissionId`, `personId?: PersonId`, `professionalRoleId?`, `stationId?: LocationId`, `role: BEDSIDE/IN_CHARGE`, `startsAt: ClinicalTime`, `endsAt?: ClinicalTime`, `eventId` | At least person or station required. If role supplied it must belong to person and be a nurse role. Shift changes preserve old assignments and do not automatically change task owners. |

An unresolved bed-only request is `UnassignedIntake`, not a fabricated Patient/Admission. Prior hospital history can be retained without forcing unknown admissions into artificially precise episodes.

## 4. Source material, provenance and audit

| Object | Fields | Relationships and rules |
| --- | --- | --- |
| SourceItem | `kind: TYPED_NOTE/DICTATION/SHARED_TEXT/CHAT_SCREENSHOT/DOCUMENT/REPORTED_CALL`, `text?: String`, `documentId?`, `sourceAuthorId?: PersonId`, `sourceAuthorLabel?: String`, `sourceTime: ClinicalTime`, `capturedAt: Instant`, `replyToSourceId?`, `importBatchId: UUID`, `contentHash: String` | At least text or document. Hash detects candidates, not automatic clinical equivalence. Reply relations cannot cycle. Quoted author remains separate from forwarding person. |
| SourceAttribution | `sourceItemId`, `personId?`, `label?: String`, `role: AUTHOR/SENDER/FORWARDER/QUOTED_AUTHOR`, `spanStart?: Int`, `spanEnd?: Int` | Preserves multiple speakers within an imported transcript. Unknown identities stay labels. |
| Document | `mimeType`, `originalName`, `encryptedStorageKey`, `sha256`, `byteSize: Long`, `category: LAB_REPORT/WRITTEN_IMAGING_REPORT/RAW_IMAGING/ECG_TRACE/NOTE/CHAT_SCREENSHOT/OTHER`, `importedAt: Instant`, `pageCount?: Int`, `supersedesDocumentId?` | Original immutable bytes; path is internal, not a permanent public URI. MIME and content validated on import. |
| ClinicalEvent | `patientId`, `admissionId?`, `eventType: String`, `effectiveTime: ClinicalTime`, `recordedAt: Instant`, `recorderPersonId`, `verification`, `recordState`, `operationId: UUID`, `supersedesEventId?`, `correctionReason?: String` | Admission required for inpatient events, optional for longitudinal history/allergy. Event types use a versioned catalogue. Correction requires reason and previous-event link. |
| EventParticipant | `eventId`, `personId`, `role: DECISION_MAKER/AUTHOR/PERFORMER/APPROVER/REPORTER/RECIPIENT/CONSULTANT` | Unique event/person/role. Roles are snapshots of participation. |
| EventEvidence | `eventId`, `sourceItemId`, `pageIndex?: Int`, `textStart?: Int`, `textEnd?: Int`, `region?: NormalizedRectangle` | Source-page/region or text offsets anchor extracted claims. Validate bounds. |
| RecordRevision | `recordType`, `recordId`, `recordVersion: Int`, `schemaVersion: Int`, `payloadSnapshot: JSON`, `eventId` | Immutable prior clinical payload; unique record/type/version. JSON is audit storage, not the live clinical query model. |
| AuditEntry | `operationId`, `actorPersonId`, `action`, `targetType`, `targetId`, `priorVersion?: Int`, `newVersion: Int`, `recordedAt: Instant`, `reason?: String` | Append-only; saved in same transaction as affected records. |
| AppliedOperation | `operationId`, `requestHash`, `committedAt: Instant`, `resultReferences: JSON` | Unique owner/operationId. Identical retry returns original receipt; different payload under same ID is rejected. |

`ClinicalTime` and `NormalizedRectangle` are embedded value objects, not separate tables. A generic type/id reference in audit or operation receipts must resolve through a controlled object-type registry. Live care relationships use explicit foreign keys.

## 5. Clinical reviews, problems and plans

| Object | Fields | Relationships and rules |
| --- | --- | --- |
| Encounter | `admissionId`, `kind: ROUND/REFERRAL_REVIEW/ON_CALL_REVIEW/OTHER`, `reviewedAt: ClinicalTime`, `summary: String`, `eventId` | Actual review, not a future appointment. Participants in EventParticipant. |
| ClinicalNote | `patientId`, `admissionId?`, `encounterId?`, `kind: PROGRESS/HISTORY/HANDOVER_NOTE/OTHER`, `text: String`, `eventId` | Free text supplements typed facts, does not silently replace them. |
| Problem | `patientId`, `admissionId?`, `description`, `codeSystem?: String`, `code?: String`, `certainty: SUSPECTED/CONFIRMED`, `status: ACTIVE/RESOLVED`, `onset: ClinicalTime`, `resolvedAt?: ClinicalTime`, `eventId` | Code requires codeSystem. Changing certainty/status creates revision. |
| Allergy | `patientId`, `substance`, `reaction?: String`, `severity?: String`, `status: ACTIVE/INACTIVE`, `eventId` | Separate from AllergyAssessment. |
| AllergyAssessment | `patientId`, `result: UNKNOWN/NONE_KNOWN/KNOWN_ALLERGIES`, `eventId` | No allergy rows is not equivalent to NONE_KNOWN; retain dated assessments. |
| ClinicalDecision | `admissionId`, `encounterId?`, `description`, `rationale?: String`, `decidedAt: ClinicalTime`, `status: ACTIVE/SUPERSEDED/RETRACTED`, `eventId` | Decision-maker via EventParticipant; unknown allowed, not defaulted to recorder. |
| DecisionProblemLink | `decisionId`, `problemId` | Same patient, unique pair. |
| CarePlanRevision | `admissionId`, `decisionId?`, `goals: String`, `instructions: String`, `effectiveAt: ClinicalTime`, `previousRevisionId?`, `eventId` | Append-only plan version; current plan is the latest applicable non-retracted revision, not latest arrival time. |
| MedicationHistoryItem | `patientId`, `admissionId?`, `drugText`, `regimenText?: String`, `useStatus: REPORTED_CURRENT/PAST/UNKNOWN`, `eventId` | A home/history medicine list; does not create an inpatient order or administration. |

## 6. Medication and procedure objects

| Object | Fields | Relationships and rules |
| --- | --- | --- |
| MedicationDefinition | `displayName`, `ingredient?: String`, `strengthValue?: Decimal`, `strengthUnit?: String`, `form?: String`, `codeSystem?: String`, `code?: String` | Catalogue entry, not patient-specific care. Uncertain names remain unresolved proposals or raw history. |
| MedicationOrder | `admissionId`, `medicationId`, `decisionId?`, `prescriberId?: PersonId`, `orderedAt: ClinicalTime`, `doseValue?: Decimal`, `doseUnit?: String`, `doseText?: String`, `route?: String`, `schedule: Regimen`, `startsAt: ClinicalTime`, `endsAt?: ClinicalTime`, `status: ACTIVE/HELD/STOPPED/COMPLETED`, `eventId` | May preserve incomplete reported order with explicit missing fields; incomplete regimen cannot generate administration schedules or be presented as fully specified. |
| MedicationOrderEvent | `orderId`, `action: START/CHANGE/HOLD/RESUME/STOP/COMPLETE`, `reason?: String`, `previousOrderVersion?: Int`, `newOrderVersion: Int`, `eventId` | Transition legality checked. State update, revision and event saved atomically. |
| MedicationAdministration | `admissionId`, `orderId?`, `medicationId`, `outcome: GIVEN/OMITTED/REFUSED`, `doseValue?: Decimal`, `doseUnit?: String`, `route?: String`, `administeredAt: ClinicalTime`, `performerId?: PersonId`, `reason?: String`, `eventId` | Reported incomplete dose retained as incomplete; cannot imply dose verified. Link to order must match admission/drug. OMITTED/REFUSED require reason or explicitly unknown reason. |
| ProcedureOrder | `admissionId`, `decisionId?`, `procedureName`, `requesterId?: PersonId`, `requestedAt: ClinicalTime`, `plannedAt?: ClinicalTime`, `status: REQUESTED/SCHEDULED/CANCELLED/COMPLETED`, `eventId` | Completion requires linked performed event, not reminder completion. |
| ProcedureEvent | `admissionId`, `orderId?`, `procedureName`, `outcome: PERFORMED/ABORTED`, `performedAt: ClinicalTime`, `findings?: String`, `eventId` | Performers use EventParticipant; ad hoc procedure need not invent an order. |

`Regimen` is a versioned embedded object: `kind: FIXED_TIMES/INTERVAL/AS_NEEDED/TEXT_ONLY`, `originalText`, `times?: LocalTime[]`, `intervalMinutes?: Int`, `condition?: String`, `maximumDoseText?: String`, `zoneId?: String`. Fixed times require times/zone; interval requires positive interval. TEXT_ONLY cannot be silently normalized into a precise schedule. Medication notification generation is a separate explicit action, not automatic for every order.

## 7. Investigations and reports

| Object | Fields | Relationships and rules |
| --- | --- | --- |
| InvestigationOrder | `admissionId`, `decisionId?`, `kind: LAB/IMAGING/OTHER`, `testName`, `requesterId?: PersonId`, `requestedAt: ClinicalTime`, `status: REQUESTED/IN_PROGRESS/CANCELLED/RESULT_AVAILABLE`, `eventId` | One order per independently trackable test/study; grouping optional via shared requestGroupId UUID. |
| Specimen | `admissionId`, `type: String`, `accession?: String`, `collectedAt: ClinicalTime`, `collectorId?: PersonId`, `eventId` | Collection does not imply report availability. |
| OrderSpecimenLink | `orderId`, `specimenId` | Many-to-many where tests share a sample. |
| ImagingStudy | `admissionId`, `orderId?`, `modality`, `bodySite?: String`, `accession?: String`, `performedAt: ClinicalTime`, `eventId` | A study is separate from its interpretation/report. |
| DiagnosticReport | `admissionId`, `kind: LAB/WRITTEN_IMAGING/OTHER`, `issuer?: String`, `reportedAt: ClinicalTime`, `status: PRELIMINARY/FINAL/AMENDED`, `previousReportId?`, `imagingStudyId?`, `narrative?: String`, `eventId` | AMENDED requires predecessor or explicit missing-predecessor note. Original reports remain immutable. |
| ReportOrderLink | `reportId`, `orderId` | Report can cover multiple orders or no known order; same admission required. |
| ReportDocumentLink | `reportId`, `documentId`, `sequence: Int` | Multi-page/multi-file report with stable ordering. |
| Observation | `patientId`, `admissionId?`, `reportId?`, `specimenId?`, `encounterId?`, `name`, `valueKind: NUMBER/TEXT/BOOLEAN`, `numericValue?: Decimal`, `textValue?: String`, `booleanValue?: Boolean`, `unit?: String`, `referenceRangeText?: String`, `observedAt: ClinicalTime`, `eventId` | Exactly one typed value. Preserve original value/unit in evidence; never silently convert units or fabricate reference ranges. Bedside observations need no report. |
| ReportReview | `reportId`, `reviewerPersonId`, `reviewedAt: Instant`, `outcome: NO_CHANGE/DECISION_RECORDED/FOLLOW_UP_REQUIRED`, `decisionId?`, `note?: String`, `eventId` | Review applies to that exact report version; amendment creates fresh pending review. |

## 8. Referrals, questions and communication

| Object | Fields | Relationships and rules |
| --- | --- | --- |
| Referral | `admissionId`, `direction: INCOMING/OUTGOING`, `requesterPersonId?`, `requesterTeamId?`, `requestedPersonId?`, `requestedTeamId?`, `requestedSpecialty?: String`, `reason`, `requestedAt: ClinicalTime`, `priority: ROUTINE/URGENT/UNSPECIFIED`, `status: OPEN/CLOSED/CANCELLED`, `eventId` | At least requested person/team/specialty. Direction is relative to owner doctor's involvement; urgency only explicitly supplied/approved. |
| ReferralMilestone | `referralId`, `kind: ACCEPTED/PATIENT_SEEN/ADVICE_RECEIVED/CLOSED/CANCELLED`, `encounterId?`, `note?: String`, `eventId` | Occurrence can arrive out of order. PATIENT_SEEN requires encounter or explicit reported evidence; does not invent an acceptance. |
| ConsultationAdvice | `referralId?`, `admissionId`, `advisorPersonId?`, `text`, `advisedAt: ClinicalTime`, `disposition: UNREVIEWED/ACCEPTED/REJECTED/DEFERRED`, `reviewedBy?: PersonId`, `reviewedAt?: Instant`, `dispositionReason?: String`, `eventId` | Review fields required after UNREVIEWED; rejected/deferred requires reason. No automatic order change. |
| AdviceDecisionLink | `adviceId`, `decisionId` | Accepted advice can create several decisions, or several advice entries inform one decision. |
| ClinicalQuestion | `admissionId`, `askerPersonId?`, `text`, `askedAt: ClinicalTime`, `status: OPEN/ANSWERED/CLOSED`, `eventId` | Reopening appends revision/event. |
| QuestionResponse | `questionId`, `responderPersonId?`, `text`, `decisionId?`, `answeredAt: ClinicalTime`, `eventId` | Multiple responses retained; resulting tasks are independent. |
| CommunicationEvent | `admissionId`, `senderPersonId?`, `channel: VERBAL/CALL/MESSAGE/OTHER`, `state: CONVEYED/ACKNOWLEDGED`, `communicatedAt: ClinicalTime`, `contentSummary`, `precedingCommunicationId?`, `eventId` | ACKNOWLEDGED links prior communication where known; sending/exporting alone cannot create it. |
| CommunicationRecipient | `communicationId`, `personId?`, `teamId?`, `stationId?`, `reportedLabel?: String` | Exactly one target or unknown-name label per row; at least one row per communication. |
| CommunicationSubject | `communicationId`, `referralId?`, `questionId?`, `decisionId?`, `medicationOrderId?`, `careTaskId?` | Exactly one subject per row; multiple rows allowed and must match admission. |

Advice, question and referral replace the earlier loosely defined combined `Consultation / Question` placeholder. A consultation's actual patient review is an Encounter; there is no competing second visit history.

## 9. Work, reminders and responses

| Object | Fields | Relationships and rules |
| --- | --- | --- |
| CareTask | `admissionId`, `kind: REVIEW/CONSULT/INVESTIGATION/RESULT_REVIEW/COMMUNICATION/TREATMENT/OTHER`, `title`, `instructions?: String`, `status: PENDING/IN_PROGRESS/COMPLETED/CANCELLED`, `priority: ROUTINE/URGENT/UNSPECIFIED`, `followUpOwnerPersonId`, `dueAt?: Instant`, `dueZoneId?: String`, `waitingReason?: String`, `completedAt?: ClinicalTime`, `eventId` | No dueAt means unscheduled, still visible. dueAt requires zone. Completed/cancelled requires TaskResponse. Follow-up owner is explicitly set, distinct from performer. |
| TaskAssignment | `taskId`, `personId?`, `teamId?`, `stationId?`, `startsAt: ClinicalTime`, `endsAt?: ClinicalTime`, `eventId` | Exactly one target per assignment. No current assignment means unassigned; one current performing assignment for first release. |
| TaskClinicalLink | `taskId`, `referralId?`, `questionId?`, `decisionId?`, `medicationOrderId?`, `procedureOrderId?`, `investigationOrderId?`, `reportId?` | Exactly one typed target per row; multiple rows allowed, same admission. |
| TaskDependency | `taskId`, `dependsOnTaskId`, `relationship: BLOCKS/INFORMS` | No self-link or cycles for BLOCKS. Completing predecessor never auto-completes dependent task. |
| TaskResponse | `taskId`, `action: NOTE/COMPLETE/COMPLETE_WITH_NOTE/RESCHEDULE/CANCEL/REOPEN`, `actorPersonId`, `note?: String`, `performedAt: ClinicalTime`, `oldDueAt?: Instant`, `newDueAt?: Instant`, `eventId`, `operationId` | NOTE requires note; RESCHEDULE requires explicit new time or explicit clear-due intent; CANCEL requires reason in note. Source action stored once per operation. |
| ResponseClinicalLink | `responseId`, `administrationId?`, `procedureEventId?`, `encounterId?`, `observationId?` | Exactly one typed target per row. Typed outcome plus response committed atomically. |
| ReminderSchedule | `careTaskId?`, `intakeFollowUpId?`, `triggerAt: Instant`, `zoneId`, `revision: Int`, `state: ACTIVE/FIRED/CANCELLED`, `precision: EXACT/INEXACT/UNAVAILABLE`, `platformRequestKey: String` | Exactly one target. Positive revision. Rescheduling invalidates old revision; old notifications cannot mutate latest task blindly. |
| NotificationAttempt | `scheduleId`, `scheduleRevision: Int`, `firedAt?: Instant`, `postAttemptedAt?: Instant`, `status: ATTEMPTED/ACCEPTED_BY_OS/BLOCKED/FAILED`, `reasonCode?: String`, `interactedAt?: Instant` | OS acceptance is not proof doctor saw notification. Dismissal has no task-completion effect. |
| SchedulingOutbox | `scheduleId`, `scheduleRevision`, `action: SCHEDULE/CANCEL`, `state: PENDING/APPLIED/FAILED`, `attemptCount: Int`, `lastErrorCode?: String`, `nextRetryAt?: Instant` | Created in local task transaction; platform scheduling reconciles outside DB transaction. Prevent false “scheduled” receipts. |

Recurring follow-up is explicitly expanded into separate task occurrences with separate completion; do not reset a completed task. First release can support one-off reminders while leaving repeat scheduling unavailable.

## 10. Inbox, chat and AI proposals

| Object | Fields | Relationships and rules |
| --- | --- | --- |
| UnassignedIntake | `summary`, `identityHints: JSON`, `status: UNRESOLVED/RESOLVED/DISMISSED`, `resolvedPatientId?`, `resolvedAdmissionId?`, `resolutionNote?: String` | Hints are unverified capture data; RESOLVED requires confirmed targets. Dismissal retains source and reason. |
| IntakeSourceLink | `intakeId`, `sourceItemId` | Multiple messages/images can describe one request. |
| IntakeFollowUp | `intakeId`, `title`, `status: PENDING/COMPLETED/CANCELLED`, `dueAt?: Instant`, `dueZoneId?: String`, `resolvedCareTaskId?` | Personal reminder, not a clinical task. On resolution, optionally convert into CareTask and invalidate original reminder atomically. |
| ConversationThread | `scope: GLOBAL/PATIENT/NEW_PATIENT_DRAFT`, `patientId?`, `admissionId?`, `title` | PATIENT requires both targets. GLOBAL cannot silently hold a hidden last-patient target. |
| ConversationItem | `threadId`, `sequence: Long`, `kind: USER_TEXT/ASSISTANT_TEXT/CLARIFICATION/PROPOSAL/RECEIPT`, `text?: String`, `sourceItemId?`, `proposalId?`, `operationId?` | Unique thread/sequence; kind determines payload. Source-linked original survives extraction. |
| ProcessingJob | `state: QUEUED/RUNNING/AWAITING_REVIEW/SUCCEEDED/FAILED/CANCELLED`, `inputRefs: JSON`, `inputDigest`, `route?: String`, `modelId?: String`, `promptVersion?: String`, `attemptCount`, `errorCode?: String` | Operational object; SUCCEEDED means processing succeeded, not clinical actions saved. |
| ProposedAction | `jobId?`, `threadId?`, `patientId?`, `admissionId?`, `actionType`, `payloadSchemaVersion`, `payload: JSON`, `expectedTargetVersions: JSON`, `status: DRAFT/NEEDS_CLARIFICATION/APPROVED/REJECTED/COMMITTED`, `reviewerId?`, `reviewedAt?: Instant`, `operationId?` | Typed allowlisted payload. Writes require resolved identity, explicit review when applicable, version validation and local command execution. Approval alone is not commitment. |
| ProposalEvidence | `proposalId`, `sourceItemId`, `pageIndex?: Int`, `textStart?: Int`, `textEnd?: Int` | Retain evidence for each action, not just whole conversation. |

JSON is appropriate for unverified intake hints, versioned transport proposals and job metadata. Confirmed medications, observations, tasks and identity must be written to their typed objects, not left as JSON blobs. Do not retain raw voice audio by default; confirmed transcript becomes SourceItem.

## 11. Handover and hospital-course drafts

| Object | Fields | Relationships and rules |
| --- | --- | --- |
| SummarySnapshot | `kind: HANDOVER/HOSPITAL_COURSE`, `state: DRAFT/REVIEWED/SUPERSEDED`, `generatedAt: Instant`, `asOf: Instant`, `reviewerId?`, `reviewedAt?: Instant`, `previousSnapshotId?` | Immutable after review; edits create a new version/snapshot. |
| SummaryPatientSection | `snapshotId`, `admissionId`, `sequence`, `identityLocationSnapshot: JSON`, `backgroundText`, `changesText`, `pendingText`, `uncertaintyText?: String` | Stores what was actually reviewed/shared, not only a query that later changes. |
| SummaryEvidence | `sectionId`, `eventId?`, `taskId?`, `reportId?`, `targetVersion: Int` | Exactly one source target per row; supports source-linked claims and stale-summary detection. |
| ExportEvent | `snapshotId`, `format: TEXT/PDF`, `preparedAt`, `shareLaunchedAt?: Instant`, `recipientLabel?: String`, `communicationEventId?` | Export is not delivery. PDF is optional output, not a required report-input format. |

Patient census, current location, pending work, referral progress, daily timeline and PatientMedicalRecord are derived query models. Do not create separately editable copies of those views. Any cache carries source versions and can be rebuilt.

## 12. Required transactional commands and indexes

Commands must perform validation, expected-version checks, record/event/revision/audit writes and operation receipt creation in one database transaction. Platform notifications and cloud requests are reconciled after commit.

Required commands: CreatePatientAndAdmission; ResolveIntake; RecordTransfer; StartOrEndInvolvement; RecordEncounter; RecordReferralMilestone; ReviewAdviceAndProposeActions; CommitClinicalDecision; ChangeMedicationOrder; RecordAdministration; AttachAndMatchReport; RecordReportReview; AssignTask; RespondToTask; RescheduleReminder; PrepareHandover; DischargeAdmission; CorrectClinicalRecord.

- Index active involvements by owner/person/status; active admissions by patient/status.
- Index identifiers by owner/hospital/system/normalized value, with reviewed conflict handling.
- Index location assignments by admission and effective interval; enforce one current confirmed assignment and conflicting bed occupancy checks.
- Index clinical events by owner/patient/admission/effective time and separately recordedAt for late-entry review.
- Index tasks by owner/status/dueAt/admission; referrals by admission/status; questions by admission/status.
- Index reports by admission/reportedAt and reviews by report/version; source hashes by owner/hash for duplicate candidates.
- Unique constraints for operation IDs, record revisions, thread sequence, and join-table target pairs.
- Restrict destructive cascades from patients/admissions to care history. Explicit retention/deletion is a separate workflow.
- Known interval ends cannot precede starts; partial dates require overlap/uncertainty handling, not fabricated comparisons. Patient/episode/source conflicts return reviewable errors without partial writes.

## 13. Migration and delivery boundaries

| Existing object | Destination | Preservation rule |
| --- | --- | --- |
| Patient | Patient + identifiers + reviewed episode grouping | Preserve legacy fields/source; global discharge flag is not permanent admission authority. |
| Visit | Encounter and ClinicalNote within migrated admission context | Unknown episode boundaries stay marked legacy/uncertain; do not invent exact admissions. |
| Medicine | MedicationHistoryItem or reported MedicationOrder following explicit migration policy | Never infer administration, prescriber, route or start date from a medicine row. |
| Task | CareTask + any known assignment | Preserve original status as legacy-reported; unknown completion time stays unknown. |
| Report | Document + DiagnosticReport only where category/context support it | Attachment time is not collection/report time; raw images are not written reports. |
| FollowUp | CareTask + ReminderSchedule + available historical response evidence | Preserve links and legacy notification IDs during reconciliation; avoid duplicate reminders. |

Before implementation, WF-01 must map each logical object to a Room table/value class, define enum serialization and migrations, and check relationship constraints against legacy fixtures. Later workflow tasks implement the relevant objects in slices; no requirement to build every table before basic manual workflows can be tested. No destructive fallback migration is permitted.

Schema acceptance must cover: bed reuse; UHID alias/conflict; referral closure without discharge; late and corrected events; shift reassignment; order versus administration; report amendments; repeated notification taps; stale proposals; duplicate imports; cross-account/admission link rejection; and migration preserving unknown information. This contract creates design requirements, not a claim that these objects already exist.
