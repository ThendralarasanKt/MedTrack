# MedTrack MCP Server Transport

This app now contains a first real MCP transport layer:

- transport type: Streamable HTTP subset
- bind address: `127.0.0.1`
- port: `8765`
- endpoint: `POST /mcp`

## Request Path

1. Android app process starts in `MedTrackApp`
2. `LocalMcpHttpServer.start()` opens a loopback `ServerSocket`
3. A client sends HTTP `POST /mcp`
4. `LocalMcpHttpServer` parses HTTP headers and JSON body
5. `McpRequestRouter.handle()` reads the JSON-RPC method
6. `initialize`, `tools/list`, or `tools/call` are dispatched
7. Tool methods call `MedTrackMcpService`
8. `MedTrackMcpService` calls repositories and DAOs
9. Room reads or updates SQLite data
10. Result is converted back to JSON-RPC and sent to the client

## Supported MCP Methods

- `initialize`
- `notifications/initialized`
- `ping`
- `tools/list`
- `tools/call`

## Tools

- `update_patient_room`
- `get_patient_current_process`

## Example Initialize Request

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-11-25",
    "capabilities": {},
    "clientInfo": {
      "name": "example-client",
      "version": "1.0.0"
    }
  }
}
```

## Example tools/call Request

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "update_patient_room",
    "arguments": {
      "patientName": "John",
      "currentRoomNumber": "101",
      "newRoomNumber": "102"
    }
  }
}
```

## Important Limits

- `GET /mcp` currently returns `405`, so SSE streaming is not implemented yet
- there is no session persistence layer yet
- the server runs only while the Android app process is alive
