package com.medtrack.app.data.care.command

class StaleRecordException(
    val targetType: String,
    val targetId: String,
    val expectedVersion: Int,
    val actualVersion: Int
) : IllegalStateException(
    "Stale $targetType $targetId: expected version $expectedVersion, found $actualVersion"
)
