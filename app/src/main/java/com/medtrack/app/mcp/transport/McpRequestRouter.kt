package com.medtrack.app.mcp.transport

import com.medtrack.app.mcp.GetPatientCurrentProcessRequest
import com.medtrack.app.mcp.MedTrackMcpCatalog
import com.medtrack.app.mcp.MedTrackMcpService
import com.medtrack.app.mcp.UpdatePatientRoomRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class McpRequestRouter @Inject constructor(
    private val mcpService: MedTrackMcpService
) {
    fun handle(requestJson: JSONObject): McpProtocolResponse {
        val method = requestJson.optString("method")
        val id = requestJson.opt("id")
        val params = requestJson.optJSONObject("params") ?: JSONObject()

        if (requestJson.optString("jsonrpc") != "2.0") {
            return McpProtocolResponse.json(
                statusCode = 400,
                body = errorBody(id, -32600, "Invalid Request: jsonrpc must be '2.0'")
            )
        }

        if (method.isBlank()) {
            return McpProtocolResponse.json(
                statusCode = 400,
                body = errorBody(id, -32600, "Invalid Request: method is required")
            )
        }

        if (id == null || id == JSONObject.NULL) {
            handleNotification(method, params)
            return McpProtocolResponse.empty(202)
        }

        val result = when (method) {
            "initialize" -> successBody(id, initializeResult())
            "ping" -> successBody(id, JSONObject())
            "tools/list" -> successBody(id, listToolsResult())
            "tools/call" -> handleToolCall(id, params)
            else -> errorBody(id, -32601, "Method not found: $method")
        }

        return McpProtocolResponse.json(200, result)
    }

    private fun handleNotification(method: String, params: JSONObject) {
        when (method) {
            "notifications/initialized" -> Unit
            "notifications/cancelled" -> Unit
            else -> Unit
        }
    }

    private fun initializeResult(): JSONObject =
        JSONObject()
            .put("protocolVersion", MCP_PROTOCOL_VERSION)
            .put(
                "capabilities",
                JSONObject().put(
                    "tools",
                    JSONObject().put("listChanged", false)
                )
            )
            .put(
                "serverInfo",
                JSONObject()
                    .put("name", SERVER_NAME)
                    .put("version", SERVER_VERSION)
            )
            .put(
                "instructions",
                "Use tools/list to discover MedTrack tools, then tools/call to invoke them."
            )

    private fun listToolsResult(): JSONObject =
        JSONObject().put(
            "tools",
            JSONArray(mcpService.listTools().map { it.toToolJson() })
        )

    private fun handleToolCall(id: Any, params: JSONObject): JSONObject {
        val toolName = params.optString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()

        val result = when (toolName) {
            MedTrackMcpCatalog.UPDATE_PATIENT_ROOM -> runBlocking {
                mcpService.updatePatientRoom(
                    UpdatePatientRoomRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        currentRoomNumber = arg(arguments, "currentRoomNumber", "current_room_number"),
                        newRoomNumber = arg(arguments, "newRoomNumber", "new_room_number")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            MedTrackMcpCatalog.GET_PATIENT_CURRENT_PROCESS -> runBlocking {
                mcpService.getPatientCurrentProcess(
                    GetPatientCurrentProcessRequest(
                        patientName = arg(arguments, "patientName", "patient_name"),
                        roomNumber = arg(arguments, "roomNumber", "room_number")
                    )
                )
            }.let { response ->
                toolResult(
                    text = response.message,
                    structuredContent = response.toStructuredContent(),
                    isError = !response.success
                )
            }

            else -> return errorBody(id, -32602, "Unknown tool: $toolName")
        }

        return successBody(id, result)
    }

    private fun arg(arguments: JSONObject, camelCase: String, snakeCase: String): String =
        arguments.optStringOrNull(camelCase)
            ?: arguments.optStringOrNull(snakeCase)
            ?: ""

    private fun toolResult(
        text: String,
        structuredContent: JSONObject,
        isError: Boolean
    ): JSONObject =
        JSONObject()
            .put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", text)
                )
            )
            .put("structuredContent", structuredContent)
            .put("isError", isError)

    private fun successBody(id: Any, result: JSONObject): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("result", result)

    private fun errorBody(id: Any?, code: Int, message: String): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id ?: JSONObject.NULL)
            .put(
                "error",
                JSONObject()
                    .put("code", code)
                    .put("message", message)
            )

    companion object {
        const val MCP_PROTOCOL_VERSION = "2025-11-25"
        private const val SERVER_NAME = "medtrack-local-mcp"
        private const val SERVER_VERSION = "1.0.0"
    }
}
