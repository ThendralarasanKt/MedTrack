# Cloud user profile, professional details and subscription access

Status: Implemented for UP-01 through UP-06; payment checkout remains deferred  
Date: 2026-09-20  
Scope: Ankita first, isolated personal accounts for additional users later

Related: [Hybrid architecture](hybrid-architecture.md), [information model](patient-information-model.md), [authentication completion requirements](authentication-and-care-review.md), [HY delivery tasks](../task/05-hybrid-architecture-implementation.md).

## 1. Product decision and authority

Every successfully authenticated user gets a persistent cloud identity and editable professional profile. The backend also stores whether that user's personal account has an active subscription or another explicit access grant. Android provides a My Profile screen and an encrypted local copy for offline display/edit drafts.

Cloud authority covers the user's profile, subscription and feature access. Clinical patient records and their write authority remain on the device. Synchronizing a doctor's profile does not enable patient-chart synchronization or shared hospital access.

Successful sign-in can create a profile without granting product access. For the first pilot only Ankita's provisioned account is enabled. Other signed-in identities can receive a profile with access pending/disabled; they cannot open Ankita's local records or submit inference. This refines the earlier provisioning design: profile bootstrap is allowed before product provisioning, protected care/AI capabilities are not.

Authentication identity, professional profile, account access status, subscription status and actual feature entitlements are separate concepts. Do not collapse them into a client-editable isSubscriber boolean.

## 2. Concrete cloud objects

IDs are stable opaque IDs. Mutable objects have server-issued `version`, `createdAt` and `updatedAt` timestamps. Optional fields use `?`. All writes are authenticated and scoped by server-resolved identity/account; the request body cannot choose another owner.

| Object | Fields | Ownership and constraints |
| --- | --- | --- |
| UserIdentity (existing) | `userId`, `authIssuer`, `authSubject`, `email?`, `emailVerified`, `status` | Unique issuer/subject. Identity email and verified status come from verified authentication, not editable profile text. Never merge accounts by email alone. |
| UserProfile | `profileId`, `userId`, `displayName`, `preferredName?`, `professionCode?`, `phone?`, `timeZoneId?`, `preferredLanguage?`, `onboardingStatus: NOT_STARTED/INCOMPLETE/COMPLETE` | One profile per user. Default display name may come from identity provider at creation; later sign-ins do not overwrite user edits. No patient information in this object. |
| SpecialtyConcept | `specialtyId`, `codeSystem`, `code`, `displayName`, `active` | Controlled reusable catalogue. Searchable by label/aliases. Catalogue administration separate from profile editing. |
| ProfileSpecialty | `id`, `profileId`, `specialtyId?`, `reportedSpecialtyText?`, `isPrimary`, `verificationStatus: SELF_REPORTED/VERIFIED` | Exactly one resolved specialty or unresolved text. Many specialties allowed, at most one primary. User edits cannot self-award VERIFIED. |
| HospitalDirectoryEntry | `hospitalId`, `name`, `city?`, `stateOrRegion?`, `countryCode?`, `address?`, `externalIdentifier?`, `status: ACTIVE/RETIRED` | Reusable directory concept, not a hospital tenant or patient-data access grant. Managed updates preserve stable identity. |
| ProfileHospitalAffiliation | `affiliationId`, `profileId`, `hospitalId?`, `reportedHospitalName?`, `reportedCity?`, `departmentName?`, `jobTitle?`, `startsOn?: Date`, `endsOn?: Date`, `isPrimary`, `verificationStatus: SELF_REPORTED/VERIFIED` | Exactly one directory hospital or private unresolved hospital name. Multiple current hospitals allowed; at most one primary active affiliation. End cannot precede known start. |
| PlanDefinition | `planId`, `code`, `displayName`, `active`, `planVersion`, `featurePolicy` | Server-controlled catalogue of features/quotas. No price or payment provider is selected in this spec. |
| AccountSubscription | `subscriptionId`, `accountId`, `planId`, `status: PENDING/TRIALING/ACTIVE/PAST_DUE/CANCELLED/EXPIRED`, `provider: NONE/MANUAL/EXTERNAL`, `providerCustomerRef?`, `providerSubscriptionRef?`, `periodStart?`, `periodEnd?`, `cancelAtPeriodEnd`, `lastVerifiedAt?` | Belongs to the personal account, not a hospital affiliation. One current subscription record per account initially, with history retained. Payment identifiers are server-only. |
| AccessGrant | `grantId`, `accountId`, `kind: PILOT/TRIAL/COMPLIMENTARY`, `featurePolicy`, `startsAt`, `endsAt?`, `status: ACTIVE/REVOKED`, `grantedBy`, `reason` | Enables a pilot without falsely marking a free user as a paying subscriber. Only authorized server administration can grant/revoke. |
| EntitlementSnapshot | `accountId`, `policyVersion`, `features`, `limits`, `effectiveAt`, `refreshAfter`, `accessState` | Server-derived from provisioning, subscription/grants and account policy. Client receives a read-only projection, never sets it. |
| ProfileAuditEntry | `id`, `userId`, `actorId`, `objectType`, `objectId`, `previousVersion?`, `newVersion`, `action`, `recordedAt` | Capture edits and administrative entitlement changes without logging credentials or entire clinical payloads. |

Future payment integration additionally needs an idempotent SubscriptionEvent ledger with provider/event ID uniqueness, event time, received time and verification outcome. Do not create a purchase flow until payment provider, prices, channel rules and lifecycle verification are specified separately.

## 3. Local objects and connection to clinical master data

| Local object | Fields/purpose |
| --- | --- |
| UserProfileCache | accountId/userId, latest server version, profile/specialty/affiliation snapshot, fetchedAt; encrypted |
| ProfileEditOutbox | operationId, accountId/userId, baseVersion, field patch, queuedAt, state QUEUED/SENDING/SAVED/CONFLICT/FAILED, lastErrorCode? |
| EntitlementCache | server snapshot, fetchedAt, stale flag; presentation only for cloud permissions |
| ProfileMasterLink | accountId, cloud object type/ID, local object type/ID, linkedAt, sourceVersion; unique mapping within owner |

The local clinician is the existing Person, with ProfessionalRole and HospitalAffiliation records. Link profile identity to that Person only after verified account binding. SpecialtyConcept maps to local ReferenceConcept; HospitalDirectoryEntry maps to local Hospital through ProfileMasterLink. Names alone do not authorize merges.

Cloud profile updates may propose updates to the doctor's local master details. Apply them through explicit validated local operations; do not rewrite old EventParticipant roles, hospital assignments or decision attribution. A doctor changing their primary hospital does not move their patients, change admissions or alter existing reminders.

An unlisted hospital/specialty remains private reported text until a catalogue match is reviewed. Do not publish personal affiliation data or add arbitrary user text to a shared directory automatically. Profile fields are self-reported professional context, not credential verification or hospital-system authorization.

## 4. Bootstrap and API contract

| Endpoint | Behaviour |
| --- | --- |
| `POST /v1/me/bootstrap` | Verify identity cryptographically; idempotently create/retrieve UserIdentity, UserProfile and personal account/owner membership. Return onboarding, provisioning state and profile version. No clinical database access is granted by creation alone. |
| `GET /v1/me/profile` | Return own profile with specialties, hospital affiliations and version. |
| `PATCH /v1/me/profile` | Apply allowlisted profile fields and relationship edits with expectedVersion and operationId; validate atomically. |
| `GET /v1/me/access` | Return safe subscription summary, grants summary and entitlement snapshot. No secrets or payment-provider internal identifiers. |
| `GET /v1/catalogues/specialties` | Paginated/searchable specialty options. |
| `GET /v1/catalogues/hospitals` | Paginated/searchable hospital directory; returns no other users' affiliations. |

Bootstrap runs only after verified Firebase identity; an expired or forged token creates nothing. Repeating it cannot create multiple profiles/accounts. A blocked account must not bypass its block by re-running bootstrap. Profile edits reject ownership, subscription, verification, grant and entitlement fields.

Offline edits retain the last confirmed server copy and queue the doctor's patch. Show “Saved on this device; waiting to sync.” On version conflict, show the changed fields for reconciliation; do not silently replace the server profile. Retries are idempotent. Outbox jobs never migrate to another signed-in account.

If cloud bootstrap is unavailable on first sign-in, retain a draft and report pending setup; do not manufacture a locally authorized owner. Previously verified users can view cached profiles under the existing local unlock policy.

## 5. My Profile screen

Provide a profile/avatar entry from the workspace and a clearly labelled My Profile screen. Avatar image upload is optional later; initials suffice initially.

Sections:

1. **Identity:** preferred/display name, signed-in email (read-only), profession. Explain identity-provider email changes separately from profile edits.
2. **Professional details:** primary specialty, optional additional specialties, hospital affiliations, primary hospital, department and job title. Search existing catalogues; permit “Not listed” with explicit self-reported fields.
3. **Preferences:** language and time zone. Changes do not silently reinterpret previously scheduled clinical times.
4. **Membership:** actual plan/grant label, subscription state, access end/renewal date if known, and available features. Distinguish “Pilot access” from “Paid subscription.”
5. **Account:** sync status, sign out and recovery/help entry. Follow the existing lock/ownership requirements; sign-out must not erase local patient history.

After first bootstrap invite the doctor to complete professional details. Name can default from Google; specialty and hospital are editable and can be marked “Not provided yet.” These missing fields do not lock an already authorized doctor out of patient care. Changing them does not prove qualifications or confer access to hospital colleagues' data.

Loading, empty, stale/offline, saved, conflict and failure states must be explicit. Hide purchase/upgrade actions until a functioning payment flow exists; never show a success state based solely on a button press.

## 6. Subscription and access policy

First-release decision: provision Ankita's permitted account and issue an explicit PILOT AccessGrant. No payment processor is needed for this grant. Its scope/duration is configured administratively, not hard-coded to the name Ankita. Do not invent a paid plan or payment confirmation.

Subscription and entitlement rules:

- ACTIVE/TRIALING requires server-verified eligibility within its effective interval. PENDING is not paid access. PAST_DUE follows an explicit server grace policy; absent such a policy it grants no additional cloud access.
- A cancelled renewal can leave access active through a paid-through period; cancellation, entitlement expiry and account suspension are distinct.
- Grants and subscriptions may both exist; deterministic server policy computes the effective features and limits. Account disablement takes precedence over grants.
- Every chargeable cloud job checks authorization and current entitlement server-side. Manipulating the device cache cannot enable paid features or replenish quotas.
- Offline UI may show the last-known state with its timestamp. Paid cloud operations wait for server verification; no recurring online check is needed to complete a locally saved bedside task.
- Subscription lapse does not delete or hide existing clinical records, cancel already saved reminders, block local completion/notes or prevent export/recovery. Cloud AI/new paid services can be restricted according to policy.
- Subscription expiry does not make an unauthorized identity the owner of the local database. Access control and subscription restrictions remain separate.

Exact prices, quotas, trial duration, renewal policy and purchase provider remain undecided. They must be configured and documented before selling subscriptions, without redesigning profile identity.

## 7. Isolation, lifecycle and scope

Profiles and affiliations are private to their user and authorized backend administration. Other users may search public/reference hospital names but cannot list staff profiles or infer membership. Choosing a hospital does not create a shared workspace.

Apply authenticated ownership checks to every API and record query; client security rules alone do not protect server-admin SDK access. Profile cache/outbox is encrypted and account-bound. Never send profile fields to an LLM unless needed for a specific permitted request.

Profile/account information has a separately defined account-retention policy; it is not deleted by the temporary AI-artifact cleanup. Account deletion and any required billing retention need their own explicit export/deletion design. Ordinary profile edits, sign-out and subscription lapse never trigger patient-data deletion.

## 8. Delivery and acceptance

| Slice | Deliverable | Mapping |
| --- | --- | --- |
| UP-01 | Cloud schema, private profile bootstrap and identity/account mapping | HY-02/HY-03; prerequisite authentication fixes R-01/R-02 |
| UP-02 | Profile/catalogue APIs, versioned edits, audit and cross-user tests | HY-03/HY-12 |
| UP-03 | Encrypted cache/outbox, My Profile UI and onboarding | HY-04/HY-06 |
| UP-04 | Local clinician/specialty/hospital mappings with historical preservation | Existing information model and local command path |
| UP-05 | Plan/subscription/grant model, entitlement evaluation and pilot administration | HY-03/HY-08/HY-12 |
| UP-06 | End-to-end profile, account-switch, offline and entitlement verification | HY-13 |

UP-01 through UP-06 are implemented in the gateway and Android client. Payment checkout/webhooks remain deferred until a provider-specific spec exists.

Acceptance scenarios:

1. Repeated sign-in creates one cloud profile for the verified identity and restores it independently of patient records.
2. Unprovisioned user can have a profile but cannot access Ankita's data or inference entitlement.
3. Doctor saves multiple specialties/hospitals with one primary each; invalid references and impossible date ranges fail without partial saves.
4. Reopening/reinstalling retrieves profile after authentication, without claiming patient records were restored.
5. Offline edit survives restart, retains account binding, syncs once, and exposes concurrent-edit conflicts.
6. User cannot change another user's profile or write isSubscriber, grant, verification or entitlement state through profile APIs.
7. Ankita sees Pilot access; a future verified paid account shows its actual subscription; cached status manipulation cannot authorize inference.
8. Expiry or cancellation changes cloud entitlement correctly while existing local chart/history/reminders remain usable for the authorized owner.
9. Primary-hospital change preserves old care attribution, patient locations and scheduled times.
10. Bootstrap, edits and server grants have idempotency/audit evidence; existing authentication blockers are resolved before real-user rollout.
