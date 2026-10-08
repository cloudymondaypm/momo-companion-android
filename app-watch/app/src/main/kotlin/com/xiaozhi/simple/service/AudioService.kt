package com.xiaozhi.simple.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioService(private val context: Context) {
    companion object {
        private const val TAG = "AudioService"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 960 // 60 ms of mono PCM
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val codec = NativeOpus()
    private var decoderReady = codec.decoderInit()
    private var encoderReady = false
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var oboePlayer: OboePlayer? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    var onAudioData: ((ByteArray) -> Unit)? = null

    @Synchronized
    fun playAudio(packet: ByteArray) {
        if (!decoderReady) decoderReady = codec.decoderInit()
        if (!decoderReady) return
        val pcm = codec.decode(packet) ?: return
        if (oboePlayer == null) {
            val player = OboePlayer()
            if (!player.start()) { player.release(); return }
            oboePlayer = player
        }
        _isPlaying.value = true
        oboePlayer?.enqueueAudio(pcm)
    }

    @Synchronized
    fun stopPlayback() {
        _isPlaying.value = false
        oboePlayer?.stop()
        oboePlayer?.release()
        oboePlayer = null
    }

    @Synchronized
    fun startRecording(): Boolean {
        if (_isRecording.value) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return false
        if (!encoderReady) encoderReady = codec.encoderInit()
        if (!encoderReady) return false
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) return false
        var recorder: AudioRecord? = null
        try {
            recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum * 2, FRAME_SIZE * 2 * 4))
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return false
            }
            recorder.startRecording()
        } catch (e: Exception) {
            recorder?.release()
            Log.e(TAG, "Cannot start microphone", e)
            return false
        }
        val activeRecorder = recorder ?: return false
        audioRecord = activeRecorder
        _isRecording.value = true
        recordingJob = scope.launch {
            val pcm = ShortArray(FRAME_SIZE)
            var filled = 0
            while (isActive) {
                val count = try {
                    activeRecorder.read(pcm, filled, FRAME_SIZE - filled)
                } catch (_: IllegalStateException) { break }
                if (!isActive) break
                if (count <= 0) {
                    synchronized(this@AudioService) {
                        if (audioRecord === activeRecorder) stopRecording()
                    }
                    break
                }
                filled += count
                if (filled == FRAME_SIZE) {
                    synchronized(this@AudioService) {
                        if (isActive && _isRecording.value && audioRecord === activeRecorder) {
                            codec.encode(pcm)?.let { onAudioData?.invoke(it) }
                        }
                    }
                    filled = 0
                }
            }
        }
        return true
    }

    @Synchronized
    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        val recorder = audioRecord
        audioRecord = null
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Microphone stop: ${e.message}")
        } finally {
            recorder?.release()
        }
    }

    @Synchronized
    fun release() {
        stopRecording()
        stopPlayback()
        scope.cancel()
        codec.encoderRelease()
        codec.decoderRelease()
        encoderReady = false
        decoderReady = false
    }
}
