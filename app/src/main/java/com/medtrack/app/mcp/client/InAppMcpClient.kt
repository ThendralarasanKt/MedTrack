package com.medtrack.app.mcp.client

import com.medtrack.app.mcp.transport.McpRequestRouter
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class InAppMcpClient @Inject constructor(
    private val router: McpRequestRouter
) {
    private var nextId = 1

    fun initialize(): JSONObject =
        sendRequest(
            method = "initialize",
            params = JSONObject()
                .put("protocolVersion", McpRequestRouter.MCP_PROTOCOL_VERSION)
                .put("capabilities", JSONObject())
                .put(
                    "clientInfo",
                    JSONObject()
                        .put("name", "medtrack-in-app-ai")
                        .put("version", "1.0.0")
                )
        )

    fun listTools(): JSONObject =
        sendRequest(
            method = "tools/list",
            params = JSONObject()
        )

    fun callTool(name: String, arguments: JSONObject): JSONObject =
        sendRequest(
            method = "tools/call",
            params = JSONObject()
                .put("name", name)
                .put("arguments", arguments)
        )

    private fun sendRequest(method: String, params: JSONObject): JSONObject {
        val request = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", nextId++)
            .put("method", method)
            .put("params", params)

        val response = router.handle(request)
        return response.body ?: JSONObject()
    }
}
