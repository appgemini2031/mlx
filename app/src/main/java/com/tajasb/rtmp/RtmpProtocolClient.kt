package com.tajasb.rtmp

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.util.Random
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class ParsedRtmpEndpoint(
    val scheme: String,
    val isTls: Boolean,
    val host: String,
    val port: Int,
    val app: String,
    val streamKey: String,
    val tcUrl: String
)

/**
 * Pure Kotlin RTMP / RTMPS Protocol Client & FLV/AMF0 Packetizer.
 * Supports YouTube (rtmp://), Facebook Live (rtmps://), Instagram Live (rtmps://),
 * TikTok Live Studio, and custom RTMP servers without external native dependencies.
 */
class RtmpProtocolClient {

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var outChunkSize: Int = 4096
    private var transactionId: Int = 1

    @Volatile
    var totalBytesSent: Long = 0L
        private set

    companion object {
        private const val RTMP_VERSION: Byte = 0x03
        private const val HANDSHAKE_SIZE = 1536

        private const val MSG_SET_CHUNK_SIZE: Byte = 0x01
        private const val MSG_AUDIO: Byte = 0x08
        private const val MSG_VIDEO: Byte = 0x09
        private const val MSG_AMF0_DATA: Byte = 0x12
        private const val MSG_AMF0_COMMAND: Byte = 0x14

        fun parseEndpoint(serverUrl: String, explicitStreamKey: String): Result<ParsedRtmpEndpoint> {
            val trimmedUrl = serverUrl.trim()
            if (trimmedUrl.isEmpty()) {
                return Result.failure(IllegalArgumentException("Server URL RTMP tidak boleh kosong"))
            }
            val normalizedUrl = if (!trimmedUrl.contains("://")) {
                "rtmp://$trimmedUrl"
            } else {
                trimmedUrl
            }

            return try {
                val uri = URI(normalizedUrl)
                val scheme = (uri.scheme ?: "rtmp").lowercase()
                if (scheme != "rtmp" && scheme != "rtmps") {
                    return Result.failure(IllegalArgumentException("Skema URL harus rtmp:// atau rtmps://"))
                }
                val isTls = scheme == "rtmps"
                val host = uri.host?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(IllegalArgumentException("Host server RTMP tidak valid"))
                val port = if (uri.port > 0) uri.port else if (isTls) 443 else 1935

                val rawPath = (uri.path ?: "").trim('/')
                val segments = rawPath.split('/').filter { it.isNotBlank() }

                val cleanExplicitKey = explicitStreamKey.trim().trimStart('/')
                val app: String
                val finalStreamKey: String

                if (cleanExplicitKey.isNotEmpty()) {
                    app = if (segments.isEmpty()) "live" else segments.joinToString("/")
                    finalStreamKey = cleanExplicitKey
                } else {
                    when {
                        segments.size >= 2 -> {
                            app = segments.dropLast(1).joinToString("/")
                            finalStreamKey = segments.last()
                        }
                        segments.size == 1 -> {
                            app = segments.first()
                            finalStreamKey = ""
                        }
                        else -> {
                            app = "live"
                            finalStreamKey = ""
                        }
                    }
                }

                val querySuffix = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
                val tcUrl = "$scheme://$host:$port/$app$querySuffix"

                Result.success(
                    ParsedRtmpEndpoint(
                        scheme = scheme,
                        isTls = isTls,
                        host = host,
                        port = port,
                        app = app + querySuffix,
                        streamKey = finalStreamKey,
                        tcUrl = tcUrl
                    )
                )
            } catch (e: Exception) {
                Result.failure(IllegalArgumentException("Format URL RTMP tidak valid: ${e.message}"))
            }
        }
    }

    /**
     * Tests TCP/TLS connectivity and RTMP C0/C1/S0/S1 handshake against the target server.
     */
    fun testServerReachability(endpoint: ParsedRtmpEndpoint, timeoutMs: Int = 6000): Result<String> {
        return try {
            openSocketAndHandshake(endpoint, timeoutMs)
            closeQuietly()
            Result.success("Server ${endpoint.host}:${endpoint.port} (${endpoint.scheme.uppercase()}) aktif & merespons Handshake RTMP v3.")
        } catch (e: Exception) {
            closeQuietly()
            Result.failure(e)
        }
    }

    /**
     * Connects, performs RTMP handshake, negotiates AMF0 connect/createStream/publish,
     * and sends initial stream metadata.
     */
    fun connectAndPublish(
        endpoint: ParsedRtmpEndpoint,
        width: Int,
        height: Int,
        videoBitrateKbps: Int,
        fps: Int,
        audioBitrateKbps: Int,
        antiHashTag: String? = null,
        timeoutMs: Int = 8000,
        onStageUpdate: (RtmpStreamState, String) -> Unit = { _, _ -> }
    ): Result<Unit> {
        return try {
            if (endpoint.streamKey.isBlank()) {
                return Result.failure(IllegalArgumentException("Stream Key wajib diisi untuk memulai siaran Live."))
            }

            onStageUpdate(RtmpStreamState.CONNECTING, "Menghubungi ${endpoint.host}:${endpoint.port}...")
            openSocketAndHandshake(endpoint, timeoutMs, onStageUpdate)

            // 1. Set high chunk size (4096 bytes) for smooth video streaming
            sendSetChunkSize(4096)

            // 2. AMF0 connect(app, tcUrl)
            onStageUpdate(RtmpStreamState.HANDSHAKING, "Mengautentikasi aplikasi '${endpoint.app}'...")
            sendConnectCommand(endpoint)

            // 3. releaseStream & FCPublish & createStream
            sendReleaseStreamCommand(endpoint.streamKey)
            sendFcPublishCommand(endpoint.streamKey)
            sendCreateStreamCommand()

            // 4. publish(streamKey, "live")
            onStageUpdate(RtmpStreamState.HANDSHAKING, "Membuka jalur publish stream...")
            sendPublishCommand(endpoint.streamKey)

            // 5. @setDataFrame("onMetaData")
            sendOnMetaData(
                width = width,
                height = height,
                videoBitrateKbps = videoBitrateKbps,
                fps = fps,
                audioBitrateKbps = audioBitrateKbps,
                encoderTag = antiHashTag ?: "Lavf60.16.100"
            )

            onStageUpdate(RtmpStreamState.LIVE, "Siaran Live RTMP Aktif ke ${endpoint.host}")
            Result.success(Unit)
        } catch (e: Exception) {
            closeQuietly()
            Result.failure(e)
        }
    }

    private fun openSocketAndHandshake(
        endpoint: ParsedRtmpEndpoint,
        timeoutMs: Int,
        onStageUpdate: (RtmpStreamState, String) -> Unit = { _, _ -> }
    ) {
        closeQuietly()
        val rawSocket = Socket()
        rawSocket.tcpNoDelay = true
        rawSocket.soTimeout = timeoutMs
        rawSocket.connect(InetSocketAddress(endpoint.host, endpoint.port), timeoutMs)

        val connectedSocket = if (endpoint.isTls) {
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = sslFactory.createSocket(rawSocket, endpoint.host, endpoint.port, true) as SSLSocket
            sslSocket.soTimeout = timeoutMs
            sslSocket.startHandshake()
            sslSocket
        } else {
            rawSocket
        }

        socket = connectedSocket
        input = DataInputStream(BufferedInputStream(connectedSocket.getInputStream(), 16384))
        output = DataOutputStream(BufferedOutputStream(connectedSocket.getOutputStream(), 32768))
        totalBytesSent = 0L
        transactionId = 1

        onStageUpdate(RtmpStreamState.HANDSHAKING, "Melakukan Handshake RTMP C0/C1/C2...")
        val out = output ?: throw IllegalStateException("Output stream belum siap")
        val inp = input ?: throw IllegalStateException("Input stream belum siap")

        // Send C0 + C1
        out.writeByte(RTMP_VERSION.toInt())
        val c1 = ByteArray(HANDSHAKE_SIZE)
        val rnd = Random()
        rnd.nextBytes(c1)
        // First 4 bytes = timestamp (0), next 4 bytes = zero
        for (i in 0 until 8) c1[i] = 0
        out.write(c1)
        out.flush()
        totalBytesSent += 1 + HANDSHAKE_SIZE

        // Read S0 + S1
        val s0 = inp.readByte()
        if (s0 != RTMP_VERSION) {
            throw IllegalStateException("Respon versi RTMP server tidak didukung ($s0)")
        }
        val s1 = ByteArray(HANDSHAKE_SIZE)
        inp.readFully(s1)

        // Send C2 (echo S1)
        out.write(s1)
        out.flush()
        totalBytesSent += HANDSHAKE_SIZE

        // Read S2
        val s2 = ByteArray(HANDSHAKE_SIZE)
        inp.readFully(s2)
    }

    private fun sendSetChunkSize(newSize: Int) {
        outChunkSize = newSize
        val buf = ByteBuffer.allocate(4).putInt(newSize).array()
        writeRtmpPacket(
            chunkStreamId = 2,
            timestampMs = 0,
            messageTypeId = MSG_SET_CHUNK_SIZE,
            messageStreamId = 0,
            payload = buf
        )
    }

    private fun sendConnectCommand(endpoint: ParsedRtmpEndpoint) {
        val body = ByteArrayOutputStream()
        val amf = DataOutputStream(body)
        writeAmfString(amf, "connect")
        writeAmfNumber(amf, (transactionId++).toDouble())

        // Command Object
        amf.writeByte(0x03) // AMF0 Object start
        writeAmfProperty(amf, "app") { writeAmfString(amf, endpoint.app) }
        writeAmfProperty(amf, "type") { writeAmfString(amf, "nonprivate") }
        writeAmfProperty(amf, "flashVer") { writeAmfString(amf, "FMLE/3.0 (compatible; FMSc/1.0)") }
        writeAmfProperty(amf, "tcUrl") { writeAmfString(amf, endpoint.tcUrl) }
        writeAmfObjectEnd(amf)

        writeRtmpPacket(
            chunkStreamId = 3,
            timestampMs = 0,
            messageTypeId = MSG_AMF0_COMMAND,
            messageStreamId = 0,
            payload = body.toByteArray()
        )
    }

    private fun sendReleaseStreamCommand(streamKey: String) {
        val body = ByteArrayOutputStream()
        val amf = DataOutputStream(body)
        writeAmfString(amf, "releaseStream")
        writeAmfNumber(amf, (transactionId++).toDouble())
        writeAmfNull(amf)
        writeAmfString(amf, streamKey)

        writeRtmpPacket(
            chunkStreamId = 3,
            timestampMs = 0,
            messageTypeId = MSG_AMF0_COMMAND,
            messageStreamId = 0,
            payload = body.toByteArray()
        )
    }

    private fun sendFcPublishCommand(streamKey: String) {
        val body = ByteArrayOutputStream()
        val amf = DataOutputStream(body)
        writeAmfString(amf, "FCPublish")
        writeAmfNumber(amf, (transactionId++).toDouble())
        writeAmfNull(amf)
        writeAmfString(amf, streamKey)

        writeRtmpPacket(
            chunkStreamId = 3,
            timestampMs = 0,
            messageTypeId = MSG_AMF0_COMMAND,
            messageStreamId = 0,
            payload = body.toByteArray()
        )
    }

    private fun sendCreateStreamCommand() {
        val body = ByteArrayOutputStream()
        val amf = DataOutputStream(body)
        writeAmfString(amf, "createStream")
        writeAmfNumber(amf, (transactionId++).toDouble())
        writeAmfNull(amf)

        writeRtmpPacket(
            chunkStreamId = 3,
            timestampMs = 0,
            messageTypeId = MSG_AMF0_COMMAND,
            messageStreamId = 0,
            payload = body.toByteArray()
        )
    }

    private fun sendPublishCommand(streamKey: String) {
        val body = ByteArrayOutputStream()
        val amf = DataOutputStream(body)
        writeAmfString(amf, "publish")
        writeAmfNumber(amf, 0.0)
        writeAmfNull(amf)
        writeAmfString(amf, streamKey)
        writeAmfString(amf, "live")

        writeRtmpPacket(
            chunkStreamId = 3,
            timestampMs = 0,
            messageTypeId = MSG_AMF0_COMMAND,
            messageStreamId = 1,
            payload = body.toByteArray()
        )
    }

    fun sendOnMetaData(
        width: Int,
        height: Int,
        videoBitrateKbps: Int,
        fps: Int,
        audioBitrateKbps: Int,
        encoderTag: String
    ) {
        val body = ByteArrayOutputStream()
        val amf = DataOutputStream(body)
        writeAmfString(amf, "@setDataFrame")
        writeAmfString(amf, "onMetaData")

        // ECMA Array (0x08)
        amf.writeByte(0x08)
        amf.writeInt(11)
        writeAmfProperty(amf, "width") { writeAmfNumber(amf, width.toDouble()) }
        writeAmfProperty(amf, "height") { writeAmfNumber(amf, height.toDouble()) }
        writeAmfProperty(amf, "videodatarate") { writeAmfNumber(amf, videoBitrateKbps.toDouble()) }
        writeAmfProperty(amf, "framerate") { writeAmfNumber(amf, fps.toDouble()) }
        writeAmfProperty(amf, "videocodecid") { writeAmfNumber(amf, 7.0) } // 7 = AVC (H.264)
        writeAmfProperty(amf, "audiodatarate") { writeAmfNumber(amf, audioBitrateKbps.toDouble()) }
        writeAmfProperty(amf, "audiosamplerate") { writeAmfNumber(amf, 44100.0) }
        writeAmfProperty(amf, "audiosamplesize") { writeAmfNumber(amf, 16.0) }
        writeAmfProperty(amf, "stereo") { writeAmfBoolean(amf, true) }
        writeAmfProperty(amf, "audiocodecid") { writeAmfNumber(amf, 10.0) } // 10 = AAC
        writeAmfProperty(amf, "encoder") { writeAmfString(amf, encoderTag) }
        writeAmfObjectEnd(amf)

        writeRtmpPacket(
            chunkStreamId = 5,
            timestampMs = 0,
            messageTypeId = MSG_AMF0_DATA,
            messageStreamId = 1,
            payload = body.toByteArray()
        )
    }

    /**
     * Sends FLV AVC Sequence Header (SPS + PPS) required before H.264 video frames.
     */
    fun sendAvcSequenceHeader(sps: ByteArray, pps: ByteArray, timestampMs: Long = 0L) {
        val cleanSps = stripAnnexBStartCode(sps)
        val cleanPps = stripAnnexBStartCode(pps)
        if (cleanSps.size < 4 || cleanPps.isEmpty()) return

        val body = ByteArrayOutputStream(cleanSps.size + cleanPps.size + 16)
        val out = DataOutputStream(body)
        // FrameType = 1 (keyframe), CodecID = 7 (AVC) -> 0x17
        out.writeByte(0x17)
        // AVCPacketType = 0 (AVC sequence header)
        out.writeByte(0x00)
        // CompositionTime = 0 (3 bytes)
        out.writeByte(0x00)
        out.writeByte(0x00)
        out.writeByte(0x00)

        // AVCDecoderConfigurationRecord
        out.writeByte(0x01) // configurationVersion = 1
        out.writeByte(cleanSps[1].toInt() and 0xFF) // AVCProfileIndication
        out.writeByte(cleanSps[2].toInt() and 0xFF) // profile_compatibility
        out.writeByte(cleanSps[3].toInt() and 0xFF) // AVCLevelIndication
        out.writeByte(0xFF) // lengthSizeMinusOne = 3 (4-byte NALU lengths)

        // SPS
        out.writeByte(0xE1) // numOfSequenceParameterSets = 1
        out.writeShort(cleanSps.size)
        out.write(cleanSps)

        // PPS
        out.writeByte(0x01) // numOfPictureParameterSets = 1
        out.writeShort(cleanPps.size)
        out.write(cleanPps)

        writeRtmpPacket(
            chunkStreamId = 6,
            timestampMs = timestampMs,
            messageTypeId = MSG_VIDEO,
            messageStreamId = 1,
            payload = body.toByteArray()
        )
    }

    /**
     * Sends an H.264 video frame (converts Annex-B start codes to 4-byte AVCC lengths if needed).
     */
    fun sendH264Frame(frameData: ByteArray, isKeyFrame: Boolean, timestampMs: Long) {
        if (frameData.isEmpty()) return
        val avccPayload = convertAnnexBToAvccIfNeeded(frameData)
        if (avccPayload.isEmpty()) return

        val flvVideo = ByteArray(5 + avccPayload.size)
        // 0x17 = Keyframe + AVC, 0x27 = Interframe + AVC
        flvVideo[0] = if (isKeyFrame) 0x17.toByte() else 0x27.toByte()
        flvVideo[1] = 0x01 // AVCPacketType = 1 (AVC NALU)
        flvVideo[2] = 0x00 // CompositionTime = 0
        flvVideo[3] = 0x00
        flvVideo[4] = 0x00
        System.arraycopy(avccPayload, 0, flvVideo, 5, avccPayload.size)

        writeRtmpPacket(
            chunkStreamId = 6,
            timestampMs = timestampMs,
            messageTypeId = MSG_VIDEO,
            messageStreamId = 1,
            payload = flvVideo
        )
    }

    /**
     * Sends FLV AAC Sequence Header (AudioSpecificConfig).
     */
    fun sendAacSequenceHeader(audioSpecificConfig: ByteArray, timestampMs: Long = 0L) {
        val asc = if (audioSpecificConfig.isNotEmpty()) {
            audioSpecificConfig
        } else {
            // Default AAC-LC 44.1kHz Stereo (0x12, 0x10)
            byteArrayOf(0x12.toByte(), 0x10.toByte())
        }
        val flvAudio = ByteArray(2 + asc.size)
        // SoundFormat=10 (AAC), SoundRate=3 (44kHz), SoundSize=1 (16-bit), SoundType=1 (Stereo) -> 0xAF
        flvAudio[0] = 0xAF.toByte()
        flvAudio[1] = 0x00 // AACPacketType = 0 (AAC sequence header)
        System.arraycopy(asc, 0, flvAudio, 2, asc.size)

        writeRtmpPacket(
            chunkStreamId = 4,
            timestampMs = timestampMs,
            messageTypeId = MSG_AUDIO,
            messageStreamId = 1,
            payload = flvAudio
        )
    }

    /**
     * Sends raw AAC audio frame wrapped in FLV audio tag.
     */
    fun sendAacFrame(rawAac: ByteArray, timestampMs: Long) {
        if (rawAac.isEmpty()) return
        val flvAudio = ByteArray(2 + rawAac.size)
        flvAudio[0] = 0xAF.toByte()
        flvAudio[1] = 0x01 // AACPacketType = 1 (AAC raw)
        System.arraycopy(rawAac, 0, flvAudio, 2, rawAac.size)

        writeRtmpPacket(
            chunkStreamId = 4,
            timestampMs = timestampMs,
            messageTypeId = MSG_AUDIO,
            messageStreamId = 1,
            payload = flvAudio
        )
    }

    @Synchronized
    private fun writeRtmpPacket(
        chunkStreamId: Int,
        timestampMs: Long,
        messageTypeId: Byte,
        messageStreamId: Int,
        payload: ByteArray
    ) {
        val out = output ?: throw IllegalStateException("Koneksi RTMP terputus")
        val ts = timestampMs.coerceAtLeast(0L).toInt()
        val hasExtendedTs = ts >= 0xFFFFFF
        val headerTs = if (hasExtendedTs) 0xFFFFFF else ts

        // Format 0 Basic Header (1 byte for CSID 2..63)
        out.writeByte(chunkStreamId and 0x3F)

        // Timestamp (3 bytes big-endian)
        out.writeByte((headerTs ushr 16) and 0xFF)
        out.writeByte((headerTs ushr 8) and 0xFF)
        out.writeByte(headerTs and 0xFF)

        // Message Length (3 bytes big-endian)
        val len = payload.size
        out.writeByte((len ushr 16) and 0xFF)
        out.writeByte((len ushr 8) and 0xFF)
        out.writeByte(len and 0xFF)

        // Message Type ID (1 byte)
        out.writeByte(messageTypeId.toInt() and 0xFF)

        // Message Stream ID (4 bytes little-endian per RTMP spec!)
        out.writeByte(messageStreamId and 0xFF)
        out.writeByte((messageStreamId ushr 8) and 0xFF)
        out.writeByte((messageStreamId ushr 16) and 0xFF)
        out.writeByte((messageStreamId ushr 24) and 0xFF)

        if (hasExtendedTs) {
            out.writeInt(ts)
        }

        // Write payload split by outChunkSize with Format 3 continuation headers (0xC0 | csid)
        var offset = 0
        while (offset < len) {
            if (offset > 0) {
                out.writeByte(0xC0 or (chunkStreamId and 0x3F))
                if (hasExtendedTs) {
                    out.writeInt(ts)
                }
            }
            val bytesToWrite = minOf(outChunkSize, len - offset)
            out.write(payload, offset, bytesToWrite)
            offset += bytesToWrite
        }

        out.flush()
        totalBytesSent += 12 + len
    }

    fun splitAnnexBNalus(data: ByteArray): List<ByteArray> {
        val nalus = mutableListOf<ByteArray>()
        var i = 0
        val startIndices = mutableListOf<Int>()
        val prefixLengths = mutableListOf<Int>()

        while (i <= data.size - 3) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) {
                    startIndices.add(i + 3)
                    prefixLengths.add(3)
                    i += 3
                    continue
                } else if (i <= data.size - 4 && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    startIndices.add(i + 4)
                    prefixLengths.add(4)
                    i += 4
                    continue
                }
            }
            i++
        }

        for (k in startIndices.indices) {
            val start = startIndices[k]
            val end = if (k + 1 < startIndices.size) {
                startIndices[k + 1] - prefixLengths[k + 1]
            } else {
                data.size
            }
            if (end > start) {
                nalus.add(data.copyOfRange(start, end))
            }
        }
        return nalus
    }

    private fun stripAnnexBStartCode(data: ByteArray): ByteArray {
        val nalus = splitAnnexBNalus(data)
        return if (nalus.isNotEmpty()) nalus.first() else data
    }

    private fun convertAnnexBToAvccIfNeeded(data: ByteArray): ByteArray {
        val nalus = splitAnnexBNalus(data)
        if (nalus.isEmpty()) {
            val out = ByteArrayOutputStream(4 + data.size)
            val dos = DataOutputStream(out)
            dos.writeInt(data.size)
            dos.write(data)
            return out.toByteArray()
        }

        val out = ByteArrayOutputStream(data.size + nalus.size * 4)
        val dos = DataOutputStream(out)
        for (nalu in nalus) {
            if (nalu.isNotEmpty()) {
                dos.writeInt(nalu.size)
                dos.write(nalu)
            }
        }
        return out.toByteArray()
    }

    private fun writeAmfString(out: DataOutputStream, value: String) {
        out.writeByte(0x02) // AMF0 String marker
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeShort(bytes.size)
        out.write(bytes)
    }

    private fun writeAmfNumber(out: DataOutputStream, value: Double) {
        out.writeByte(0x00) // AMF0 Number marker
        out.writeDouble(value)
    }

    private fun writeAmfBoolean(out: DataOutputStream, value: Boolean) {
        out.writeByte(0x01) // AMF0 Boolean marker
        out.writeByte(if (value) 1 else 0)
    }

    private fun writeAmfNull(out: DataOutputStream) {
        out.writeByte(0x05) // AMF0 Null marker
    }

    private inline fun writeAmfProperty(out: DataOutputStream, key: String, writeValue: () -> Unit) {
        val bytes = key.toByteArray(Charsets.UTF_8)
        out.writeShort(bytes.size)
        out.write(bytes)
        writeValue()
    }

    private fun writeAmfObjectEnd(out: DataOutputStream) {
        out.writeByte(0x00)
        out.writeByte(0x00)
        out.writeByte(0x09)
    }

    @Synchronized
    fun closeQuietly() {
        try {
            output?.flush()
        } catch (_: Throwable) {}
        try {
            input?.close()
        } catch (_: Throwable) {}
        try {
            output?.close()
        } catch (_: Throwable) {}
        try {
            socket?.close()
        } catch (_: Throwable) {}
        input = null
        output = null
        socket = null
    }
}
