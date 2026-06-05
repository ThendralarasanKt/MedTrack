package com.medtrack.app.ai.openrouter

import com.medtrack.app.mcp.McpToolDescriptor
import com.medtrack.app.mcp.McpToolField
import org.json.JSONArray
import org.json.JSONObject

fun List<McpToolDescriptor>.toOpenRouterTools(): List<OpenRouterTool> =
    map { descriptor -> descriptor.toOpenRouterTool() }

fun McpToolDescriptor.toOpenRouterTool(): OpenRouterTool =
    OpenRouterTool(
        name = name,
        description = buildString {
            append(description)
            if (useWhen.isNotBlank()) {
                append(" Use when: ")
                append(useWhen)
            }
        },
        parameters = toOpenRouterParametersJson()
    )

private fun McpToolDescriptor.toOpenRouterParametersJson(): JSONObject {
    val properties = JSONObject()
    val required = JSONArray()

    inputFields.forEach { field ->
        properties.put(field.name, field.toOpenRouterFieldJson())
        if (field.required) {
            required.put(field.name)
        }
    }

    return JSONObject()
        .put("type", "object")
        .put("properties", properties)
        .put("required", required)
        .put("additionalProperties", false)
}

private fun McpToolField.toOpenRouterFieldJson(): JSONObject =
    JSONObject()
        .put("type", type)
        .put("description", description)
