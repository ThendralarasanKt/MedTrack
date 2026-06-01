# Learning Inch by Inch

This file tracks the MCP learning journey step by step.

## Part 1 - How A Request Enters The App

Goal:

- Understand how an outside MCP client reaches the app.
- Understand why the request must be JSON.
- Understand what `LocalMcpHttpServer` does before any tool function runs.

Current focus:

- `LocalMcpHttpServer.start()`
- `acceptLoop(...)`
- `handleClient(...)`
- `readRequest(...)`
- `route(...)`
- `handlePost(...)`

Key idea:

The MCP server does not understand normal English text directly.

It receives an HTTP POST request at:

```text
http://127.0.0.1:8765/mcp
```

The request body must be JSON-RPC JSON, for example:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "update_patient_room",
    "arguments": {
      "patientName": "Ramesh",
      "currentRoomNumber": "438A",
      "newRoomNumber": "434A"
    }
  }
}
```

The English request:

```text
Move Ramesh from 438A to 434A
```

must first be converted into the JSON above by some client, assistant, or test code.

After that JSON reaches the app, `LocalMcpHttpServer` reads the HTTP request and passes the JSON body to `McpRequestRouter.handle(...)`.

### Doubt 1 - How Does Codex Know To Send JSON?

Codex knows because MCP is a JSON-RPC based protocol.

If Codex is connected to this app as an MCP client, it first asks the server:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

The app replies with available tool names and input schemas.

Those tool names and fields come from:

- `MedTrackMcpCatalog.tools`
- `McpJson.toToolJson()`

So Codex sees something like:

```json
{
  "name": "update_patient_room",
  "description": "Update the room number on the latest visit that matches the given patient name and current room number.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "patientName": { "type": "string" },
      "currentRoomNumber": { "type": "string" },
      "newRoomNumber": { "type": "string" }
    },
    "required": ["patientName", "currentRoomNumber", "newRoomNumber"]
  }
}
```

Then when the user says:

```text
Move Ramesh from 438A to 434A
```

Codex chooses the matching tool and fills the schema:

```json
{
  "patientName": "Ramesh",
  "currentRoomNumber": "438A",
  "newRoomNumber": "434A"
}
```

The MCP client wrapper then sends that as a JSON-RPC `tools/call` request.

Important:

- Codex does not send raw English directly to `LocalMcpHttpServer`.
- Codex reads the tool schema first.
- Codex chooses a tool.
- Codex fills that tool's arguments.
- The MCP client sends the final JSON-RPC request.

## Part 2 - How Codex Discovers Tools And Builds A Tool Call

Example user sentence:

```text
Move Ramesh from 4 to 2A
```

Before Codex can call `update_patient_room`, it must know what tools the app has.

### Step 2.1 - Codex Connects To The Server Address

The app opens this local address:

```text
http://127.0.0.1:8765/mcp
```

That address comes from:

```kotlin
const val HOST = "127.0.0.1"
const val PORT = 8765
const val PATH = "/mcp"
```

### Step 2.2 - Codex Sends A `tools/list` Request

Codex sends HTTP POST to `/mcp`.

HTTP body:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

Variable-style meaning:

```text
requestJson["jsonrpc"] = "2.0"
requestJson["id"] = 1
requestJson["method"] = "tools/list"
requestJson["params"] = {}
```

### Step 2.3 - Router Sees `method = tools/list`

Inside `McpRequestRouter.handle(requestJson)`:

```kotlin
val method = requestJson.optString("method")
```

Actual value:

```text
method = "tools/list"
```

So this branch runs:

```kotlin
"tools/list" -> successBody(id, listToolsResult())
```

### Step 2.4 - `listToolsResult()` Gets Tools From The Catalog

`listToolsResult()` calls:

```kotlin
mcpService.listTools()
```

`mcpService.listTools()` returns:

```kotlin
MedTrackMcpCatalog.tools
```

That catalog contains tool definitions like:

```kotlin
McpToolDescriptor(
    name = UPDATE_PATIENT_ROOM,
    description = "Update the room number on the latest visit...",
    inputFields = listOf(
        McpToolField("patientName", "string", true, "..."),
        McpToolField("currentRoomNumber", "string", true, "..."),
        McpToolField("newRoomNumber", "string", true, "...")
    )
)
```

### Step 2.5 - `McpJson.toToolJson()` Converts Kotlin Objects To JSON

The catalog is Kotlin objects.

Codex cannot read Kotlin objects directly.

So `McpJson.toToolJson()` converts this:

```kotlin
McpToolField("patientName", "string", true, "Exact patient name")
```

into JSON schema:

```json
{
  "type": "string",
  "description": "Exact patient name"
}
```

For `update_patient_room`, the final tool JSON looks like:

```json
{
  "name": "update_patient_room",
  "description": "Update the room number on the latest visit that matches the given patient name and current room number.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "patientName": {
        "type": "string",
        "description": "Exact patient name, matched case-insensitively."
      },
      "currentRoomNumber": {
        "type": "string",
        "description": "Current room number on the matched visit."
      },
      "newRoomNumber": {
        "type": "string",
        "description": "New room number to store on the matched visit."
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
```

### Step 2.6 - Codex Uses Intelligence To Choose The Tool

Codex reads:

```text
User said: "Move Ramesh from 4 to 2A"
```

Codex compares it with tool descriptions:

```text
update_patient_room = update room number
get_patient_current_process = load patient process
search_patient = search patients
```

Codex decides:

```text
toolName = "update_patient_room"
```

Then it fills the fields:

```text
patientName = "Ramesh"
currentRoomNumber = "4"
newRoomNumber = "2A"
```

### Step 2.7 - Codex Sends A `tools/call` Request

Codex does not create a `.json` file.

It creates a JSON object in memory and sends it as an HTTP POST body.

Final request body:

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "update_patient_room",
    "arguments": {
      "patientName": "Ramesh",
      "currentRoomNumber": "4",
      "newRoomNumber": "2A"
    }
  }
}
```

Variable-style meaning:

```text
requestJson["method"] = "tools/call"
params["name"] = "update_patient_room"
arguments["patientName"] = "Ramesh"
arguments["currentRoomNumber"] = "4"
arguments["newRoomNumber"] = "2A"
```

This is the point where the router will choose the actual tool function.

### Part 2 Understanding Check

Confirmed understanding:

Codex connects by sending an HTTP POST request to the running local server:

```text
http://127.0.0.1:8765/mcp
```

The server is already listening because `LocalMcpHttpServer.start()` opened the socket.

Codex first sends:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

Important correction:

`LocalMcpHttpServer` receives the HTTP request, but it does not itself decide `tools/list`.

The actual decision is:

```text
LocalMcpHttpServer.handlePost(...)
    -> requestRouter.handle(json)
    -> McpRequestRouter sees method = "tools/list"
    -> listToolsResult()
    -> mcpService.listTools()
    -> MedTrackMcpCatalog.tools
    -> McpJson.toToolJson()
```

Then Codex receives the tool schema and uses intelligence to choose the correct tool.

For:

```text
Move Ramesh from 4 to 2A
```

Codex chooses:

```text
toolName = "update_patient_room"
```

and fills:

```text
patientName = "Ramesh"
currentRoomNumber = "4"
newRoomNumber = "2A"
```

## Part 3 - How `tools/call` Chooses The Correct Function

Now Codex already knows the tool list.

User request:

```text
Move Ramesh from 4 to 2A
```

Codex decided:

```text
toolName = "update_patient_room"
patientName = "Ramesh"
currentRoomNumber = "4"
newRoomNumber = "2A"
```

### Step 3.1 - Codex Sends `tools/call`

HTTP POST body:

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "update_patient_room",
    "arguments": {
      "patientName": "Ramesh",
      "currentRoomNumber": "4",
      "newRoomNumber": "2A"
    }
  }
}
```

Variable-style:

```text
requestJson["jsonrpc"] = "2.0"
requestJson["id"] = 2
requestJson["method"] = "tools/call"
requestJson["params"]["name"] = "update_patient_room"
requestJson["params"]["arguments"]["patientName"] = "Ramesh"
requestJson["params"]["arguments"]["currentRoomNumber"] = "4"
requestJson["params"]["arguments"]["newRoomNumber"] = "2A"
```

### Step 3.2 - `LocalMcpHttpServer` Receives It

Same as before:

```text
LocalMcpHttpServer.handlePost(...)
    -> val json = JSONObject(request.body)
    -> requestRouter.handle(json)
```

Now:

```text
json["method"] = "tools/call"
```

### Step 3.3 - `McpRequestRouter.handle(...)` Reads The Method

Code:

```kotlin
val method = requestJson.optString("method")
val id = requestJson.opt("id")
val params = requestJson.optJSONObject("params") ?: JSONObject()
```

Actual values:

```text
method = "tools/call"
id = 2
params = {
  "name": "update_patient_room",
  "arguments": {
    "patientName": "Ramesh",
    "currentRoomNumber": "4",
    "newRoomNumber": "2A"
  }
}
```

Then this branch runs:

```kotlin
"tools/call" -> handleToolCall(id, params)
```

### Step 3.4 - `handleToolCall(...)` Reads Tool Name And Arguments

Code:

```kotlin
val toolName = params.optString("name")
val arguments = params.optJSONObject("arguments") ?: JSONObject()
```

Actual values:

```text
toolName = "update_patient_room"
arguments["patientName"] = "Ramesh"
arguments["currentRoomNumber"] = "4"
arguments["newRoomNumber"] = "2A"
```

### Step 3.5 - Router Chooses The Tool Branch

Code:

```kotlin
val result = when (toolName) {
    MedTrackMcpCatalog.SEARCH_PATIENT -> ...
    MedTrackMcpCatalog.UPDATE_PATIENT_ROOM -> ...
    MedTrackMcpCatalog.GET_PATIENT_CURRENT_PROCESS -> ...
    else -> return errorBody(id, -32602, "Unknown tool: $toolName")
}
```

Actual comparison:

```text
toolName = "update_patient_room"
MedTrackMcpCatalog.UPDATE_PATIENT_ROOM = "update_patient_room"
```

They match, so this branch runs:

```kotlin
MedTrackMcpCatalog.UPDATE_PATIENT_ROOM -> runBlocking {
    mcpService.updatePatientRoom(
        UpdatePatientRoomRequest(
            patientName = arg(arguments, "patientName", "patient_name"),
            currentRoomNumber = arg(arguments, "currentRoomNumber", "current_room_number"),
            newRoomNumber = arg(arguments, "newRoomNumber", "new_room_number")
        )
    )
}
```

### Step 3.6 - `arg(...)` Reads The Argument Values

Code:

```kotlin
private fun arg(arguments: JSONObject, camelCase: String, snakeCase: String): String =
    arguments.optStringOrNull(camelCase)
        ?: arguments.optStringOrNull(snakeCase)
        ?: ""
```

For patient name:

```text
camelCase = "patientName"
snakeCase = "patient_name"
arguments["patientName"] = "Ramesh"

result = "Ramesh"
```

For current room:

```text
camelCase = "currentRoomNumber"
snakeCase = "current_room_number"
arguments["currentRoomNumber"] = "4"

result = "4"
```

For new room:

```text
camelCase = "newRoomNumber"
snakeCase = "new_room_number"
arguments["newRoomNumber"] = "2A"

result = "2A"
```

### Step 3.7 - Kotlin Request Object Is Created

Code:

```kotlin
UpdatePatientRoomRequest(
    patientName = "Ramesh",
    currentRoomNumber = "4",
    newRoomNumber = "2A"
)
```

This is now no longer raw JSON.

It is a Kotlin object.

### Step 3.8 - Service Function Is Called

Code:

```kotlin
mcpService.updatePatientRoom(request)
```

Actual call:

```text
mcpService.updatePatientRoom(
    patientName = "Ramesh",
    currentRoomNumber = "4",
    newRoomNumber = "2A"
)
```

This is the exact point where router work ends and business/database work begins.

## Part 4 - Inside `updatePatientRoom(...)`

Previous part ended here:

```kotlin
mcpService.updatePatientRoom(
    UpdatePatientRoomRequest(
        patientName = "Ramesh",
        currentRoomNumber = "4",
        newRoomNumber = "2A"
    )
)
```

Now code enters:

```kotlin
suspend fun updatePatientRoom(request: UpdatePatientRoomRequest): UpdatePatientRoomResponse
```

### Step 4.1 - The Function Receives A Kotlin Request Object

Actual request:

```text
request.patientName = "Ramesh"
request.currentRoomNumber = "4"
request.newRoomNumber = "2A"
```

### Step 4.2 - Inputs Are Trimmed

Code:

```kotlin
val patientName = request.patientName.trim()
val currentRoomNumber = request.currentRoomNumber.trim()
val newRoomNumber = request.newRoomNumber.trim()
```

Actual values:

```text
patientName = "Ramesh"
currentRoomNumber = "4"
newRoomNumber = "2A"
```

If input had spaces:

```text
request.patientName = "  Ramesh  "
patientName = "Ramesh"
```

### Step 4.3 - Validation Checks

Check 1:

```kotlin
if (patientName.isBlank()) {
    return UpdatePatientRoomResponse(success = false, message = "patient_name is required")
}
```

For our example:

```text
patientName = "Ramesh"
isBlank = false
continue
```

Check 2:

```kotlin
if (currentRoomNumber.isBlank()) {
    return UpdatePatientRoomResponse(success = false, message = "current_room_number is required")
}
```

For our example:

```text
currentRoomNumber = "4"
isBlank = false
continue
```

Check 3:

```kotlin
if (newRoomNumber.isBlank()) {
    return UpdatePatientRoomResponse(success = false, message = "new_room_number is required")
}
```

For our example:

```text
newRoomNumber = "2A"
isBlank = false
continue
```

Check 4:

```kotlin
if (currentRoomNumber.equals(newRoomNumber, ignoreCase = true)) {
    return UpdatePatientRoomResponse(
        success = false,
        message = "new_room_number must be different from current_room_number"
    )
}
```

For our example:

```text
currentRoomNumber = "4"
newRoomNumber = "2A"
same = false
continue
```

### Step 4.4 - Service Asks Repository To Find Matching Visit

Code:

```kotlin
val context = clinicalRepository
    .findVisitContextsByPatientNameAndRoom(patientName, currentRoomNumber)
    .firstOrNull()
```

Actual call:

```text
clinicalRepository.findVisitContextsByPatientNameAndRoom(
    patientName = "Ramesh",
    roomNo = "4"
)
```

The repository function is:

```kotlin
suspend fun findVisitContextsByPatientNameAndRoom(
    patientName: String,
    roomNo: String
): List<PatientVisitContext> =
    visitDao.findVisitContextsByPatientNameAndRoom(patientName, roomNo)
```

It simply passes the values to `VisitDao`.

### Step 4.5 - DAO Runs SQLite SELECT Query

The DAO query searches:

```sql
FROM visits
INNER JOIN patients ON patients.id = visits.patientId
WHERE LOWER(TRIM(patients.name)) = LOWER(TRIM(:patientName))
  AND LOWER(TRIM(visits.roomNo)) = LOWER(TRIM(:roomNo))
ORDER BY visits.visitDate DESC, visits.visitTime DESC, visits.id DESC
```

Actual SQL parameter values:

```text
:patientName = "Ramesh"
:roomNo = "4"
```

Meaning:

```text
Find all visits where:
patient name equals Ramesh
and visit room equals 4

Newest visit first.
```

Example database rows:

```text
patients table:
id = 10, name = "Ramesh", age = 45

visits table:
id = 91, patientId = 10, roomNo = "4", visitDate = "2026-05-26", visitTime = "10:15"
```

DAO returns:

```text
List<PatientVisitContext> with 1 item
```

That item contains joined patient + visit data:

```text
context.patientId = 10
context.patientName = "Ramesh"
context.visitId = 91
context.roomNo = "4"
context.visitDate = "2026-05-26"
context.visitTime = "10:15"
```

### Step 4.6 - `.firstOrNull()` Chooses Latest Match

Because SQL ordered newest first:

```kotlin
.firstOrNull()
```

means:

```text
Take the newest matching visit.
```

Actual:

```text
context = PatientVisitContext(
    patientId = 10,
    patientName = "Ramesh",
    visitId = 91,
    roomNo = "4",
    ...
)
```

### Step 4.7 - If No Match Found

If DAO returns empty list:

```text
context = null
```

Then this return happens:

```kotlin
return UpdatePatientRoomResponse(
    success = false,
    message = buildRoomSuggestionMessage(patientName, currentRoomNumber),
    suggestedRoomNumber = ...
)
```

No database update happens in this case.

### Step 4.8 - If Match Found, Update SQLite

Code:

```kotlin
val updated = clinicalRepository.updateVisitRoomNo(context.visitId, newRoomNumber)
```

Actual values:

```text
context.visitId = 91
newRoomNumber = "2A"
```

Actual call:

```text
clinicalRepository.updateVisitRoomNo(
    visitId = 91,
    newRoomNo = "2A"
)
```

Repository passes to DAO:

```kotlin
visitDao.updateVisitRoomNo(visitId, newRoomNo)
```

DAO query:

```sql
UPDATE visits
SET roomNo = :newRoomNo
WHERE id = :visitId
```

Actual SQL parameter values:

```text
:newRoomNo = "2A"
:visitId = 91
```

Meaning:

```text
In visits table,
find row with id = 91,
change roomNo from "4" to "2A".
```

Before:

```text
visits.id = 91
visits.roomNo = "4"
```

After:

```text
visits.id = 91
visits.roomNo = "2A"
```

The DAO returns how many rows changed:

```text
updated = 1
```

### Step 4.9 - Service Checks Update Result

Code:

```kotlin
if (updated <= 0) {
    return UpdatePatientRoomResponse(
        success = false,
        message = "Failed to update patient room"
    )
}
```

For our example:

```text
updated = 1
updated <= 0 = false
continue
```

### Step 4.10 - Success Response Object Is Created

Code:

```kotlin
return UpdatePatientRoomResponse(
    success = true,
    message = "Room updated successfully for the latest matched visit.",
    patientId = context.patientId,
    patientName = context.patientName,
    visitId = context.visitId,
    oldRoomNumber = context.roomNo,
    newRoomNumber = newRoomNumber,
    visitDate = context.visitDate,
    visitTime = context.visitTime
)
```

Actual response:

```text
success = true
message = "Room updated successfully for the latest matched visit."
patientId = 10
patientName = "Ramesh"
visitId = 91
oldRoomNumber = "4"
newRoomNumber = "2A"
visitDate = "2026-05-26"
visitTime = "10:15"
```

This response is still a Kotlin object.

Next part will explain how this Kotlin response becomes JSON and returns to Codex.

## Part 4A - Where `McpModels.kt` Fits In The Flow

`McpModels.kt` does not run database queries.

It does not choose tools.

It does not receive HTTP requests.

It defines the shapes of data used by the MCP layer.

Think:

```text
McpModels.kt = forms / boxes / containers
```

### Request Models

Request models define what input a service function expects.

Example:

```kotlin
data class UpdatePatientRoomRequest(
    val patientName: String,
    val currentRoomNumber: String,
    val newRoomNumber: String
)
```

When JSON arrives:

```json
{
  "patientName": "Ramesh",
  "currentRoomNumber": "4",
  "newRoomNumber": "2A"
}
```

`McpRequestRouter` converts it into:

```kotlin
UpdatePatientRoomRequest(
    patientName = "Ramesh",
    currentRoomNumber = "4",
    newRoomNumber = "2A"
)
```

Then service receives this Kotlin object:

```kotlin
mcpService.updatePatientRoom(request)
```

### Response Models

Response models define what service function returns.

Example:

```kotlin
data class UpdatePatientRoomResponse(
    val success: Boolean,
    val message: String,
    val suggestedRoomNumber: String? = null,
    val patientId: Int? = null,
    val patientName: String? = null,
    val visitId: Int? = null,
    val oldRoomNumber: String? = null,
    val newRoomNumber: String? = null,
    val visitDate: String? = null,
    val visitTime: String? = null
)
```

After SQLite update succeeds, service creates:

```kotlin
UpdatePatientRoomResponse(
    success = true,
    message = "Room updated successfully for the latest matched visit.",
    patientId = 10,
    patientName = "Ramesh",
    visitId = 91,
    oldRoomNumber = "4",
    newRoomNumber = "2A",
    visitDate = "2026-05-26",
    visitTime = "10:15"
)
```

Then `McpJson.kt` converts this response model into JSON.

### Search Patient Models

Search request:

```kotlin
data class SearchPatientRequest(
    val query: String,
    val limit: Int = 10
)
```

Example:

```text
query = "Ramesh"
limit = 10
```

One search result item:

```kotlin
data class SearchPatientItem(
    val patientId: Int,
    val patientName: String,
    val age: Int,
    val sex: String,
    val contact: String,
    val address: String,
    val medicalHistory: String,
    val latestRoomNumber: String?
)
```

One actual patient:

```text
SearchPatientItem(
    patientId = 10,
    patientName = "Ramesh",
    age = 45,
    sex = "Male",
    contact = "9876543210",
    address = "Chennai",
    medicalHistory = "Diabetes",
    latestRoomNumber = "2A"
)
```

Whole search response:

```kotlin
data class SearchPatientResponse(
    val success: Boolean,
    val message: String,
    val query: String,
    val patients: List<SearchPatientItem> = emptyList()
)
```

Example:

```text
SearchPatientResponse(
    success = true,
    message = "Found 1 patient match(es) for 'Ramesh'.",
    query = "Ramesh",
    patients = listOf(SearchPatientItem(...))
)
```

So:

```text
SearchPatientRequest = input box
SearchPatientItem = one patient card/result
SearchPatientResponse = full result box containing many patient cards
```

### Why Models Are Important

Without these models, code would pass loose JSON everywhere.

With models:

```text
Router converts JSON -> Kotlin request model
Service works with Kotlin request model
Service returns Kotlin response model
McpJson converts response model -> JSON
```

So in the room-change flow:

```text
JSON arguments
  -> UpdatePatientRoomRequest
  -> updatePatientRoom(...)
  -> UpdatePatientRoomResponse
  -> JSON result
```

## Part 5 - Adding `create_patient` Tool Step By Step

Goal:

```text
Create a new patient record through MCP.
```

Example tool call arguments:

```json
{
  "patientName": "Kumar",
  "age": 35,
  "sex": "Male",
  "contact": "9876500000",
  "address": "",
  "medicalHistory": "diabetes"
}
```

### Step 5.1 - Add Request And Response Models

File:

```text
McpModels.kt
```

Why:

The router needs a Kotlin input box, and the service needs a Kotlin output box.

Request model:

```kotlin
data class CreatePatientRequest(
    val patientName: String,
    val age: Int,
    val sex: String,
    val contact: String,
    val address: String = "",
    val medicalHistory: String = ""
)
```

Meaning:

```text
patientName = required patient name
age = required age
sex = required sex
contact = required contact
address = optional
medicalHistory = optional
```

Response model:

```kotlin
data class CreatePatientResponse(...)
```

Meaning:

```text
Tell caller whether creation succeeded.
If success, return created patient details and new patientId.
If failure, return success=false and a message.
```

### Step 5.2 - Register The Tool In Catalog

File:

```text
MedTrackMcpCatalog.kt
```

Why:

Codex/MCP clients can only discover a tool if it appears in `MedTrackMcpCatalog.tools`.

Added:

```kotlin
const val CREATE_PATIENT = "create_patient"
```

and a descriptor with fields:

```text
patientName
age
sex
contact
address
medicalHistory
```

### Step 5.3 - Add Service Logic

File:

```text
MedTrackMcpService.kt
```

Why:

The service layer contains the actual business rules.

Actual flow:

```text
CreatePatientRequest
  -> trim text values
  -> validate required fields
  -> create PatientEntity
  -> patientRepository.insertPatient(patient)
  -> return CreatePatientResponse
```

Important validation:

```text
patientName must not be blank
age must be greater than zero
sex must not be blank
contact must not be blank
```

Database insert object:

```kotlin
PatientEntity(
    name = patientName,
    age = request.age,
    sex = normalizeSex(sex),
    contact = contact,
    address = address,
    medHistory = medicalHistory
)
```

Actual database call:

```kotlin
patientRepository.insertPatient(patient)
```

That reaches:

```text
PatientRepository.insertPatient(...)
  -> PatientDao.insertPatient(...)
  -> INSERT into patients table
```

### Step 5.4 - Add Router Branch

File:

```text
McpRequestRouter.kt
```

Why:

When `tools/call` arrives with:

```text
name = "create_patient"
```

the router must know which service function to call.

Added branch:

```kotlin
MedTrackMcpCatalog.CREATE_PATIENT -> runBlocking {
    mcpService.createPatient(
        CreatePatientRequest(...)
    )
}
```

### Step 5.5 - Add JSON Response Converter

File:

```text
McpJson.kt
```

Why:

The service returns a Kotlin `CreatePatientResponse`, but MCP callers need JSON.

Added:

```kotlin
internal fun CreatePatientResponse.toStructuredContent(): JSONObject
```

Meaning:

```text
CreatePatientResponse Kotlin object
  -> JSON structuredContent
```




Codex tools/call JSON
  -> LocalMcpHttpServer
  -> McpRequestRouter.handle()
  -> handleToolCall()
  -> MedTrackMcpService.updatePatientRoom()
  -> ClinicalRepository.updateVisitRoomNo()
  -> VisitDao.updateVisitRoomNo()
  -> SQLite visits.roomNo changes
