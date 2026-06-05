# OpenRouter Free Model MCP Plan

## Status

Discussion draft. This plan matches the current target:

- Remove the slow offline llama.cpp model integration.
- Remove `FakeLocalLlmEngine` and the local `LocalLlmEngine` abstraction.
- Make OpenRouter the only AI provider used by the app.
- Keep the normal MedTrack app fully usable without internet.
- Require internet only when the user opens and uses the AI assistant.
- Send the user's message and available MCP tool definitions to an OpenRouter
  free model.
- Let the model select an MCP tool and provide its arguments.
- Execute the selected tool locally through the existing MCP layer.

## Target User Flow

```text
1. User opens the AI screen.
2. User types a natural-language request.
3. App checks internet and OpenRouter authentication.
4. App gets the current MCP tool definitions.
5. App sends the user message and tool definitions to OpenRouter.
6. OpenRouter free model returns either:
   - A normal text answer, or
   - A tool call with tool name and arguments.
7. App validates the tool call locally.
8. App executes the tool through InAppMcpClient.
9. MCP service performs the action through repositories and Room.
10. App shows the result to the user.
```

Example:

```text
User:
Add a visit for Ramesh in room 132 with blood test and vitamin C tablet.

OpenRouter model:
create_patient_visit(patientName="Ramesh", roomNumber="132", ...)

App:
InAppMcpClient.callTool(...)

MCP:
McpRequestRouter -> MedTrackMcpService -> Repository -> Room
```

## Recommended Architecture

```text
AssistantScreen
  -> AssistantViewModel
  -> AiAssistantOrchestrator
      -> OpenRouterAiClient
      -> InAppMcpClient
          -> McpRequestRouter
          -> MedTrackMcpService
          -> Repositories
          -> Room database
```

The AI model must never access Room, repositories, or files directly. The MCP
service remains the only place where AI-selected actions are executed.

## OpenRouter Model Choice

Use:

```text
openrouter/free
```

OpenRouter's Free Models Router automatically selects an available free model.
When tools are included in the request, it filters for models that support tool
calling.

Important limitations:

- Free-model availability changes.
- Free models may have lower rate limits.
- Free models may be slower during peak usage.
- A different model may answer each request.
- Tool-call quality may vary between selected free models.
- Free inference is suitable for an initial release, demo, or low-volume use,
  but reliability must be measured before depending on it for critical work.

The response includes the actual model used. Store this only as non-sensitive
diagnostic metadata.

Official references:

- https://openrouter.ai/docs/guides/routing/routers/free-router
- https://openrouter.ai/docs/guides/features/tool-calling
- https://openrouter.ai/docs/api-reference/chat-completion
- https://openrouter.ai/docs/guides/overview/models

## OpenRouter Request Shape

Use the OpenAI-compatible chat completions endpoint:

```text
POST https://openrouter.ai/api/v1/chat/completions
```

The request should include:

```json
{
  "model": "openrouter/free",
  "messages": [
    {
      "role": "system",
      "content": "You are the MedTrack assistant. Use tools when the user asks to read or change app data."
    },
    {
      "role": "user",
      "content": "Move Ramesh from room 132 to 135"
    }
  ],
  "tools": [],
  "tool_choice": "auto",
  "provider": {
    "require_parameters": true,
    "zdr": true,
    "data_collection": "deny"
  }
}
```

`tools` must be generated from `MedTrackMcpCatalog`, not manually duplicated.

## Tool Definition Conversion

Create a converter:

```text
MedTrackMcpCatalog.tools
  -> OpenRouter/OpenAI function tool JSON
```

Each MCP tool descriptor becomes:

```json
{
  "type": "function",
  "function": {
    "name": "update_patient_room",
    "description": "Update the room number on the latest matching visit.",
    "parameters": {
      "type": "object",
      "properties": {
        "patientName": {
          "type": "string",
          "description": "Exact patient name."
        },
        "currentRoomNumber": {
          "type": "string",
          "description": "Current room number."
        },
        "newRoomNumber": {
          "type": "string",
          "description": "New room number."
        }
      },
      "required": [
        "patientName",
        "currentRoomNumber",
        "newRoomNumber"
      ],
      "additionalProperties": false
    }
  }
}
```

This keeps the OpenRouter tool list synchronized with the MCP catalog.

## Tool Execution Loop

The orchestrator should support a real tool-call loop:

```text
User message
  -> OpenRouter request with tools
  -> Model returns tool_calls
  -> Validate tool name and arguments
  -> Execute through InAppMcpClient
  -> Send tool result back to OpenRouter
  -> Model returns final user-facing answer
```

OpenRouter does not execute MedTrack tools. The model only suggests the tool
call. The Android app executes it locally.

Limit the number of tool-call rounds, for example:

```text
Maximum tool rounds per user message: 4
```

This prevents accidental loops and excessive requests.

## Multi-Tool Requests

Some user requests require more than one MCP call.

Example:

```text
Add a visit for Ramesh with blood test and urine test.
```

`create_patient_visit` supports one optional task. The model may need to call:

```text
1. search_patient
2. create_patient_visit
3. add_patient_task
4. get_patient_current_process
```

The orchestrator must preserve previous tool results in the conversation until
the request is complete.

## Local Validation

Never execute a model tool call without local validation.

Validate:

- Tool name exists in `MedTrackMcpCatalog`.
- Tool arguments are valid JSON.
- Required fields are present.
- Unknown fields are rejected.
- String values are trimmed.
- Integer values are valid.
- Tool-call round limit is not exceeded.
- The MCP result is checked for `isError`.

The MCP service already performs business validation such as patient/room
matching. Keep that validation as the final authority.

## Action Confirmation

For the first version, use this rule:

### Read Tools

Execute immediately:

- `search_patient`
- `get_patient_current_process`
- `get_patient_medicines`
- `get_patient_reports`
- `get_patient_blood_reports`
- `get_patient_tasks`
- `get_due_follow_ups`

### Write Tools

Show a confirmation card before execution:

- `create_patient`
- `create_patient_visit`
- `update_patient_room`
- `add_patient_task`
- `update_patient_task_status`
- `add_or_update_medicine`
- `remove_patient_medicine`
- `schedule_follow_up`
- `mark_follow_up_done`
- `reschedule_follow_up`
- `discharge_patient`

The confirmation should show the tool action in normal language, not raw JSON.

Example:

```text
Move Ramesh from room 132 to room 135?

[Cancel] [Confirm]
```

## Internet and Offline Behavior

The app remains offline-first.

```text
No internet:
  All normal app screens continue working.
  AI screen shows "AI requires an internet connection."
  No AI response or AI action is available.

Internet available:
  AI screen can send requests to OpenRouter.

OpenRouter unavailable or rate-limited:
  AI screen shows a clear error.
  No normal app workflow is affected.
```

Do not automatically retry write tool calls after a timeout. The action may
already have completed locally.

## Remove llama.cpp

The target architecture no longer needs any local or fake model inference.

Files and wiring to review for removal:

- `LlamaCppEngine.kt`
- `NativeLlmBridge.kt`
- `LocalModelFileManager.kt`
- `FakeLocalLlmEngine.kt`
- `LocalLlmEngine.kt`
- Native C++ llama.cpp integration under `app/src/main/cpp`
- llama.cpp dependencies and CMake configuration
- llama-specific test commands in `AiAssistantOrchestrator.kt`
- `AiModule.kt` local engine binding

Do not remove the MCP server, MCP client, MCP catalog, or MCP service. Those are
still required for local tool execution.

## Suggested New Classes

```text
OpenRouterAiClient
  Calls OpenRouter chat completions API.

OpenRouterModels
  Contains "openrouter/free" and future model configuration.

McpToolSchemaConverter
  Converts MedTrackMcpCatalog tools into OpenRouter function tools.

AiToolCallValidator
  Validates model-selected tools and arguments.

AiConversationMessage
  Represents user, assistant, tool call, and tool result messages.

AiAssistantOrchestrator
  Runs the request -> tool -> result -> final answer loop.

ConnectivityObserver
  Reports whether AI requests can be attempted.
```

## Authentication Decision

Free models still require an OpenRouter API key.

Do not place a shared OpenRouter API key inside the APK.

Choose one:

### Option A: User Connects OpenRouter Account

Use OpenRouter OAuth PKCE so each user has a user-controlled key.

Best when:

- Users should manage their own OpenRouter account.
- The app should not operate a backend.

Reference:

- https://openrouter.ai/docs/use-cases/oauth-pkce

### Option B: Small Backend Holds the Key

The Android app calls a backend, and the backend calls OpenRouter.

Best when:

- The product owner wants to manage access.
- Users should not need an OpenRouter account.
- Usage limits and centralized control are required.

The backend should only proxy AI inference. MCP tools must still execute on the
device because the Room database is local.

## Privacy

Patient information may be sensitive.

For every request:

- Use `provider.zdr = true`.
- Use `provider.data_collection = "deny"`.
- Do not enable prompt or response logging.
- Send only data needed for the current request.
- Do not send report files in the first version.
- Do not log patient prompts, tool arguments, or tool results to Logcat.
- Do not assume ZDR replaces a legal healthcare compliance review.

## Implementation Phases

### Phase 1: Remove Local Model Path

- Remove llama.cpp UI commands, native code, dependencies, and production wiring.
- Remove `FakeLocalLlmEngine`, `LocalLlmEngine`, and their Hilt binding.
- Make `AiAssistantOrchestrator` depend directly on `OpenRouterAiClient`.
- Keep the existing MCP architecture unchanged.

### Phase 2: OpenRouter Text Response

- Add OpenRouter authentication.
- Add `OpenRouterAiClient`.
- Call `openrouter/free`.
- Handle offline, timeout, authentication, rate-limit, and service errors.
- Return normal text answers without tools.

### Phase 3: Read-Only MCP Tool Calling

- Convert MCP catalog tools into OpenRouter tool schemas.
- Send read-only tools to OpenRouter.
- Execute returned read tools through `InAppMcpClient`.
- Send tool results back to the model.
- Show the final answer.

### Phase 4: Write Tool Confirmation

- Add confirmation cards for write tools.
- Execute approved actions through MCP.
- Show MCP success and error messages.
- Prevent duplicate tool execution.

### Phase 5: Multi-Tool Requests

- Support multiple sequential tool calls.
- Limit tool-call rounds.
- Test visit creation with multiple tasks and medicines.
- Test wrong-room suggestions and ambiguous patient names.

## Testing Plan

Test these cases:

- App works with no internet.
- AI screen detects no internet.
- AI feature does not attempt any local fallback.
- Invalid or expired OpenRouter key.
- Free model unavailable.
- Rate limit response.
- Model returns normal text.
- Model returns one read tool.
- Model returns one write tool.
- User cancels write confirmation.
- Model returns an unknown tool.
- Model omits required arguments.
- MCP returns an error.
- Multi-tool visit creation.
- Tool loop reaches maximum rounds.
- OpenRouter response uses a different free model between requests.

## Decisions Needed Before Coding

1. Will users connect their own OpenRouter account with OAuth PKCE, or will a
   backend hold the API key?
2. Should every write action require confirmation?
3. Can patient names and clinical details be sent to OpenRouter?
4. Should the AI conversation be stored locally or cleared when the screen
   closes?
5. Should `openrouter/free` be the only model, or should settings later allow a
   paid model?
