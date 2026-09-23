# MedTrack inference contracts (v1)

Status: Frozen for HY-02. Command schema `care-commands-1`. API prefix `/v1`.

There is **no** cloud clinical commit endpoint. Approval and `CareWritePath` commits happen on the device.

Compatible evolution: additive optional fields are allowed. Removing or retyping a required field, or adding a new mutating command type, requires a new `commandSchemaVersion`. Unsupported versions are rejected with `UNSUPPORTED_SCHEMA`.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/capabilities` | Authorized routes, schema versions, limits, flags |
| POST | `/v1/artifacts` | Temporary private upload |
| DELETE | `/v1/artifacts/{artifactId}` | Delete owned temporary input |
| POST | `/v1/inference-jobs` | Submit capture/reasoning request |
| GET | `/v1/inference-jobs/{jobId}` | Owned job status or typed result |
| POST | `/v1/inference-jobs/{jobId}/cancel` | Cancel; suppress result use |

Identity is taken from the verified token. Body `accountId` / `userId` fields are ignored for authorization.

## Mutation targets

| Operation | Required target | Insufficient |
| --- | --- | --- |
| Transfer | `locationId` (stable ID) + expected location-assignment version | Bed/room **label** |
| Medication stop | `medicationOrderId` + expected order version | Drug **name** |
| Reminder / relative time | `anchor`, `resolvedAt`, `zoneId` | Bare “in four hours” |

A STAT investigation and a four-hour follow-up for the same focus fail consistency review unless they are explicitly sequenced with `dependsOn`.

## Fixtures

`fixtures/` covers success, clarification, stale context, partial input, timeout, unsupported version, wrong account, and duplicate request. Contract tests run without provider credentials.
