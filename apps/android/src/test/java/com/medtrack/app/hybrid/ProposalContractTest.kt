package com.medtrack.app.hybrid.contract

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProposalContractTest {
    @Test
    fun transferRequiresLocationIdNotLabel() {
        val bundle = JSONObject(
            """
            {
              "commandSchemaVersion":"care-commands-1",
              "identity":{"state":"RESOLVED","patientId":"p","admissionId":"a"},
              "summary":"Transfer to ICU Bed 04.",
              "operations":[{
                "operationId":"op1","type":"TRANSFER",
                "target":{"kind":"LOCATION","id":null,"displayHint":"ICU Bed 04"},
                "fields":{"admissionId":"a"}
              }]
            }
            """.trimIndent()
        )
        try {
            ProposalContract.validateForMutation(bundle)
            fail("label-only transfer must fail")
        } catch (error: ProposalValidationException) {
            assertTrue(error.message!!.contains("locationId"))
        }
    }

    @Test
    fun statVersusFourHoursFails() {
        val bundle = JSONObject(
            """
            {
              "commandSchemaVersion":"care-commands-1",
              "identity":{"state":"RESOLVED","patientId":"p","admissionId":"a"},
              "summary":"STAT potassium and four hours later.",
              "operations":[
                {"operationId":"stat","type":"ASSIGN_TASK","dependsOn":[],
                 "target":{"kind":"ADMISSION","id":"a"},
                 "fields":{"kind":"INVESTIGATION","priority":"STAT","title":"Potassium","clinicalFocus":"potassium"}},
                {"operationId":"later","type":"ASSIGN_TASK","dependsOn":[],
                 "target":{"kind":"ADMISSION","id":"a"},
                 "fields":{"kind":"INVESTIGATION","priority":"ROUTINE","title":"Check potassium","clinicalFocus":"potassium"},
                 "effectiveTime":{"relative":{"amount":4,"unit":"HOURS","anchor":"t0","resolvedAt":"2026-09-20T12:00:00+05:30","zoneId":"Asia/Kolkata"}}}
              ]
            }
            """.trimIndent()
        )
        try {
            ProposalContract.validateForMutation(bundle)
            fail("inconsistent STAT vs delay must fail")
        } catch (error: ProposalValidationException) {
            assertTrue(error.message!!.contains("STAT"))
        }
    }

    @Test
    fun relativeTimeRequiresAnchor() {
        val bundle = JSONObject(
            """
            {
              "commandSchemaVersion":"care-commands-1",
              "identity":{"state":"RESOLVED","patientId":"p","admissionId":"a"},
              "summary":"Remind in four hours.",
              "operations":[{
                "operationId":"op1","type":"ASSIGN_TASK",
                "target":{"kind":"ADMISSION","id":"a"},
                "fields":{"title":"Check labs"},
                "effectiveTime":{"relative":{"amount":4,"unit":"HOURS"}}
              }]
            }
            """.trimIndent()
        )
        try {
            ProposalContract.validateForMutation(bundle)
            fail("relative time without anchor must fail")
        } catch (error: ProposalValidationException) {
            assertTrue(error.message!!.contains("anchor"))
        }
    }
}
