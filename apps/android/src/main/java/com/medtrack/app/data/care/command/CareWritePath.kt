package com.medtrack.app.data.care.command

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single write-path facade for forms / MCP / AI (PM-012).
 * All clinical mutations go through the typed command services below —
 * no weaker parallel schema.
 */
@Singleton
class CareWritePath @Inject constructor(
    val admissions: CareCommandService,
    val clinical: CareClinicalAssessmentService,
    val therapy: CareTherapyCommandService,
    val work: CareWorkCommandService
)
