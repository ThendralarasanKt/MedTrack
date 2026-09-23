package com.medtrack.app.mcp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedTrackMcpCatalogTest {
    @Test
    fun mutatingToolsIncludeWritesAndExcludeReads() {
        assertTrue(MedTrackMcpCatalog.isMutatingTool(MedTrackMcpCatalog.CREATE_PATIENT))
        assertTrue(MedTrackMcpCatalog.isMutatingTool(MedTrackMcpCatalog.CREATE_PATIENT_VISIT))
        assertTrue(MedTrackMcpCatalog.isMutatingTool(MedTrackMcpCatalog.ADD_OR_UPDATE_MEDICINE))
        assertTrue(MedTrackMcpCatalog.isMutatingTool(MedTrackMcpCatalog.DISCHARGE_PATIENT))
        assertTrue(MedTrackMcpCatalog.isMutatingTool(MedTrackMcpCatalog.UPDATE_PATIENT_ROOM))
        assertFalse(MedTrackMcpCatalog.isMutatingTool(MedTrackMcpCatalog.SEARCH_PATIENT))
        assertFalse(MedTrackMcpCatalog.isMutatingTool(MedTrackMcpCatalog.GET_PATIENT_MEDICINES))
        assertFalse(MedTrackMcpCatalog.isMutatingTool(MedTrackMcpCatalog.GET_PATIENT_CURRENT_PROCESS))
        assertFalse(MedTrackMcpCatalog.isMutatingTool(MedTrackMcpCatalog.GET_PATIENT_TASKS))
    }
}
