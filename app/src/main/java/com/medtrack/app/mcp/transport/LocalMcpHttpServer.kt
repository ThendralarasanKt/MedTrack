package com.medtrack.app.mcp.transport

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONException
import org.json.JSONObject

@Singleton
class LocalMcpHttpServer @Inject constructor(
    private val requestRouter: McpRequestRouter
) {
    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var started = false

    private val connectionPool = Executors.newCachedThreadPool()
    private var acceptThread: Thread? = null

    fun start() {
        if (started) return

        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName(HOST), PORT))
        serverSocket = socket
        started = true

        acceptThread = Thread(
            {
                acceptLoop(socket)
            },
            "LocalMcpHttpServer-Accept"
        ).apply { start() }

        Log.i(TAG, "MCP server listening on http://$HOST:$PORT$PATH")
    }

    fun stop() {
        started = false
        closeQuietly(serverSocket)
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        connectionPool.shutdownNow()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (started) {
            try {
                val client = socket.accept()
                connectionPool.execute { handleClient(client) }
            } catch (_: SocketException) {
                return
            } catch (error: Exception) {
                Log.e(TAG, "Failed to accept MCP connection", error)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                val input = BufferedInputStream(client.getInputStream())
                val output = BufferedOutputStream(client.getOutputStream())
                val request = readRequest(input)
                val response = route(request)
                writeResponse(output, response)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to handle MCP request", error)
            }
        }
    }

    private fun route(request: HttpRequest): HttpResponse {
        if (request.path != PATH) {
            return HttpResponse.text(404, "Not Found")
        }

        val origin = request.headers["origin"]
        if (origin != null && !isAllowedOrigin(origin)) {
            return HttpResponse.text(403, "Forbidden")
        }

        return when (request.method) {
            "POST" -> handlePost(request)
            "GET" -> HttpResponse.text(405, "This MCP endpoint supports POST only.")
            "DELETE" -> HttpResponse.text(405, "Session termination is not implemented.")
            else -> HttpResponse.text(405, "Method Not Allowed")
        }
    }

    private fun handlePost(request: HttpRequest): HttpResponse {
        return try {
            val json = JSONObject(request.body)
            val protocolResponse = requestRouter.handle(json)
            val headers = linkedMapOf(
                "Content-Type" to "application/json",
                "MCP-Protocol-Version" to McpRequestRouter.MCP_PROTOCOL_VERSION
            )

            when {
                protocolResponse.body == null -> HttpResponse(
                    statusCode = protocolResponse.statusCode,
                    statusText = reasonPhrase(protocolResponse.statusCode),
                    headers = headers,
                    body = ""
                )

                else -> HttpResponse(
                    statusCode = protocolResponse.statusCode,
                    statusText = reasonPhrase(protocolResponse.statusCode),
                    headers = headers,
                    body = protocolResponse.body.toString()
                )
            }
        } catch (_: JSONException) {
            HttpResponse.text(400, "Invalid JSON")
        }
    }

    private fun readRequest(input: BufferedInputStream): HttpRequest {
        val requestLine = readLine(input)
        require(requestLine.isNotBlank()) { "Missing HTTP request line" }

        val parts = requestLine.split(" ")
        require(parts.size >= 2) { "Invalid HTTP request line: $requestLine" }

        val method = parts[0].uppercase(Locale.US)
        val path = parts[1]
        val headers = linkedMapOf<String, String>()

        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
            val separatorIndex = line.indexOf(':')
            if (separatorIndex > 0) {
                val name = line.substring(0, separatorIndex).trim().lowercase(Locale.US)
                val value = line.substring(separatorIndex + 1).trim()
                headers[name] = value
            }
        }

        val bodyLength = headers["content-length"]?.toIntOrNull() ?: 0
        val bodyBytes = ByteArray(bodyLength)
        var offset = 0
        while (offset < bodyLength) {
            val read = input.read(bodyBytes, offset, bodyLength - offset)
            if (read < 0) break
            offset += read
        }

        val body = String(bodyBytes, 0, offset, StandardCharsets.UTF_8)
        return HttpRequest(method = method, path = path, headers = headers, body = body)
    }

    private fun writeResponse(output: BufferedOutputStream, response: HttpResponse) {
        val bodyBytes = response.body.toByteArray(StandardCharsets.UTF_8)
        val headers = LinkedHashMap(response.headers)
        headers["Content-Length"] = bodyBytes.size.toString()
        headers["Connection"] = "close"

        val headerText = buildString {
            append("HTTP/1.1 ${response.statusCode} ${response.statusText}\r\n")
            headers.forEach { (name, value) ->
                append("$name: $value\r\n")
            }
            append("\r\n")
        }

        output.write(headerText.toByteArray(StandardCharsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    private fun readLine(input: BufferedInputStream): String {
        val buffer = StringBuilder()

        while (true) {
            val next = input.read()
            if (next == -1) break
            if (next == '\n'.code) break
            if (next != '\r'.code) {
                buffer.append(next.toChar())
            }
        }

        return buffer.toString()
    }

    private fun isAllowedOrigin(origin: String): Boolean =
        origin.startsWith("http://localhost", ignoreCase = true) ||
            origin.startsWith("http://127.0.0.1", ignoreCase = true)

    private fun reasonPhrase(code: Int): String =
        when (code) {
            200 -> "OK"
            202 -> "Accepted"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "OK"
        }

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
        }
    }

    companion object {
            private const val TAG = "LocalMcpHttpServer"
            const val HOST = "127.0.0.1"
            const val PORT = 8765
            const val PATH = "/mcp"
        }
}

data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String
)

data class HttpResponse(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val body: String
) {
    companion object {
        fun text(statusCode: Int, body: String): HttpResponse =
            HttpResponse(
                statusCode = statusCode,
                statusText = when (statusCode) {
                    404 -> "Not Found"
                    405 -> "Method Not Allowed"
                    403 -> "Forbidden"
                    400 -> "Bad Request"
                    else -> "OK"
                },
                headers = mapOf("Content-Type" to "text/plain; charset=utf-8"),
                body = body
            )
    }
}

data class McpProtocolResponse(
    val statusCode: Int,
    val body: JSONObject?
) {
    companion object {
        fun json(statusCode: Int, body: JSONObject): McpProtocolResponse =
            McpProtocolResponse(statusCode, body)

        fun empty(statusCode: Int): McpProtocolResponse =
            McpProtocolResponse(statusCode, null)
    }
}
