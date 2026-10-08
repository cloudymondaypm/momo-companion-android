#include <jni.h>
#include <opus.h>
#include <cstdint>
#include <vector>

#define JNI_NAME(name) Java_com_xiaozhi_simple_service_NativeOpus_##name

extern "C" JNIEXPORT jlong JNICALL JNI_NAME(createEncoder)(JNIEnv*, jobject) {
    int error = OPUS_OK;
    auto* encoder = opus_encoder_create(16000, 1, OPUS_APPLICATION_VOIP, &error);
    if (error != OPUS_OK || !encoder) {
        if (encoder) opus_encoder_destroy(encoder);
        return 0;
    }
    opus_encoder_ctl(encoder, OPUS_SET_BITRATE(24000));
    opus_encoder_ctl(encoder, OPUS_SET_COMPLEXITY(5));
    return reinterpret_cast<intptr_t>(encoder);
}

extern "C" JNIEXPORT jlong JNICALL JNI_NAME(createDecoder)(JNIEnv*, jobject) {
    int error = OPUS_OK;
    auto* decoder = opus_decoder_create(16000, 1, &error);
    if (error != OPUS_OK || !decoder) {
        if (decoder) opus_decoder_destroy(decoder);
        return 0;
    }
    return reinterpret_cast<intptr_t>(decoder);
}

extern "C" JNIEXPORT jbyteArray JNICALL JNI_NAME(encodePacket)(
    JNIEnv* env, jobject, jlong handle, jshortArray input) {
    if (!handle || !input || env->GetArrayLength(input) != 960) return nullptr;
    opus_int16 pcm[960];
    env->GetShortArrayRegion(input, 0, 960, pcm);
    if (env->ExceptionCheck()) return nullptr;
    unsigned char packet[4000];
    int count = opus_encode(reinterpret_cast<OpusEncoder*>(static_cast<intptr_t>(handle)),
                            pcm, 960, packet, sizeof(packet));
    if (count <= 0) return nullptr;
    jbyteArray result = env->NewByteArray(count);
    if (result) env->SetByteArrayRegion(result, 0, count, reinterpret_cast<jbyte*>(packet));
    return result;
}

extern "C" JNIEXPORT jshortArray JNICALL JNI_NAME(decodePacket)(
    JNIEnv* env, jobject, jlong handle, jbyteArray input) {
    if (!handle || !input) return nullptr;
    int length = env->GetArrayLength(input);
    if (length <= 0 || length > 65536) return nullptr;
    std::vector<unsigned char> packet(length);
    env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte*>(packet.data()));
    if (env->ExceptionCheck()) return nullptr;
    // Accept up to the protocol maximum of 120 ms at 16 kHz.
    opus_int16 pcm[1920];
    int count = opus_decode(reinterpret_cast<OpusDecoder*>(static_cast<intptr_t>(handle)),
                            packet.data(), length, pcm, 1920, 0);
    if (count <= 0) return nullptr;
    jshortArray result = env->NewShortArray(count);
    if (result) env->SetShortArrayRegion(result, 0, count, pcm);
    return result;
}

extern "C" JNIEXPORT void JNICALL JNI_NAME(destroyEncoder)(JNIEnv*, jobject, jlong handle) {
    if (handle) opus_encoder_destroy(reinterpret_cast<OpusEncoder*>(static_cast<intptr_t>(handle)));
}
extern "C" JNIEXPORT void JNICALL JNI_NAME(destroyDecoder)(JNIEnv*, jobject, jlong handle) {
    if (handle) opus_decoder_destroy(reinterpret_cast<OpusDecoder*>(static_cast<intptr_t>(handle)));
}
