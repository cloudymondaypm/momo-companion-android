package com.xiaozhi.simple.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** No microphone object exists before a PTT press. Workers own native codecs. */
class AudioService(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    var onAudioData: ((ByteArray) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onPlaybackFinished: (() -> Unit)? = null
    private class Recording(val recorder: AudioRecord, val codec: OpusCodec) { var job: Job? = null }
    private class Playback {
        val packets = Channel<ByteArray>(128)
        var job: Job? = null
        @Volatile var track: AudioTrack? = null
    }
    private var recording: Recording? = null
    private var playback: Playback? = null

    @Synchronized fun startRecording(): Boolean {
        if (recording != null) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        var recorder: AudioRecord? = null
        var encoder: OpusCodec? = null
        try {
            encoder = OpusCodec(true)
            val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(min > 0)
            recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 7680))
            check(recorder.state == AudioRecord.STATE_INITIALIZED)
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            val session = Recording(recorder, encoder)
            recording = session
            _isRecording.value = true
            session.job = scope.launch {
                try {
                    val pcm = ShortArray(960)
                    var filled = 0
                    while (isActive) {
                        val read = session.recorder.read(pcm, filled, pcm.size - filled)
                        if (!isActive) break
                        check(read > 0)
                        filled += read
                        if (filled == pcm.size) {
                            val packet = session.codec.encode(pcm)
                            synchronized(this@AudioService) {
                                if (recording === session) onAudioData?.invoke(packet)
                            }
                            filled = 0
                        }
                    }
                } catch (_: Exception) {
                    synchronized(this@AudioService) {
                        if (recording === session) {
                            stopRecording()
                            onError?.invoke("Microphone capture stopped. Press and hold to try again.")
                        }
                    }
                } finally {
                    runCatching { session.recorder.stop() }
                    session.recorder.release()
                    session.codec.close()
                }
            }
            return true
        } catch (_: Exception) {
            runCatching { recorder?.stop() }; recorder?.release(); encoder?.close()
            onError?.invoke("Cannot open microphone. Check microphone permission and privacy switch.")
            return false
        }
    }

    @Synchronized fun stopRecording() {
        val session = recording ?: return
        recording = null
        _isRecording.value = false
        session.job?.cancel()
        // Stop the physical microphone immediately; the worker releases its objects.
        runCatching { session.recorder.stop() }
    }

    @Synchronized fun startPlayback() {
        stopPlayback()
        val session = Playback()
        playback = session
        session.job = scope.launch {
            var track: AudioTrack? = null
            try {
                OpusCodec(false).use { decoder ->
                    val minimum = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    track = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(16000)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(maxOf(minimum, 7680)).setTransferMode(AudioTrack.MODE_STREAM).build()
                    val output = track!!
                    session.track = output
                    output.play()
                    var written = 0L
                    for (packet in session.packets) {
                        val pcm = decoder.decode(packet)
                        var offset = 0
                        while (offset < pcm.size && isActive) {
                            val count = output.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                            check(count >= 0)
                            if (count == 0) delay(5) else { offset += count; written += count }
                        }
                    }
                    // TTS stop marks end of incoming audio, not end of audible playback.
                    var waited = 0
                    while (isActive && output.playbackHeadPosition.toLong() < written && waited < 3000) {
                        delay(10); waited += 10
                    }
                }
            } catch (_: CancellationException) { /* interrupted by PTT/background */ }
            catch (_: Exception) { onError?.invoke("Unable to play server audio.") }
            finally {
                runCatching { track?.pause(); track?.flush() }
                track?.release()
                synchronized(this@AudioService) {
                    if (playback === session) { playback = null; onPlaybackFinished?.invoke() }
                }
            }
        }
    }
    @Synchronized fun playAudio(packet: ByteArray) {
        if (playback == null) startPlayback()
        if (playback?.packets?.trySend(packet)?.isFailure == true) {
            stopPlayback(); onError?.invoke("Audio queue overflow. Press PTT to retry.")
        }
    }
    @Synchronized fun finishPlayback() {
        val session = playback
        if (session == null) onPlaybackFinished?.invoke() else session.packets.close()
    }
    @Synchronized fun stopPlayback() {
        val session = playback ?: return
        playback = null
        session.packets.cancel(); session.job?.cancel()
        runCatching { session.track?.pause(); session.track?.flush() }
    }
    fun release() { stopRecording(); stopPlayback(); scope.cancel() }
}
