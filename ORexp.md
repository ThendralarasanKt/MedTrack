# OpenRouter Implementation Explanations

This file explains each OpenRouter implementation part in detail as we complete
it.

The implementation checklist lives in:

```text
openrouterimpli.md
```

This explanation file is different. It records what we learned, why the current
code exists, what will change, and how the architecture works.

---

# Part 1 - Understand What We Are Replacing

## Part 1 Status

Completed as an architecture and dependency review.

No AI implementation code was removed in this part.

## Why We Started With A Dependency Map

The current app has several files with names related to AI, but they do not all
perform the same job.

Some files affect what happens when the user sends an assistant message.
Other files affect the Android native build even when the user never opens the
assistant.

If we remove files only by name, we can create two different types of failure:

1. **Runtime dependency failure**

   Hilt cannot create `AiAssistantOrchestrator` because one of its constructor
   dependencies no longer exists.

2. **Build-time native failure**

   Gradle still asks CMake to compile native llama.cpp code even after Kotlin
   classes are removed.

The dependency map tells us the safe removal order.

## Current Assistant Entry Flow

The user interacts with the AI feature through:

```text
AssistantScreen
  -> AssistantViewModel
  -> AiAssistantOrchestrator
```

### `AssistantScreen`

File:

```text
app/src/main/java/com/medtrack/app/ui/assistant/AssistantScreen.kt
```

Responsibility:

- Displays assistant messages.
- Accepts user text.
- Shows loading state.
- Calls functions on `AssistantViewModel`.

It does not know whether the response comes from a fake engine, llama.cpp, or
OpenRouter.

This screen should remain when we move to OpenRouter.

### `AssistantViewModel`

File:

```text
app/src/main/java/com/medtrack/app/ui/assistant/AssistantViewModel.kt
```

Responsibility:

- Stores the current input text.
- Stores the conversation messages shown by the screen.
- Prevents sending another request while one is loading.
- Calls `AiAssistantOrchestrator.handleUserMessage(...)`.

It does not directly call MCP or the database.

This ViewModel should remain, but later it will need better states such as
offline, authentication error, waiting for confirmation, and executing a tool.

### `AiAssistantOrchestrator`

File:

```text
app/src/main/java/com/medtrack/app/ai/AiAssistantOrchestrator.kt
```

Responsibility today:

- Receives the user's natural-language message.
- Handles special llama.cpp test commands.
- Gets MCP tool descriptions through `InAppMcpClient.listTools()`.
- Builds a prompt.
- Asks `LocalLlmEngine` for a JSON decision.
- Parses that JSON decision.
- Calls an MCP tool through `InAppMcpClient.callTool(...)`.
- Returns a simple text result.

This class is the coordinator between AI and MCP.

It should not be deleted. It will be rewritten so that OpenRouter becomes its
only AI dependency.

## Current Production AI Path

The current production path is:

```text
AiAssistantOrchestrator
  -> LocalLlmEngine
  -> FakeLocalLlmEngine
```

This is important: the production assistant is not currently using llama.cpp.

### `LocalLlmEngine`

File:

```text
app/src/main/java/com/medtrack/app/ai/LocalLlmEngine.kt
```

Current code shape:

```kotlin
interface LocalLlmEngine {
    suspend fun generate(prompt: String): String
}
```

Why it exists:

- It is an abstraction for something that can receive a prompt and return text.
- It allows the orchestrator to depend on an interface instead of a concrete
  model implementation.

Why it will be removed:

- The name and purpose are specifically for local inference.
- The new architecture has one production AI provider: OpenRouter.
- We do not need a production abstraction that suggests multiple local engines.

Future replacement:

```text
OpenRouterAiClient
```

The orchestrator will depend on the OpenRouter client directly.

### `FakeLocalLlmEngine`

File:

```text
app/src/main/java/com/medtrack/app/ai/FakeLocalLlmEngine.kt
```

Why it exists:

- It makes the assistant appear functional without a real model.
- It uses regular expressions to detect simple room-change commands.
- It returns JSON that resembles an AI tool decision.

Example behavior:

```text
User:
Move Ramesh from 132 to 135

FakeLocalLlmEngine:
{
  "type": "tool_call",
  "tool": "update_patient_room",
  "arguments": {
    "patientName": "Ramesh",
    "currentRoomNumber": "132",
    "newRoomNumber": "135"
  }
}
```

Why it is the current production engine:

`AiModule.kt` binds it to `LocalLlmEngine`:

```text
LocalLlmEngine -> FakeLocalLlmEngine
```

Hilt sees that binding and injects `FakeLocalLlmEngine` whenever
`AiAssistantOrchestrator` requests a `LocalLlmEngine`.

Why it will be removed:

- It is not an AI model.
- It only understands a narrow room-change pattern.
- The final AI feature must depend completely on OpenRouter.

## Current Experimental llama.cpp Path

The llama.cpp path is separate from the production fake engine.

```text
AiAssistantOrchestrator special command
  -> LlamaCppEngine
  -> NativeLlmBridge
  -> medtrack_native JNI library
  -> llama.cpp
  -> GGUF model file
```

The user can trigger it only through special messages such as:

```text
native smoke test
llama version test
llama model path
llama load model test
llama generate test
llama generate <prompt>
llama tool decision test
llama decide <request>
```

These are development commands, not the normal assistant flow.

### `LlamaCppEngine`

File:

```text
app/src/main/java/com/medtrack/app/ai/LlamaCppEngine.kt
```

Responsibility:

- Checks whether a GGUF model file exists.
- Loads the model through `NativeLlmBridge`.
- Keeps a native model handle.
- Formats prompts for local generation.
- Requests generated tokens from the native layer.
- Releases the native model handle.

It implements `LocalLlmEngine`, but Hilt does not bind it as the production
`LocalLlmEngine`.

Why it will be removed:

- Local inference is too slow for the intended experience.
- OpenRouter will be the only AI provider.
- Keeping this path increases APK build complexity and maintenance cost.

### `LocalModelFileManager`

File:

```text
app/src/main/java/com/medtrack/app/ai/LocalModelFileManager.kt
```

Responsibility:

- Creates an internal app directory named `models`.
- Defines the expected model file name:

```text
medtrack-assistant.gguf
```

- Reports whether the model exists and its file size.

Why it will be removed:

- The app will no longer load a local GGUF model.
- OpenRouter hosts and runs the selected model remotely.

### `NativeLlmBridge`

File:

```text
app/src/main/java/com/medtrack/app/ai/NativeLlmBridge.kt
```

Responsibility:

- Declares JNI functions that Kotlin can call.
- Loads the native shared library:

```kotlin
System.loadLibrary("medtrack_native")
```

Declared native functions:

```text
nativeSmokeTest()
nativeLlamaVersion()
nativeLoadModel(...)
nativeGenerate(...)
nativeReleaseModel(...)
```

Why it will be removed:

- OpenRouter uses HTTP, not JNI.
- No local native model will be loaded or generated.

## Native Build Dependencies

The llama.cpp path is not only Kotlin code. It is connected to the Android build
system.

### `app/src/main/cpp/medtrack_native.cpp`

Responsibility:

- Implements the JNI functions declared by `NativeLlmBridge`.
- Includes `llama.h`.
- Initializes the llama.cpp backend.
- Loads a model from a file path.
- Tokenizes prompts.
- Generates tokens.
- Releases model and context memory.

Why it will be removed:

- No Kotlin code will need JNI model generation after OpenRouter is added.

### `app/src/main/cpp/CMakeLists.txt`

Responsibility:

- Defines the `medtrack_native` native library.
- Adds the vendored `third_party/llama.cpp` project.
- Links the native library against the `llama` library.

Important current relationship:

```text
CMakeLists.txt
  -> third_party/llama.cpp
  -> llama native library
  -> medtrack_native shared library
```

Why it will be removed:

- The Android app will no longer build any AI native library.

### `app/build.gradle.kts`

Current native-related settings:

```text
ndkVersion
abiFilters
externalNativeBuild
cmake path and version
```

These settings tell Gradle to invoke CMake while building the app.

Why they must be reviewed during removal:

- Deleting only `app/src/main/cpp` is not enough.
- If `externalNativeBuild` remains and points to a missing CMake file, the build
  fails.
- `ndkVersion` and `abiFilters` may no longer be needed after all native code is
  removed.

### `third_party/llama.cpp`

Responsibility:

- Contains the vendored llama.cpp source code used by CMake.

Why it will be removed or detached:

- It is a large third-party codebase.
- It is only referenced by `app/src/main/cpp/CMakeLists.txt` for local AI.
- OpenRouter removes the need to compile or ship it.

Before deleting this directory, we should confirm no other project feature uses
it. The Part 1 search found no other app integration.

## Hilt Dependency Map

Hilt creates the current assistant dependencies like this:

```text
AssistantViewModel
  requires AiAssistantOrchestrator

AiAssistantOrchestrator
  requires LocalLlmEngine
  requires InAppMcpClient
  requires NativeLlmBridge
  requires LlamaCppEngine

AiModule
  tells Hilt:
  LocalLlmEngine is provided by FakeLocalLlmEngine

LlamaCppEngine
  requires NativeLlmBridge
  requires LocalModelFileManager
```

Visual map:

```text
AssistantViewModel
  |
  v
AiAssistantOrchestrator
  |---------------------> InAppMcpClient
  |
  |-> LocalLlmEngine -> FakeLocalLlmEngine
  |
  |-> NativeLlmBridge -> medtrack_native -> llama.cpp
  |
  `-> LlamaCppEngine
        |-> NativeLlmBridge -> medtrack_native -> llama.cpp
        `-> LocalModelFileManager -> medtrack-assistant.gguf
```

## What Must Remain

These parts are not local model code and must remain:

```text
AssistantScreen
AssistantViewModel
AiAssistantOrchestrator
InAppMcpClient
McpRequestRouter
MedTrackMcpCatalog
MedTrackMcpService
Repositories
Room database
```

The MCP architecture is still required because OpenRouter will only select
tools. The app must execute those tools locally.

## What Will Be Replaced

Current:

```text
AiAssistantOrchestrator
  -> LocalLlmEngine
  -> FakeLocalLlmEngine
```

Future:

```text
AiAssistantOrchestrator
  -> OpenRouterAiClient
```

Current special local model path:

```text
AiAssistantOrchestrator
  -> LlamaCppEngine
  -> NativeLlmBridge
  -> llama.cpp
```

Future:

```text
Removed
```

## Safe Removal Order For A Later Part

We should not remove files yet, but the dependency map gives us a safe order:

1. Change `AiAssistantOrchestrator` so it no longer requires local engine or
   llama.cpp dependencies.
2. Remove the `AiModule` binding.
3. Remove `FakeLocalLlmEngine.kt`.
4. Remove `LocalLlmEngine.kt`.
5. Remove `LlamaCppEngine.kt`.
6. Remove `NativeLlmBridge.kt`.
7. Remove `LocalModelFileManager.kt`.
8. Remove `app/src/main/cpp`.
9. Remove native build configuration from `app/build.gradle.kts`.
10. Remove or detach `third_party/llama.cpp`.
11. Compile the app.

This order avoids Hilt and native build errors.

## Test Reference Result

The Part 1 search found no app unit tests or Android instrumentation tests that
reference:

```text
LocalLlmEngine
FakeLocalLlmEngine
LlamaCppEngine
NativeLlmBridge
LocalModelFileManager
AiAssistantOrchestrator
```

That means removal will not require updating existing tests, but it also means
the new OpenRouter path will need new tests later.

## Part 1 Conclusion

The current app contains:

1. A fake production engine used for simple room-change parsing.
2. An experimental llama.cpp path used by special assistant commands.
3. A valuable MCP execution layer that must remain.

The OpenRouter implementation should replace only the model-selection layer.
It must not replace or bypass MCP.

## Next Part

Part 2 is:

```text
Decide How The OpenRouter API Key Is Provided
```

Before writing any OpenRouter network code, we must choose whether:

- Users connect their own OpenRouter account using OAuth PKCE, or
- A backend securely holds the OpenRouter API key.

---

# Part 2 - User Enters Their Own OpenRouter API Key

## Part 2 Status

Completed as a product and architecture decision.

Chosen approach:

```text
The user creates an OpenRouter API key and enters it into MedTrack.
```

The Android app will call OpenRouter directly. There will be no MedTrack backend
for AI requests.

## Why This Choice Fits The Current App

MedTrack is currently a local Android app:

```text
Android app
  -> Room database on the device
  -> Local MCP tools on the device
```

Using a user-owned OpenRouter key keeps the AI architecture simple:

```text
Android app
  -> OpenRouter
  -> AI model selects an MCP tool
  -> Android app executes the MCP tool locally
```

Benefits:

- No backend needs to be built or hosted.
- The product owner does not pay for every user's AI usage.
- Each user controls their own OpenRouter account, key, credits, and limits.
- MCP execution stays local because the patient database is local.

Tradeoffs:

- Every user who wants AI must create an OpenRouter account and key.
- Users must understand that AI usage depends on their OpenRouter account.
- A missing, invalid, expired, disabled, or rate-limited key makes AI
  unavailable.
- The app must securely store a sensitive credential.

## Direct OpenRouter Authentication

OpenRouter authenticates API requests using a Bearer token.

The Android app will send:

```http
Authorization: Bearer <USER_OPENROUTER_API_KEY>
```

Example request:

```http
POST https://openrouter.ai/api/v1/chat/completions
Content-Type: application/json
Authorization: Bearer <USER_OPENROUTER_API_KEY>
```

The key must be added only at the network layer. It should not be passed through
UI messages, MCP tools, prompts, or Room entities.

Official OpenRouter authentication reference:

```text
https://openrouter.ai/docs/api-reference/authentication
```

## Why The Key Must Not Be Hard-Coded

The API key must not be stored in:

```text
Kotlin source files
strings.xml
BuildConfig
Gradle properties committed to Git
Room database
MCP tool arguments
Logcat
Crash reports
```

An Android APK can be inspected. If a shared key is compiled into the app, any
person can extract and misuse it.

The chosen approach avoids this because the key is entered after installation
and belongs to that user.

## Recommended User Experience

The AI screen should detect whether a key is available.

### No Key Stored

Show:

```text
Connect OpenRouter to use AI

Enter your OpenRouter API key. The key is stored securely on this device and is
used only for OpenRouter requests.

[API key field]
[Save and verify]
```

The API key field should:

- Hide the entered characters by default.
- Allow a temporary show/hide toggle.
- Disable copy display after saving.
- Trim accidental spaces before validation.
- Never display the full stored key again.

### Valid Key Stored

Show a safe summary:

```text
OpenRouter connected
Key: sk-or-v1-...7xQ

[Replace key] [Remove key]
```

Do not show the full key.

### Invalid Key

Show:

```text
OpenRouter could not verify this API key.
Check the key and try again.
```

### Removed Key

Immediately disable AI requests and remove the stored credential.

## Key Validation

Before saving a newly entered key, validate it with OpenRouter:

```text
GET https://openrouter.ai/api/v1/key
```

Request header:

```http
Authorization: Bearer <USER_OPENROUTER_API_KEY>
```

OpenRouter documents this endpoint as the way to get information about the
current authenticated API key.

Official reference:

```text
https://openrouter.ai/docs/api/api-reference/api-keys/get-current-key
```

Why validation is useful:

- Prevents saving typing mistakes.
- Detects an invalid or disabled key.
- Confirms the app can reach OpenRouter.
- Can show non-sensitive information such as whether the key is free tier or
  whether a limit remains.

Important:

- Do not store the entire validation response unless needed.
- Do not show sensitive account details.
- Do not block normal non-AI app features when validation fails.

## Key Storage

The key is a credential, not normal app data.

It should not be stored in Room because Room contains application records and
is not designed specifically for secrets.

Recommended storage:

```text
Android Keystore-backed encrypted local storage
```

Conceptual flow:

```text
User enters key
  -> App validates key with OpenRouter
  -> App encrypts key using a key protected by Android Keystore
  -> App stores only encrypted key material locally
  -> Network client retrieves and decrypts key when making OpenRouter requests
```

The exact Android storage API should be selected during implementation. The
important design rule is that the raw key is not stored as plain text.

## Suggested New Responsibility

Create a dedicated key manager:

```text
OpenRouterApiKeyStore
```

Its responsibility should be small:

```text
save(apiKey)
get()
hasKey()
remove()
maskedLabel()
```

It should not:

- Call OpenRouter.
- Build chat requests.
- Execute MCP tools.
- Store patient data.

Keeping key storage separate makes it easier to audit and replace later.

## Suggested OpenRouter Connection State

The UI should not use only a Boolean such as `hasApiKey`.

Use clear states:

```text
NotConfigured
Validating
Connected
InvalidKey
NetworkUnavailable
ValidationFailed
```

Why:

- `InvalidKey` is different from `NetworkUnavailable`.
- The user should not delete a valid key only because the internet is down.
- The assistant screen needs to explain what action the user should take.

## Key Lifecycle

### Save

```text
1. User enters key.
2. App trims whitespace.
3. App validates key with GET /api/v1/key.
4. If valid, app encrypts and stores key.
5. AI becomes available.
```

### Replace

```text
1. User chooses Replace key.
2. Existing key remains active until the new key is validated.
3. App validates the new key.
4. If valid, app replaces the stored key.
5. If invalid, app keeps the previous valid key.
```

This prevents a typing mistake from breaking an already working connection.

### Remove

```text
1. User chooses Remove key.
2. App asks for confirmation.
3. App deletes encrypted key material.
4. AI becomes unavailable.
5. Normal app features continue working.
```

## OpenRouter Key Limits

OpenRouter allows users to create keys and optionally set credit limits. The app
should encourage users to create a dedicated key for MedTrack with a reasonable
limit instead of using a broad personal key.

OpenRouter also documents that current key information and remaining limits can
be checked using:

```text
GET https://openrouter.ai/api/v1/key
```

Official references:

```text
https://openrouter.ai/docs/api-reference/authentication
https://openrouter.ai/docs/api-reference/limits/
```

## Error Handling Rules

The OpenRouter client must distinguish:

```text
No key configured
Invalid key
No internet
OpenRouter timeout
Rate limit
Free model unavailable
OpenRouter server error
```

None of these errors should affect:

- Patient records
- Visits
- Tasks
- Medicines
- Reports
- Follow-ups
- Discharge records
- Local MCP tools used outside AI

## Privacy Reminder

Using a user-owned API key changes who pays for inference, but it does not remove
privacy concerns.

Patient information may still be sent from the device to OpenRouter and the
selected model provider.

Every AI request should still use:

```json
{
  "provider": {
    "require_parameters": true,
    "zdr": true,
    "data_collection": "deny"
  }
}
```

The app must also avoid logging:

```text
API keys
Patient prompts
Tool arguments
Tool results
OpenRouter responses containing patient data
```

## Part 2 Architecture Result

The chosen authentication architecture is:

```text
User enters OpenRouter API key
  -> OpenRouterApiKeyStore securely stores it
  -> OpenRouterAiClient reads it only for network requests
  -> OpenRouter validates and serves AI requests
  -> AI-selected MCP tools execute locally
```

There is no AI backend:

```text
Android app -> OpenRouter
```

There is no shared application API key:

```text
Each user owns their own OpenRouter key
```

## Part 2 Conclusion

We can now design the OpenRouter client around direct Android-to-OpenRouter
requests.

The next implementation part is:

```text
Part 3 - Remove The Local AI Model Path
```

Before adding OpenRouter network code, we will remove the fake and llama.cpp
production paths while keeping the assistant screen, orchestrator, MCP client,
and MCP service.

---

# Part 3 - Remove The Local AI Model Path

## Part 3 Status

Completed.

The app no longer contains a production fake AI engine or llama.cpp native model
path.

The app still compiles.

## Why We Removed This Before Adding OpenRouter

If OpenRouter is the only AI provider, keeping local AI code creates confusion:

```text
Does the assistant use FakeLocalLlmEngine?
Does it use llama.cpp?
Does it use OpenRouter?
Does it fallback locally when internet is missing?
```

The answer we want is simple:

```text
AI feature = OpenRouter only
No internet = no AI feature
Normal app = still works offline
```

Removing the old paths first gives us a clean base for the OpenRouter client.

## Files Removed

### Fake Production AI

Removed:

```text
app/src/main/java/com/medtrack/app/ai/FakeLocalLlmEngine.kt
app/src/main/java/com/medtrack/app/ai/LocalLlmEngine.kt
app/src/main/java/com/medtrack/app/di/AiModule.kt
```

Why:

- `FakeLocalLlmEngine` was not a real AI model.
- It only parsed a few room-change patterns.
- `LocalLlmEngine` described local inference, which is no longer part of the
  product direction.
- `AiModule` only existed to bind `FakeLocalLlmEngine` as the production
  `LocalLlmEngine`.

Old dependency:

```text
AiAssistantOrchestrator
  -> LocalLlmEngine
  -> FakeLocalLlmEngine
```

That dependency is gone.

### llama.cpp Kotlin Layer

Removed:

```text
app/src/main/java/com/medtrack/app/ai/LlamaCppEngine.kt
app/src/main/java/com/medtrack/app/ai/NativeLlmBridge.kt
app/src/main/java/com/medtrack/app/ai/LocalModelFileManager.kt
```

Why:

- `LlamaCppEngine` managed slow local model loading and generation.
- `NativeLlmBridge` loaded the native `medtrack_native` library and exposed JNI
  functions.
- `LocalModelFileManager` managed a local `medtrack-assistant.gguf` model file.
- None of those are needed when OpenRouter runs the model remotely.

Old dependency:

```text
AiAssistantOrchestrator
  -> LlamaCppEngine
      -> NativeLlmBridge
      -> LocalModelFileManager
```

That dependency is gone.

### Native Build Layer

Removed:

```text
app/src/main/cpp/CMakeLists.txt
app/src/main/cpp/medtrack_native.cpp
third_party/llama.cpp
```

Why:

- `medtrack_native.cpp` implemented JNI methods for llama.cpp.
- `CMakeLists.txt` built the native `medtrack_native` shared library.
- `third_party/llama.cpp` was only needed by that CMake build.
- The OpenRouter architecture uses HTTP requests, not native inference.

## Gradle Build Changes

Removed native build configuration from:

```text
app/build.gradle.kts
```

Removed:

```text
ndkVersion = "30.0.14904198"
```

Removed from `defaultConfig`:

```text
ndk {
    abiFilters += listOf("arm64-v8a")
}
```

Removed:

```text
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "4.1.2"
    }
}
```

Why:

- The app no longer has native AI code.
- Gradle should not invoke CMake.
- The app should no longer be restricted to only `arm64-v8a` because of
  llama.cpp.

## Orchestrator Change

File changed:

```text
app/src/main/java/com/medtrack/app/ai/AiAssistantOrchestrator.kt
```

Old constructor:

```text
AiAssistantOrchestrator(
    LocalLlmEngine,
    InAppMcpClient,
    NativeLlmBridge,
    LlamaCppEngine
)
```

New constructor:

```text
AiAssistantOrchestrator(
    InAppMcpClient
)
```

Why `InAppMcpClient` remains:

- OpenRouter will still choose MCP tools.
- The app still needs to execute those tools locally.
- The orchestrator remains the coordinator between AI output and MCP execution.

Temporary behavior:

```text
OpenRouter AI is not configured yet.
```

This is intentional. It keeps the assistant screen compiling and working while
we build the OpenRouter client in later parts.

## Current Assistant Flow After Part 3

```text
AssistantScreen
  -> AssistantViewModel
  -> AiAssistantOrchestrator
  -> "OpenRouter AI is not configured yet."
```

No model is called.

No local fake engine is called.

No native llama.cpp library is loaded.

No MCP tool is executed yet from AI.

## What Did Not Change

These important parts remain:

```text
AssistantScreen
AssistantViewModel
AiAssistantOrchestrator
InAppMcpClient
McpRequestRouter
MedTrackMcpCatalog
MedTrackMcpService
PatientRepository
ClinicalRepository
Room database
```

This means the UI and MCP foundation are still ready for OpenRouter.

## Verification

Reference search after removal:

```text
LocalLlmEngine
FakeLocalLlmEngine
LlamaCppEngine
NativeLlmBridge
LocalModelFileManager
nativeSmokeTest
nativeLlamaVersion
llama
externalNativeBuild
ndkVersion
abiFilters
medtrack_native
```

Result:

```text
No remaining app/build references.
```

Compile command:

```text
.\gradlew.bat :app:compileDebugKotlin
```

Result:

```text
BUILD SUCCESSFUL
```

## Part 3 Conclusion

The app now has no local AI model path.

The next step is to add the OpenRouter data model layer:

```text
Part 4 - Add OpenRouter Request And Response Models
```

This next part will not call the internet yet. It will define the Kotlin shapes
for OpenRouter request and response JSON so the network client can be built
cleanly afterward.

---

# Part 4 - Add OpenRouter Request And Response Models

## Part 4 Status

Completed.

Added:

```text
app/src/main/java/com/medtrack/app/ai/openrouter/OpenRouterModels.kt
```

No network call was added in this part.

## Why We Added Models Before The Network Client

OpenRouter communication is JSON-based.

If we jump directly into network code, the client becomes responsible for too
many things at once:

```text
Build JSON
Send HTTP request
Parse HTTP response
Extract tool calls
Handle errors
Know provider settings
```

Part 4 separates the JSON shape from the HTTP call.

After this part:

```text
OpenRouterChatRequest
  -> JSONObject request body

JSONObject response body
  -> OpenRouterChatResponse
```

The future `OpenRouterAiClient` can focus only on:

```text
HTTP request
HTTP response
timeouts
authentication
network errors
```

## Why We Used `org.json`

The existing MCP layer already uses:

```text
org.json.JSONObject
org.json.JSONArray
```

Examples:

```text
McpRequestRouter.kt
McpJson.kt
InAppMcpClient.kt
```

The project does not currently use:

```text
kotlinx.serialization
Moshi
Gson
Retrofit
OkHttp
```

So Part 4 added OpenRouter JSON models using the same `org.json` style. This
keeps the change small and avoids adding a new dependency before we need one.

Later, if the OpenRouter client grows complex, we can choose a serialization
library deliberately.

## New Package

New package:

```text
com.medtrack.app.ai.openrouter
```

Why:

- Keeps OpenRouter-specific code separate from generic assistant code.
- Makes it clear which files belong to the remote AI provider.
- Prevents OpenRouter models from being mixed into the MCP package.

## `OPENROUTER_FREE_MODEL`

Code:

```kotlin
const val OPENROUTER_FREE_MODEL = "openrouter/free"
```

Why:

- The first implementation targets OpenRouter's free model router.
- The constant avoids repeating the model string in many places.
- Later model settings can replace this without changing the JSON model shape.

## `OpenRouterChatRequest`

Purpose:

Represents the body sent to:

```text
POST https://openrouter.ai/api/v1/chat/completions
```

Fields:

```text
model
messages
tools
toolChoice
provider
```

Important default:

```text
model = openrouter/free
toolChoice = auto
```

Why `tools` can be empty:

- Part 5 will first test text-only OpenRouter requests.
- Part 6 will add MCP tool schema conversion.
- The same request class supports both stages.

Example text-only request:

```kotlin
OpenRouterChatRequest(
    messages = listOf(
        OpenRouterMessage.system("You are MedTrack assistant."),
        OpenRouterMessage.user("Hello")
    )
)
```

Generated JSON shape:

```json
{
  "model": "openrouter/free",
  "messages": [
    {
      "role": "system",
      "content": "You are MedTrack assistant."
    },
    {
      "role": "user",
      "content": "Hello"
    }
  ],
  "tool_choice": "auto",
  "provider": {
    "require_parameters": true,
    "zdr": true,
    "data_collection": "deny"
  }
}
```

## `OpenRouterProviderOptions`

Purpose:

Adds provider privacy and routing controls to every OpenRouter request.

Defaults:

```text
requireParameters = true
zeroDataRetention = true
dataCollection = deny
```

Generated JSON:

```json
{
  "require_parameters": true,
  "zdr": true,
  "data_collection": "deny"
}
```

Why:

- `require_parameters` tells OpenRouter to route only to providers that support
  the request's required parameters.
- `zdr` requests Zero Data Retention routing.
- `data_collection = deny` rejects providers that require data collection.

This does not replace legal/privacy review, but it makes the request safer by
default.

## `OpenRouterMessage`

Purpose:

Represents one message in the chat conversation.

Supported message roles:

```text
system
user
assistant
tool
```

Helper constructors:

```kotlin
OpenRouterMessage.system(...)
OpenRouterMessage.user(...)
OpenRouterMessage.assistant(...)
OpenRouterMessage.toolResult(...)
```

Why `toolCallId` exists:

When OpenRouter asks the app to call a tool, the final answer request must send
the tool result back with the matching tool-call ID.

Flow:

```text
Assistant returns tool_call id = call_123
App executes MCP tool
App sends tool message with tool_call_id = call_123
OpenRouter uses that result to write final answer
```

## `OpenRouterTool`

Purpose:

Represents one OpenRouter function tool definition.

Shape:

```json
{
  "type": "function",
  "function": {
    "name": "search_patient",
    "description": "Search patients...",
    "parameters": {
      "type": "object",
      "properties": {},
      "required": []
    }
  }
}
```

Part 4 only defines the model.

Part 6 will create:

```text
McpToolSchemaConverter
```

That converter will turn:

```text
MedTrackMcpCatalog.tools
```

into:

```text
List<OpenRouterTool>
```

## `OpenRouterToolCall`

Purpose:

Represents a tool call selected by the OpenRouter model.

Important fields:

```text
id
name
argumentsJson
```

Why `argumentsJson` is a string:

OpenAI-compatible tool calling returns function arguments as a JSON string.

Example:

```json
"arguments": "{\"patientName\":\"Ramesh\",\"roomNumber\":\"132\"}"
```

The app must parse that string before passing it to MCP.

This helper handles that:

```kotlin
fun argumentsObject(): JSONObject = JSONObject(argumentsJson.ifBlank { "{}" })
```

Important:

`argumentsObject()` can still throw if the model returns invalid JSON. Later
tool validation must catch that and show a clear error.

## `OpenRouterChatResponse`

Purpose:

Represents the top-level OpenRouter response.

Fields:

```text
id
model
message
error
```

Why `model` is saved:

When using:

```text
openrouter/free
```

OpenRouter may route each request to a different free model. The response model
field lets us know which model answered.

That value is useful for diagnostics, but it must not be mixed with patient
data logs.

## `OpenRouterAssistantMessage`

Purpose:

Represents the assistant message inside the first response choice.

It supports two cases:

### Text answer

```text
content is not blank
toolCalls is empty
```

### Tool call answer

```text
toolCalls has one or more items
content may be blank
```

Helper:

```kotlin
val hasToolCalls: Boolean
```

Later orchestrator logic will use:

```text
if message.hasToolCalls:
    validate and execute tools
else:
    show content
```

## `OpenRouterError`

Purpose:

Represents an OpenRouter error object.

Fields:

```text
code
message
type
```

Why this matters:

The AI screen needs to explain failures clearly:

```text
Invalid key
Rate limit
Free model unavailable
Timeout
Server error
```

Part 4 only parses the OpenRouter error body. Part 5 will map HTTP status codes
and network failures into user-friendly error states.

## What Part 4 Does Not Do

Part 4 does not:

- Store an API key.
- Validate an API key.
- Call OpenRouter.
- Convert MCP catalog tools.
- Execute MCP tools.
- Change the assistant UI.
- Add write confirmation.

That separation is intentional.

## Verification

Compile command:

```text
.\gradlew.bat :app:compileDebugKotlin
```

Result:

```text
BUILD SUCCESSFUL
```

## Part 4 Conclusion

The app now has a typed OpenRouter JSON layer.

The next step is:

```text
Part 5 - Add The OpenRouter Network Client
```

In Part 5, we will use these models to send a text-only request to OpenRouter.
No MCP tools will be sent yet.

---

# Part 5 - Add The OpenRouter Network Client

## Part 5 Status

Completed for the developer-only text-response phase.

This part adds OpenRouter network access, but it does not send MCP tools yet.

## What Was Added

New files:

```text
app/src/main/java/com/medtrack/app/ai/openrouter/OpenRouterApiKeyProvider.kt
app/src/main/java/com/medtrack/app/ai/openrouter/OpenRouterAiClient.kt
```

Changed files:

```text
app/build.gradle.kts
app/src/main/java/com/medtrack/app/ai/AiAssistantOrchestrator.kt
app/src/main/java/com/medtrack/app/ui/assistant/AssistantScreen.kt
```

## Temporary Developer-Only API Key Storage

For this phase, the OpenRouter API key is read from:

```text
local.properties
```

Expected key:

```properties
openrouter.api.key=YOUR_OPENROUTER_KEY
```

Gradle reads this value and generates:

```kotlin
BuildConfig.OPENROUTER_API_KEY
```

Why this is acceptable only for now:

- `local.properties` is local developer configuration.
- The current goal is only to prove the OpenRouter request path.
- We are not building the polished user key UI yet.

Why this is not the final solution:

- A `BuildConfig` value is compiled into the APK.
- It is not appropriate for real user-owned key storage.
- Later we will replace this with a proper key entry UI and encrypted local
  storage.

## `OpenRouterApiKeyProvider`

File:

```text
app/src/main/java/com/medtrack/app/ai/openrouter/OpenRouterApiKeyProvider.kt
```

Responsibility:

```text
Read the temporary developer API key from BuildConfig.
```

Current behavior:

```kotlin
fun getApiKey(): String = BuildConfig.OPENROUTER_API_KEY.trim()
```

Why it exists:

- Keeps key lookup out of the network client.
- Gives us one file to replace later when we move from developer key storage to
  user-entered encrypted storage.

Future replacement:

```text
OpenRouterApiKeyProvider
  -> encrypted user key store
```

## `OpenRouterAiClient`

File:

```text
app/src/main/java/com/medtrack/app/ai/openrouter/OpenRouterAiClient.kt
```

Responsibility:

```text
OpenRouterChatRequest
  -> HTTP POST
  -> OpenRouter
  -> response JSON
  -> OpenRouterChatResponse or error
```

It does not:

- Execute MCP tools.
- Read Room data.
- Store the API key.
- Build UI state.
- Ask for user confirmation.

This separation is important because the network client should only know about
networking and OpenRouter JSON.

## Why `HttpURLConnection` Was Used

No new networking dependency was added.

The app currently does not use:

```text
OkHttp
Retrofit
Ktor
```

For this first text-only phase, `HttpURLConnection` is enough:

```text
Open URL
Set POST
Set headers
Write JSON body
Read JSON response
Parse JSON
```

Later, if the OpenRouter client needs interceptors, retries, streaming, or more
advanced error handling, we can choose a dedicated HTTP library.

## Request Headers

The client sends:

```http
Content-Type: application/json
Authorization: Bearer <developer key>
```

The API key is added only in the network client.

The key is not sent to:

- MCP tools
- Room
- Assistant messages
- OpenRouter prompt content

## Request Body

The orchestrator currently creates:

```kotlin
OpenRouterChatRequest(
    messages = listOf(
        OpenRouterMessage.system(
            "You are MedTrack assistant. For now, answer in plain text only. Tool use will be added later."
        ),
        OpenRouterMessage.user(message)
    )
)
```

This means:

- The model receives a system instruction.
- The model receives the user's message.
- No MCP tool schemas are sent yet.
- OpenRouter should return normal assistant text.

## Current Assistant Flow

After Part 5:

```text
AssistantScreen
  -> AssistantViewModel
  -> AiAssistantOrchestrator
  -> OpenRouterAiClient
  -> OpenRouter chat completions endpoint
  -> Text answer
```

MCP is not used yet from the AI flow.

## Error Handling Added

The client now handles:

```text
Missing API key
HTTP error response
OpenRouter error JSON
Socket timeout
Generic request failure
Empty model response
```

Current missing-key message:

```text
OpenRouter API key is not configured. Add openrouter.api.key to local.properties for this developer-only phase.
```

This is intentionally direct because this phase is developer-only.

## Assistant UI Copy Update

The assistant header changed from:

```text
Offline tool workflow preview
```

to:

```text
OpenRouter online AI
```

The empty state now explains that tool actions will be added after the
OpenRouter client is verified.

Why:

- The old text suggested offline/local AI.
- The new architecture is online OpenRouter only.
- We are not ready to claim MCP tool actions work through OpenRouter yet.

## What Part 5 Does Not Do

Part 5 does not:

- Add user-facing API key entry UI.
- Store the key securely.
- Validate the key with `GET /api/v1/key`.
- Send MCP tool definitions.
- Execute model-selected tools.
- Add write confirmation.
- Support multi-tool loops.

Those are later parts.

## Verification

Compile command:

```text
.\gradlew.bat :app:compileDebugKotlin
```

Result:

```text
BUILD SUCCESSFUL
```

There was one initial Gradle script issue:

```text
java.util.Properties was not resolved as written.
```

Fix:

```kotlin
import java.util.Properties
```

and an explicit:

```kotlin
file.inputStream().use { input -> load(input) }
```

After that, the build passed.

## How To Try This Phase

Add this to local `local.properties`:

```properties
openrouter.api.key=YOUR_OPENROUTER_KEY
```

Then rebuild and open the AI screen.

Important:

- Do not commit `local.properties`.
- Do not paste the key into source code.
- Do not share the key in logs or screenshots.

## Part 5 Conclusion

The app now has a text-only OpenRouter path.

The next step is:

```text
Part 6 - Convert MCP Catalog Tools Into OpenRouter Tools
```

Part 6 will not execute tools yet. It will only generate the tool schemas that
OpenRouter needs in order to select MedTrack MCP tools.
