package com.xiaozhi.simple.service

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bounded foreground recordings for server STT; temporary files are always removed. */
class MomoVoiceService(private val context: Context) {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(70, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    var onLimit: (() -> Unit)? = null
    private val base = MomoPairingService.SERVER + "/api/device/voice/"
    init {
        context.cacheDir.listFiles()?.filter {
            it.name.startsWith("momo-recording-") || it.name.startsWith("momo-speech-")
        }?.forEach { it.delete() }
    }

    @Suppress("DEPRECATION")
    fun startRecording(): Boolean {
        cancelRecording()
        return try {
            val file = File.createTempFile("momo-recording-", ".m4a", context.cacheDir)
            recordingFile = file
            val current = MediaRecorder()
            recorder = current
            current.setAudioSource(MediaRecorder.AudioSource.MIC)
            current.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            current.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            current.setAudioSamplingRate(16000); current.setAudioEncodingBitRate(64000)
            current.setMaxDuration(20000); current.setMaxFileSize(1024 * 1024)
            current.setOutputFile(file.absolutePath)
            current.setOnInfoListener { _, _, _ -> onLimit?.invoke() }
            current.prepare(); current.start(); true
        } catch (_: Exception) { cancelRecording(); false }
    }

    fun finishRecording(): File? {
        val file = recordingFile
        val current = recorder ?: return null
        val valid = runCatching { current.stop() }.isSuccess
        current.release(); recorder = null; recordingFile = null
        return if (valid && file != null && file.length() > 0) file else { file?.delete(); null }
    }
    fun cancelRecording() { runCatching { recorder?.stop() }; recorder?.release(); recorder = null; recordingFile?.delete(); recordingFile = null }

    suspend fun transcribe(token: String, file: File, language: String): String = try {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("language", language)
            .addFormDataPart("file", "recording.m4a", withContext(Dispatchers.IO) {
                file.readBytes().toRequestBody("audio/mp4".toMediaType())
            }).build()
        val bytes = request(token, "transcribe", body, 65536, "application/json")
        com.google.gson.JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            .get("text")?.asString?.takeIf { it.isNotBlank() && it.codePointCount(0, it.length) <= 8000 }
            ?: throw IOException("Empty Momo transcription")
    } finally { file.delete() }

    suspend fun speak(token: String, text: String) {
        // Server adapters accept 1500 characters per synthesis request.
        for (chunk in text.chunked(1400)) {
            val body = com.google.gson.JsonObject().apply { addProperty("text", chunk) }.toString()
                .toRequestBody("application/json".toMediaType())
            val bytes = request(token, "speak", body, 2_000_000, "audio/mpeg")
            val file = withContext(Dispatchers.IO) {
                File.createTempFile("momo-speech-", ".mp3", context.cacheDir).also { it.writeBytes(bytes) }
            }
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val player = MediaPlayer()
                    continuation.invokeOnCancellation { player.release(); file.delete() }
                    player.setOnCompletionListener { player.release(); if (continuation.isActive) continuation.resume(Unit) }
                    player.setOnErrorListener { _, _, _ -> player.release(); if (continuation.isActive) continuation.resumeWithException(IOException("Momo audio playback failed")); true }
                    player.setOnPreparedListener { if (continuation.isActive) it.start() }
                    try { player.setDataSource(file.absolutePath); player.prepareAsync() }
                    catch (e: Exception) { player.release(); if (continuation.isActive) continuation.resumeWithException(e) }
                }
            } finally { file.delete() }
        }
    }

    private suspend fun request(token: String, action: String, body: RequestBody, limit: Long, type: String): ByteArray = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(base + action).header("X-Device-Token", token).post(body).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("Momo speech service unavailable")) }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        if (!it.isSuccessful) throw MomoChatService.ChatException(it.code, "Momo server speech failed (HTTP ${it.code}). Enable this agent's STT/TTS in the dashboard.")
                        if (!it.header("Content-Type").orEmpty().contains(type)) throw IOException("Invalid Momo speech response")
                        val source = it.body?.source() ?: throw IOException("Empty Momo speech response")
                        source.request(limit + 1)
                        if (source.buffer.size > limit) throw IOException("Momo speech response too large")
                        source.readByteArray()
                    } }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
    }
    fun release() { cancelRecording(); client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
