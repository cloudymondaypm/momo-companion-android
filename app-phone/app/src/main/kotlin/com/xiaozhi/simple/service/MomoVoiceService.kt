package com.xiaozhi.simple.service

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bounded foreground recordings for server STT; temporary files are always removed. */
class MomoVoiceService(private val context: Context) {
    private val api = MomoSpeechApi()
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    var onLimit: (() -> Unit)? = null
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
            current.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) onLimit?.invoke()
            }
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
        val audio = withContext(Dispatchers.IO) {
            require(file.length() in 1..1024 * 1024) { "Momo recording is empty or too large." }
            file.readBytes()
        }
        api.transcribe(token, audio, language)
    } finally { file.delete() }

    suspend fun speak(token: String, text: String) {
        // Server adapters accept 1500 characters per synthesis request.
        for (chunk in text.chunked(1400)) {
            val bytes = api.speak(token, chunk)
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

    fun release() { cancelRecording(); api.release() }
}
