package com.xiaozhi.simple.service

import android.util.Log

class OboePlayer {
    companion object {
        private const val TAG = "OboePlayer"
        
        init {
            System.loadLibrary("xiaozhi_audio")
            Log.d(TAG, "Native library loaded")
        }
    }
    
    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeEnqueueAudio(audioData: ShortArray)
    private external fun nativeClearQueue()
    private external fun nativeGetQueueSize(): Int
    private external fun nativeRelease()
    
    fun start(): Boolean {
        val result = nativeStart()
        Log.d(TAG, "Start: $result")
        return result
    }
    
    fun stop() {
        nativeStop()
        Log.d(TAG, "Stopped")
    }
    
    fun enqueueAudio(audioData: ShortArray) {
        nativeEnqueueAudio(audioData)
    }
    
    fun clearQueue() {
        nativeClearQueue()
        Log.d(TAG, "Queue cleared")
    }
    
    fun getQueueSize(): Int = nativeGetQueueSize()
    
    fun release() {
        nativeRelease()
        Log.d(TAG, "Released")
    }
}
