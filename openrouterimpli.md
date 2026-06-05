# OpenRouter Implementation - Learn Inch by Inch

This file tracks the OpenRouter implementation journey step by step.

The purpose is not only to make the AI feature work. The purpose is to
understand why each file is needed, how data moves through the app, and why the
AI model is allowed to select MCP tools but is not allowed to directly change
the database.

We will implement one small part at a time. After each part, we will:

1. Review the code that was added or removed.
2. Explain why it belongs in that file.
3. Explain how it connects to the existing app.
4. Compile or test the small change.
5. Decide whether to continue to the next part.

## Final Goal

The normal MedTrack app must continue to work without internet.

The AI feature will depend completely on OpenRouter:

```text
User types a request in the AI screen
  -> Android app sends request and MCP tool schemas to OpenRouter
  -> OpenRouter free model chooses a tool or returns text
  -> Android app validates the selected tool
  -> Android app calls the existing local MCP client
  -> MCP service changes or reads local Room data
  -> Android app shows the result
```

There will be:

- No llama.cpp model.
- No local AI model.
- No `FakeLocalLlmEngine` in production.
- No AI response when internet or OpenRouter is unavailable.
- No direct database access from the AI model.

## Important Architecture Rule

OpenRouter does not execute MedTrack actions.

OpenRouter only returns a suggestion such as:

```json
{
  "name": "update_patient_room",
  "arguments": {
    "patientName": "Ramesh",
    "currentRoomNumber": "132",
    "newRoomNumber": "135"
  }
}
```

The Android app decides whether that tool exists and whether it is allowed.
Only after validation does the app call:

```text
InAppMcpClient.callTool(...)
```

The existing MCP flow remains responsible for the real action:

```text
InAppMcpClient
  -> McpRequestRouter
  -> MedTrackMcpService
  -> Repository
  -> Room database
```

This boundary is important because an AI model can make mistakes. The MCP
service remains the source of truth for validation and app behavior.

---

## Part 1 - Understand What We Are Replacing

### Goal

Understand the current AI path before removing it.

### Current Files

- `app/src/main/java/com/medtrack/app/ui/assistant/AssistantScreen.kt`
- `app/src/main/java/com/medtrack/app/ui/assistant/AssistantViewModel.kt`
- `app/src/main/java/com/medtrack/app/ai/AiAssistantOrchestrator.kt`
- `app/src/main/java/com/medtrack/app/ai/LocalLlmEngine.kt`
- `app/src/main/java/com/medtrack/app/ai/FakeLocalLlmEngine.kt`
- `app/src/main/java/com/medtrack/app/ai/LlamaCppEngine.kt`
- `app/src/main/java/com/medtrack/app/ai/NativeLlmBridge.kt`
- `app/src/main/java/com/medtrack/app/ai/LocalModelFileManager.kt`
- `app/src/main/java/com/medtrack/app/di/AiModule.kt`
- `app/src/main/cpp`

### Current Flow

```text
AssistantScreen
  -> AssistantViewModel.sendMessage()
  -> AiAssistantOrchestrator.handleUserMessage()
  -> LocalLlmEngine.generate()
  -> FakeLocalLlmEngine
  -> JSON decision
  -> InAppMcpClient.callTool()
```

`FakeLocalLlmEngine` is currently the production implementation because
`AiModule` binds it to `LocalLlmEngine`.

It is not a real AI model. It uses regular-expression matching for simple room
change requests.

`LlamaCppEngine` is present as an experimental local model path, but it is slow
and is not the target architecture.

### Why We Must Remove Carefully

If we delete `FakeLocalLlmEngine` before replacing its dependency, Hilt cannot
create `AiAssistantOrchestrator`.

If we delete native llama.cpp files but leave Gradle or CMake configuration,
the Android build can fail.

If we remove `AiAssistantOrchestrator` entirely, the assistant screen has no
place to coordinate OpenRouter responses and MCP tool calls.

### Part 1 Implementation Task

Create a dependency map of every reference to:

```text
LocalLlmEngine
FakeLocalLlmEngine
LlamaCppEngine
NativeLlmBridge
LocalModelFileManager
```

Do not remove code yet.

### Part 1 Verification

- We know which files will break when local AI classes are removed.
- We know which llama.cpp Gradle and CMake settings must be removed later.
- The app still compiles unchanged.

---

## Part 2 - Decide How The OpenRouter API Key Is Provided

### Goal

Choose how the Android app gets permission to call OpenRouter.

### Important Fact

Even `openrouter/free` requires an OpenRouter API key.

The key must not be hard-coded in:

- Kotlin source code
- `strings.xml`
- `BuildConfig`
- Gradle files committed to Git
- The APK

An APK can be extracted. A shared API key inside the app can be stolen.

### Option A - User Connects Their Own OpenRouter Account

Use OpenRouter OAuth PKCE.

```text
User
  -> Connect OpenRouter account
  -> OpenRouter authorization
  -> App receives user-controlled access key
  -> App stores it securely
```

This avoids operating a backend, but every user needs an OpenRouter account.

### Option B - Backend Holds The OpenRouter Key

```text
Android app
  -> Your backend
  -> OpenRouter
```

The backend stores the key securely. Users do not need an OpenRouter account.

The backend must not execute MCP tools because the Room database is local to the
device. It should only send AI requests and return AI responses.

### Part 2 Implementation Task

Choose Option A or Option B.

Do not build the OpenRouter client until this is decided because the network
request path and authentication code will be different.

### Part 2 Verification

- No API key will be embedded in the APK.
- We know who owns and pays for OpenRouter usage.
- We know whether the Android app calls OpenRouter directly or calls a backend.

---

## Part 3 - Remove The Local AI Model Path

### Goal

Remove llama.cpp and fake production inference without removing the assistant or
MCP architecture.

### Files To Remove Or Change

Remove:

```text
FakeLocalLlmEngine.kt
LocalLlmEngine.kt
LlamaCppEngine.kt
NativeLlmBridge.kt
LocalModelFileManager.kt
app/src/main/cpp
```

Change:

```text
AiModule.kt
AiAssistantOrchestrator.kt
app/build.gradle.kts
```

### Why `AiAssistantOrchestrator` Stays

The orchestrator is still needed because it will coordinate:

```text
User message
  -> OpenRouter request
  -> OpenRouter tool call
  -> Local MCP execution
  -> OpenRouter final answer
```

It changes responsibility, but it does not disappear.

### Part 3 Implementation Task

Remove the local model classes and native build configuration. Temporarily make
the assistant return a clear message:

```text
OpenRouter AI is not configured yet.
```

This keeps the app compiling between implementation stages.

### Part 3 Verification

- The app compiles without native llama.cpp.
- The app no longer contains a fake production AI path.
- Normal app screens still work.
- Assistant screen shows a temporary configuration message.

---

## Part 4 - Add OpenRouter Request And Response Models

### Goal

Create Kotlin models that represent the JSON sent to and received from
OpenRouter.

### Why Models Are Needed

OpenRouter receives structured JSON, not a plain string.

Example request:

```json
{
  "model": "openrouter/free",
  "messages": [
    {
      "role": "user",
      "content": "Move Ramesh from room 132 to 135"
    }
  ],
  "tools": [],
  "tool_choice": "auto"
}
```

Example response with a tool call:

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "tool_calls": [
          {
            "id": "call_1",
            "type": "function",
            "function": {
              "name": "update_patient_room",
              "arguments": "{\"patientName\":\"Ramesh\",\"currentRoomNumber\":\"132\",\"newRoomNumber\":\"135\"}"
            }
          }
        ]
      }
    }
  ]
}
```

Notice that `function.arguments` is a JSON string. The app must parse it before
calling MCP.

### Suggested Files

```text
app/src/main/java/com/medtrack/app/ai/openrouter/OpenRouterModels.kt
```

### Part 4 Implementation Task

Add request and response models for:

- Chat completion request
- Chat messages
- Tool definitions
- Tool calls
- Provider privacy settings
- Chat completion response
- OpenRouter error response

### Part 4 Verification

- Sample OpenRouter request JSON can be generated.
- Sample OpenRouter response JSON can be parsed.
- Tool-call arguments can be extracted as JSON.

---

## Part 5 - Add The OpenRouter Network Client

### Goal

Send a text-only request to OpenRouter and return the assistant response.

### Suggested File

```text
app/src/main/java/com/medtrack/app/ai/openrouter/OpenRouterAiClient.kt
```

### Responsibility

`OpenRouterAiClient` should only handle network communication:

```text
Kotlin request model
  -> HTTP request
  -> OpenRouter
  -> HTTP response
  -> Kotlin response model
```

It should not:

- Select MCP tools itself.
- Execute MCP tools.
- Read Room data.
- Build UI messages.

### Required Request Settings

```json
{
  "model": "openrouter/free",
  "provider": {
    "require_parameters": true,
    "zdr": true,
    "data_collection": "deny"
  }
}
```

### Part 5 Implementation Task

Add a text-only OpenRouter request without MCP tools.

### Part 5 Verification

- A user message receives a normal OpenRouter text response.
- Invalid authentication is shown as a clear error.
- No internet is shown as a clear error.
- Rate-limit and server errors do not crash the app.

---

## Part 6 - Convert MCP Catalog Tools Into OpenRouter Tools

### Goal

Reuse the existing MCP catalog so OpenRouter can understand every available
MedTrack tool.

### Why We Must Not Duplicate Tool Schemas

The app already defines tool names, descriptions, fields, and required fields
in:

```text
MedTrackMcpCatalog.tools
```

If we manually create a separate OpenRouter tool list, the two lists can become
different over time.

The correct flow is:

```text
MedTrackMcpCatalog.tools
  -> McpToolSchemaConverter
  -> OpenRouter tools JSON
```

### Suggested File

```text
app/src/main/java/com/medtrack/app/ai/openrouter/McpToolSchemaConverter.kt
```

### Part 6 Implementation Task

Convert every `McpToolDescriptor` into an OpenAI-compatible function tool.

### Part 6 Verification

- OpenRouter request contains all MCP tools.
- Required MCP fields are required in OpenRouter tool schemas.
- Adding a new MCP catalog tool automatically makes it available to OpenRouter.

---

## Part 7 - Execute One Read-Only Tool Call

### Goal

Allow OpenRouter to select one safe read-only MCP tool.

### First Recommended Tool

```text
search_patient
```

It is a good first tool because it reads data and does not change anything.

### Flow

```text
User:
Find Ramesh

OpenRouter:
search_patient(query="Ramesh")

App:
Validate tool call

InAppMcpClient:
callTool("search_patient", arguments)

MCP:
Return matching patient data
```

### Part 7 Implementation Task

Support one OpenRouter tool call and execute only read-only tools.

### Part 7 Verification

- OpenRouter can select `search_patient`.
- Unknown tools are rejected.
- Invalid arguments are rejected.
- MCP errors are shown to the user.
- No write tool can execute.

---

## Part 8 - Send Tool Results Back To OpenRouter

### Goal

Let OpenRouter turn raw MCP tool results into a useful final answer.

### Why This Is Needed

After a tool call, the MCP result may be structured JSON. The user should see a
clear answer, not raw protocol data.

```text
MCP result:
Found 1 patient match.

OpenRouter final answer:
Ramesh is patient ID 1 and the latest room is 132.
```

### Flow

```text
User message
  -> Assistant tool call message
  -> Tool result message
  -> OpenRouter second request
  -> Final assistant text
```

### Part 8 Implementation Task

Add the tool-result message to the conversation and request the final answer.

### Part 8 Verification

- The user sees a natural-language answer after a tool call.
- Tool result messages are linked to the correct tool-call ID.
- The app does not expose raw JSON unless needed for debugging.

---

## Part 9 - Add Write Tool Confirmation

### Goal

Prevent the AI model from changing patient data without user approval.

### Why Confirmation Is Needed

The model may misunderstand:

```text
Move Ramesh to 135
```

The app should show:

```text
Move Ramesh from room 132 to room 135?

[Cancel] [Confirm]
```

### Read Tools

Read tools can execute immediately.

### Write Tools

Write tools must wait for confirmation.

### Part 9 Implementation Task

Add a pending tool-action state to `AssistantViewModel` and a confirmation card
to `AssistantScreen`.

### Part 9 Verification

- Write tools do not execute before confirmation.
- Cancel does not call MCP.
- Confirm calls MCP exactly once.
- The user sees the MCP result.

---

## Part 10 - Support Multiple Tool Calls

### Goal

Handle requests that require more than one MCP action.

### Example

```text
Add a visit for Ramesh in room 132 with blood test and urine test.
```

Possible tool sequence:

```text
1. search_patient
2. create_patient_visit
3. add_patient_task
4. get_patient_current_process
```

### Important Limit

Set a maximum number of tool rounds:

```text
Maximum tool rounds per user message: 4
```

This prevents infinite loops and excessive OpenRouter requests.

### Part 10 Implementation Task

Add a bounded tool-call loop to `AiAssistantOrchestrator`.

### Part 10 Verification

- Multi-step requests can complete.
- Tool results remain available during the request.
- The loop stops after the maximum round count.
- Duplicate write actions are prevented.

---

## Part 11 - Improve AI Screen States

### Goal

Make the AI feature understandable when OpenRouter cannot be used.

### States To Show

```text
AI ready
AI requires internet
Connecting to OpenRouter
Authentication required
Free model unavailable
Rate limit reached
Request failed
Waiting for confirmation
Executing action
```

### Part 11 Implementation Task

Replace a single loading boolean with a clear assistant state model.

### Part 11 Verification

- The user understands why AI is unavailable.
- Normal app workflows remain unaffected.
- Failed AI requests preserve the user's context.

---

## Part 12 - Privacy And Logging Review

### Goal

Reduce sensitive patient data sent to OpenRouter and prevent accidental logs.

### Rules

- Use `provider.zdr = true`.
- Use `provider.data_collection = "deny"`.
- Do not log prompts, tool arguments, or tool results.
- Do not send report files in the first version.
- Send only data needed for the current task.
- Do not assume ZDR replaces a healthcare compliance review.

### Part 12 Implementation Task

Audit all AI requests, responses, logs, and error messages.

### Part 12 Verification

- No patient prompt appears in Logcat.
- No OpenRouter key appears in logs.
- No report file is uploaded.
- Privacy provider settings are present on every request.

---

## Part 13 - Final Test Scenarios

### Offline App

- Disable internet.
- Open dashboard, patients, visits, reports, follow-ups, and discharged list.
- Confirm normal app features still work.
- Confirm AI screen explains that internet is required.

### OpenRouter Errors

- Invalid key
- Expired key
- Free model unavailable
- Rate limit
- Timeout
- Server error

### Tool Safety

- Unknown tool
- Missing required argument
- Wrong room number
- Similar patient names
- Multiple matching tasks
- User cancels write confirmation
- Tool loop reaches maximum rounds

### Real User Requests

- Find Ramesh.
- What is happening with Ramesh in room 132?
- Move Ramesh from room 132 to 135.
- Add a blood test task for Ramesh in room 132.
- Add a visit with multiple tasks.
- Discharge a patient after confirmation.

---

## Current Progress

```text
Part 1 - Completed
Part 2 - Completed: user enters and owns the OpenRouter API key
Part 3 - Completed
Part 4 - Completed
Part 5 - Completed: developer-only text OpenRouter client
Part 6 - Pending
Part 7 - Pending
Part 8 - Pending
Part 9 - Pending
Part 10 - Pending
Part 11 - Pending
Part 12 - Pending
Part 13 - Pending
```

## Next Step

Start with **Part 1 - Understand What We Are Replacing**.

We should inspect all local AI references and build a dependency map before
removing any code.
