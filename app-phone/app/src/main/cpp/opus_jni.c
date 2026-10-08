#include <jni.h>
#include <stdint.h>
#include <opus.h>
static void fail(JNIEnv *env, const char *message) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), message);
}
JNIEXPORT jlong JNICALL Java_com_xiaozhi_simple_service_OpusCodec_create(JNIEnv *env, jobject obj, jboolean encode) {
    int error = OPUS_OK;
    void *handle;
    if (encode) {
        OpusEncoder *enc = opus_encoder_create(16000, 1, OPUS_APPLICATION_VOIP, &error);
        handle = enc;
        if (enc) {
            opus_encoder_ctl(enc, OPUS_SET_BITRATE(24000));
            opus_encoder_ctl(enc, OPUS_SET_COMPLEXITY(5));
        }
    } else { handle = opus_decoder_create(16000, 1, &error); }
    if (error != OPUS_OK || !handle) { fail(env, "Cannot initialize Opus"); return 0; }
    return (jlong)(intptr_t)handle;
}
JNIEXPORT jbyteArray JNICALL Java_com_xiaozhi_simple_service_OpusCodec_encodeNative(JNIEnv *env, jobject obj, jlong handle, jshortArray pcm) {
    if (!handle || (*env)->GetArrayLength(env, pcm) != 960) { fail(env, "Expected 960 PCM samples"); return NULL; }
    unsigned char encoded[1275];
    jshort *samples = (*env)->GetShortArrayElements(env, pcm, NULL);
    int count = opus_encode((OpusEncoder *)(intptr_t)handle, samples, 960, encoded, sizeof(encoded));
    (*env)->ReleaseShortArrayElements(env, pcm, samples, JNI_ABORT);
    if (count < 0) { fail(env, "Opus encoding failed"); return NULL; }
    jbyteArray out = (*env)->NewByteArray(env, count);
    (*env)->SetByteArrayRegion(env, out, 0, count, (const jbyte *)encoded);
    return out;
}
JNIEXPORT jshortArray JNICALL Java_com_xiaozhi_simple_service_OpusCodec_decodeNative(JNIEnv *env, jobject obj, jlong handle, jbyteArray packet) {
    int size = (*env)->GetArrayLength(env, packet);
    if (!handle || size < 1 || size > 8192) { fail(env, "Invalid Opus packet"); return NULL; }
    opus_int16 pcm[1920];
    jbyte *data = (*env)->GetByteArrayElements(env, packet, NULL);
    int count = opus_decode((OpusDecoder *)(intptr_t)handle, (unsigned char *)data, size, pcm, 1920, 0);
    (*env)->ReleaseByteArrayElements(env, packet, data, JNI_ABORT);
    if (count < 0) { fail(env, "Invalid Opus audio from server"); return NULL; }
    jshortArray out = (*env)->NewShortArray(env, count);
    (*env)->SetShortArrayRegion(env, out, 0, count, pcm);
    return out;
}
JNIEXPORT void JNICALL Java_com_xiaozhi_simple_service_OpusCodec_destroy(JNIEnv *env, jobject obj, jlong handle, jboolean encode) {
    if (!handle) return;
    if (encode) opus_encoder_destroy((OpusEncoder *)(intptr_t)handle);
    else opus_decoder_destroy((OpusDecoder *)(intptr_t)handle);
}
