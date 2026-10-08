package com.xiaozhi.simple.service
/** Each codec is owned and destroyed by a single audio worker. */
class OpusCodec(private val encoding: Boolean) : AutoCloseable {
    companion object { init { System.loadLibrary("fold5_opus") } }
    private var handle = create(encoding)
    private external fun create(encoding: Boolean): Long
    private external fun encodeNative(handle: Long, pcm: ShortArray): ByteArray
    private external fun decodeNative(handle: Long, packet: ByteArray): ShortArray
    private external fun destroy(handle: Long, encoding: Boolean)
    fun encode(pcm: ShortArray): ByteArray { check(encoding && handle != 0L); return encodeNative(handle, pcm) }
    fun decode(packet: ByteArray): ShortArray { check(!encoding && handle != 0L); return decodeNative(handle, packet) }
    override fun close() { if (handle != 0L) { destroy(handle, encoding); handle = 0L } }
}
