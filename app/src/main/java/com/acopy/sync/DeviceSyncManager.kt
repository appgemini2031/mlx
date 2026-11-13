package com.acopy.sync

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object DeviceSyncManager {

    const val DEFAULT_PORT = 8765

    data class SyncNote(
        val id: String,
        val title: String,
        val content: String,
        val isDeleted: Boolean = false,
        val deletedAt: Long = 0L
    )

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var serverThread: Thread? = null

    val isServerActive: Boolean
        get() = isRunning.get() && serverSocket != null && !serverSocket!!.isClosed

    val isServerRunning: Boolean
        get() = isServerActive

    /**
     * Resolves the local IP address on Wi-Fi or local Hotspot.
     */
    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (_: Exception) {}

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }

    /**
     * Starts the lightweight embedded LAN sync server.
     */
    @Synchronized
    fun startServer(
        currentNotesProvider: () -> List<SyncNote>,
        onNotesReceived: (List<SyncNote>, String) -> Unit
    ): Boolean {
        if (isServerActive) return true

        return try {
            val ss = ServerSocket(DEFAULT_PORT)
            serverSocket = ss
            isRunning.set(true)

            val thread = Thread {
                while (isRunning.get() && !ss.isClosed) {
                    try {
                        val client = ss.accept()
                        handleClientConnection(client, currentNotesProvider, onNotesReceived)
                    } catch (_: Exception) {
                        break
                    }
                }
                isRunning.set(false)
            }
            serverThread = thread
            thread.start()
            true
        } catch (e: Exception) {
            isRunning.set(false)
            false
        }
    }

    /**
     * Stops the LAN sync server.
     */
    @Synchronized
    fun stopServer() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverThread = null
    }

    private fun handleClientConnection(
        socket: Socket,
        currentNotesProvider: () -> List<SyncNote>,
        onNotesReceived: (List<SyncNote>, String) -> Unit
    ) {
        Thread {
            try {
                socket.soTimeout = 10000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
                val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")

                val requestLine = reader.readLine() ?: return@Thread
                val parts = requestLine.split(" ")
                val method = if (parts.isNotEmpty()) parts[0].uppercase(Locale.US) else "GET"
                val path = if (parts.size > 1) parts[1] else "/"

                var contentLength = 0
                var line = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    val lower = line.lowercase(Locale.US)
                    if (lower.startsWith("content-length:")) {
                        contentLength = lower.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }

                when {
                    path.startsWith("/api/notes") && method == "GET" -> {
                        val notes = currentNotesProvider()
                        val jsonStr = serializeNotesToJson(notes)
                        sendHttpResponse(writer, 200, "application/json; charset=utf-8", jsonStr)
                    }

                    path.startsWith("/api/notes") && method == "POST" -> {
                        val body = if (contentLength > 0) {
                            val buf = CharArray(contentLength)
                            var readTotal = 0
                            while (readTotal < contentLength) {
                                val r = reader.read(buf, readTotal, contentLength - readTotal)
                                if (r < 0) break
                                readTotal += r
                            }
                            String(buf, 0, readTotal)
                        } else {
                            ""
                        }

                        val received = deserializeNotesFromJson(body)
                        val senderIp = socket.inetAddress?.hostAddress ?: "Device"
                        onNotesReceived(received, senderIp)
                        val responseJson = "{\"status\":\"success\",\"imported\":${received.size}}"
                        sendHttpResponse(writer, 200, "application/json; charset=utf-8", responseJson)
                    }

                    path.startsWith("/api/ping") -> {
                        val json = "{\"status\":\"ok\",\"app\":\"Pintasan\",\"port\":$DEFAULT_PORT}"
                        sendHttpResponse(writer, 200, "application/json; charset=utf-8", json)
                    }

                    else -> {
                        // Serve friendly HTML dashboard
                        val notes = currentNotesProvider()
                        val html = buildWebDashboardHtml(notes)
                        sendHttpResponse(writer, 200, "text/html; charset=utf-8", html)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {}
            }
        }.start()
    }

    private fun sendHttpResponse(
        writer: OutputStreamWriter,
        statusCode: Int,
        contentType: String,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val statusText = if (statusCode == 200) "OK" else "Error"
        writer.write("HTTP/1.1 $statusCode $statusText\r\n")
        writer.write("Content-Type: $contentType\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Access-Control-Allow-Origin: *\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.write(body)
        writer.flush()
    }

    /**
     * Pulls notes from a sender device over Wi-Fi/LAN via HTTP.
     */
    fun fetchNotesFromDevice(targetIp: String, port: Int = DEFAULT_PORT): Result<List<SyncNote>> {
        return try {
            val cleanIp = targetIp.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
            val url = URL("http://$cleanIp:$port/api/notes")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val text = reader.readText()
                reader.close()
                val list = deserializeNotesFromJson(text)
                Result.success(list)
            } else {
                Result.failure(Exception("Gagal menghubungi device (HTTP $code)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends notes to another device over Wi-Fi/LAN via HTTP POST.
     */
    fun sendNotesToDevice(targetIp: String, notes: List<SyncNote>, port: Int = DEFAULT_PORT): Result<Int> {
        return try {
            val cleanIp = targetIp.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
            val url = URL("http://$cleanIp:$port/api/notes")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            val jsonStr = serializeNotesToJson(notes)
            val os = conn.outputStream
            os.write(jsonStr.toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val code = conn.responseCode
            if (code == 200) {
                Result.success(notes.size)
            } else {
                Result.failure(Exception("Device tujuan menolak (HTTP $code)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun serializeNotesToJson(notes: List<SyncNote>): String {
        val root = JSONObject()
        root.put("app", "Pintasan")
        root.put("timestamp", System.currentTimeMillis())
        val arr = JSONArray()
        for (n in notes) {
            val obj = JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("isDeleted", n.isDeleted)
                put("deletedAt", n.deletedAt)
            }
            arr.put(obj)
        }
        root.put("notes", arr)
        return root.toString()
    }

    fun deserializeNotesFromJson(jsonStr: String): List<SyncNote> {
        if (jsonStr.isBlank()) return emptyList()
        val parsed = com.acopy.data.BackupRestoreManager.parseBackup(jsonStr)
        return parsed.notes.map { SyncNote(it.id, it.title, it.content, it.isDeleted, it.deletedAt) }
    }

    fun exportNotesToJson(notes: List<SyncNote>): String = serializeNotesToJson(notes)
    fun parseNotesFromJson(jsonStr: String): List<SyncNote> = deserializeNotesFromJson(jsonStr)

    private fun buildWebDashboardHtml(notes: List<SyncNote>): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
        sb.append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
        sb.append("<title>Pintasan - Sinkronisasi Catatan Antar Device</title>")
        sb.append("<style>")
        sb.append("body{background:#0F172A;color:#F8FAFC;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;padding:16px;margin:0;}")
        sb.append(".header{border-bottom:1px solid #334155;padding-bottom:12px;margin-bottom:16px;}")
        sb.append(".card{background:#1E293B;border:1px solid #334155;border-radius:10px;padding:12px;margin-bottom:12px;}")
        sb.append(".title{color:#38BDF8;font-weight:bold;font-size:15px;margin-bottom:6px;}")
        sb.append(".content{color:#E2E8F0;font-size:13px;white-space:pre-wrap;background:#0F172A;padding:8px;border-radius:6px;margin-bottom:8px;}")
        sb.append(".btn{background:#0284C7;color:#fff;border:none;padding:6px 12px;border-radius:6px;font-size:12px;font-weight:bold;cursor:pointer;}")
        sb.append("</style></head><body>")
        sb.append("<div class='header'><h2>📱 Pintasan - Sinkronisasi 3 Device</h2>")
        sb.append("<p style='color:#94A3B8;font-size:12px;'>Tersedia ${notes.size} catatan di HP ini. Anda bisa salin langsung di browser ini.</p></div>")

        if (notes.isEmpty()) {
            sb.append("<p style='color:#64748B;'>Belum ada catatan tersimpan di HP ini.</p>")
        } else {
            for ((idx, n) in notes.withIndex()) {
                val titleEsc = n.title.ifBlank { "Catatan #${idx + 1}" }.replace("<", "&lt;").replace(">", "&gt;")
                val contentEsc = n.content.replace("<", "&lt;").replace(">", "&gt;")
                sb.append("<div class='card'>")
                sb.append("<div class='title'>$titleEsc</div>")
                sb.append("<div class='content' id='c_$idx'>$contentEsc</div>")
                sb.append("<button class='btn' onclick='navigator.clipboard.writeText(document.getElementById(\"c_$idx\").innerText);this.innerText=\"✓ Disalin\";setTimeout(()=>this.innerText=\"Salin\",1500)'>📋 Salin</button>")
                sb.append("</div>")
            }
        }
        sb.append("</body></html>")
        return sb.toString()
    }
}
