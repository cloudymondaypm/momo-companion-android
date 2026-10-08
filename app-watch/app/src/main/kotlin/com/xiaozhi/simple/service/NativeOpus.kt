package com.xiaozhi.simple.service

/** Raw Opus packets at 16 kHz, mono; no microphone is opened by this codec. */
class NativeOpus {
    companion object { init { System.loadLibrary("kiumo_opus") } }
    private var encoder = 0L
    private var decoder = 0L

    @Synchronized fun encoderInit(): Boolean {
        encoderRelease()
        encoder = createEncoder()
        return encoder != 0L
    }
    @Synchronized fun decoderInit(): Boolean {
        decoderRelease()
        decoder = createDecoder()
        return decoder != 0L
    }
    @Synchronized fun encode(pcm: ShortArray): ByteArray? =
        if (encoder == 0L) null else encodePacket(encoder, pcm)
    @Synchronized fun decode(packet: ByteArray): ShortArray? =
        if (decoder == 0L) null else decodePacket(decoder, packet)
    @Synchronized fun encoderRelease() {
        if (encoder != 0L) destroyEncoder(encoder)
        encoder = 0L
    }
    @Synchronized fun decoderRelease() {
        if (decoder != 0L) destroyDecoder(decoder)
        decoder = 0L
    }
    private external fun createEncoder(): Long
    private external fun createDecoder(): Long
    private external fun encodePacket(handle: Long, pcm: ShortArray): ByteArray?
    private external fun decodePacket(handle: Long, packet: ByteArray): ShortArray?
    private external fun destroyEncoder(handle: Long)
    private external fun destroyDecoder(handle: Long)
}
