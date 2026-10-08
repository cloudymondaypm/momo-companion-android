#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <atomic>
#include <cstring>

#define LOG_TAG "OboePlayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class OboePlayer : public oboe::AudioStreamDataCallback {
public:
    static constexpr int SAMPLE_RATE = 16000;
    static constexpr int CHANNEL_COUNT = 1;
    static constexpr int RING_BUFFER_SIZE = 16000 * 2;
    static constexpr int JITTER_BUFFER_SAMPLES = 16000 * 300 / 1000;
    
    std::atomic<int> mCallbackCount{0};
    std::atomic<int> mUnderrunCount{0};
    std::atomic<int> mEnqueueCount{0};
    std::atomic<bool> mMuted{false};

    OboePlayer() {
        mRingBuffer = new int16_t[RING_BUFFER_SIZE];
        memset(mRingBuffer, 0, RING_BUFFER_SIZE * sizeof(int16_t));
    }
    
    ~OboePlayer() {
        stop();
        delete[] mRingBuffer;
    }

    bool start() {
        LOGI("Starting Oboe player...");
        
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               ->setPerformanceMode(oboe::PerformanceMode::None)
               ->setSharingMode(oboe::SharingMode::Shared)
               ->setFormat(oboe::AudioFormat::I16)
               ->setChannelCount(CHANNEL_COUNT)
               ->setSampleRate(SAMPLE_RATE)
               ->setBufferCapacityInFrames(16000)
               ->setDataCallback(this);

        oboe::Result result = builder.openStream(mStream);
        if (result != oboe::Result::OK) {
            LOGE("Failed to open stream: %s", oboe::convertToText(result));
            return false;
        }

        mActualSampleRate = mStream->getSampleRate();
        mFramesPerBurst = mStream->getFramesPerBurst();
        
        int bufferCapacity = mStream->getBufferCapacityInFrames();
        int targetBuffer = mFramesPerBurst * 8;
        if (targetBuffer <= bufferCapacity) {
            mStream->setBufferSizeInFrames(targetBuffer);
        }
        
        result = mStream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Failed to start stream: %s", oboe::convertToText(result));
            mStream->close();
            mStream.reset();
            return false;
        }

        mIsRunning = true;
        LOGI("Oboe started: sampleRate=%d, framesPerBurst=%d, bufferSize=%d, jitterBuffer=%d", 
             mActualSampleRate, mFramesPerBurst, mStream->getBufferSizeInFrames(), JITTER_BUFFER_SAMPLES);
        return true;
    }

    void stop() {
        mIsRunning = false;
        if (mStream) {
            mStream->requestStop();
            mStream->close();
            mStream.reset();
        }
        clearBuffer();
        LOGI("Oboe stopped");
    }


    void enqueueAudio(const int16_t* data, int numSamples) {
        mMuted.store(false, std::memory_order_release);
        
        int writePos = mWritePos.load(std::memory_order_relaxed);
        
        for (int i = 0; i < numSamples; i++) {
            mRingBuffer[writePos] = data[i];
            writePos = (writePos + 1) % RING_BUFFER_SIZE;
        }
        
        mWritePos.store(writePos, std::memory_order_release);
        mSamplesInBuffer.fetch_add(numSamples, std::memory_order_relaxed);
        
        int count = mEnqueueCount.fetch_add(1, std::memory_order_relaxed);
        if (count % 50 == 0) {
            int buffered = mSamplesInBuffer.load(std::memory_order_relaxed);
            LOGI("Enqueue[%d]: samples=%d, buffered=%d, callbacks=%d, underruns=%d", 
                 count, numSamples, buffered, mCallbackCount.load(), mUnderrunCount.load());
        }
    }

    void clearBuffer() {
        mMuted.store(true, std::memory_order_release);
        mWritePos.store(0, std::memory_order_relaxed);
        mReadPos.store(0, std::memory_order_relaxed);
        mSamplesInBuffer.store(0, std::memory_order_relaxed);
        mJitterBufferFilled = false;
        memset(mRingBuffer, 0, RING_BUFFER_SIZE * sizeof(int16_t));
        
        if (mStream) {
            mStream->requestFlush();
        }
        LOGI("Buffer cleared and flushed, muted");
    }

    int getBufferedSamples() {
        return mSamplesInBuffer.load(std::memory_order_relaxed);
    }

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream,
            void* audioData,
            int32_t numFrames) override {
        
        mCallbackCount.fetch_add(1, std::memory_order_relaxed);
        
        auto* output = static_cast<int16_t*>(audioData);
        int samplesNeeded = numFrames * CHANNEL_COUNT;
        
        if (mMuted.load(std::memory_order_acquire)) {
            memset(output, 0, samplesNeeded * sizeof(int16_t));
            return oboe::DataCallbackResult::Continue;
        }
        
        int buffered = mSamplesInBuffer.load(std::memory_order_acquire);
        
        if (!mJitterBufferFilled) {
            if (buffered >= JITTER_BUFFER_SAMPLES) {
                mJitterBufferFilled = true;
                LOGI("Jitter buffer filled: buffered=%d, needed=%d, callbacks=%d", 
                     buffered, JITTER_BUFFER_SAMPLES, mCallbackCount.load());
            } else {
                memset(output, 0, samplesNeeded * sizeof(int16_t));
                return oboe::DataCallbackResult::Continue;
            }
        }
        
        int readPos = mReadPos.load(std::memory_order_relaxed);
        int samplesToRead = (buffered >= samplesNeeded) ? samplesNeeded : buffered;
        
        if (samplesToRead < samplesNeeded) {
            int underruns = mUnderrunCount.fetch_add(1, std::memory_order_relaxed);
            if (underruns < 20 || underruns % 100 == 0) {
                LOGI("UNDERRUN[%d]: needed=%d, have=%d, buffered=%d", 
                     underruns, samplesNeeded, samplesToRead, buffered);
            }
        }
        
        for (int i = 0; i < samplesToRead; i++) {
            output[i] = mRingBuffer[readPos];
            readPos = (readPos + 1) % RING_BUFFER_SIZE;
        }
        
        if (samplesToRead < samplesNeeded) {
            memset(output + samplesToRead, 0, (samplesNeeded - samplesToRead) * sizeof(int16_t));
        }
        
        mReadPos.store(readPos, std::memory_order_relaxed);
        mSamplesInBuffer.fetch_sub(samplesToRead, std::memory_order_relaxed);
        
        return oboe::DataCallbackResult::Continue;
    }

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    int16_t* mRingBuffer = nullptr;
    std::atomic<int> mWritePos{0};
    std::atomic<int> mReadPos{0};
    std::atomic<int> mSamplesInBuffer{0};
    std::atomic<bool> mIsRunning{false};
    bool mJitterBufferFilled = false;
    int mActualSampleRate = 0;
    int mFramesPerBurst = 0;
};

static OboePlayer* gPlayer = nullptr;


extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_xiaozhi_simple_service_OboePlayer_nativeStart(JNIEnv* env, jobject thiz) {
    if (gPlayer != nullptr) {
        gPlayer->stop();
        delete gPlayer;
        gPlayer = nullptr;
    }
    gPlayer = new OboePlayer();
    return gPlayer->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_xiaozhi_simple_service_OboePlayer_nativeStop(JNIEnv* env, jobject thiz) {
    if (gPlayer != nullptr) {
        gPlayer->stop();
    }
}

JNIEXPORT void JNICALL
Java_com_xiaozhi_simple_service_OboePlayer_nativeEnqueueAudio(
        JNIEnv* env, jobject thiz, jshortArray audioData) {
    if (gPlayer == nullptr) return;
    
    jsize length = env->GetArrayLength(audioData);
    jshort* data = env->GetShortArrayElements(audioData, nullptr);
    
    gPlayer->enqueueAudio(data, length);
    
    env->ReleaseShortArrayElements(audioData, data, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_xiaozhi_simple_service_OboePlayer_nativeClearQueue(JNIEnv* env, jobject thiz) {
    if (gPlayer != nullptr) {
        gPlayer->clearBuffer();
    }
}

JNIEXPORT jint JNICALL
Java_com_xiaozhi_simple_service_OboePlayer_nativeGetQueueSize(JNIEnv* env, jobject thiz) {
    if (gPlayer == nullptr) return 0;
    return gPlayer->getBufferedSamples();
}

JNIEXPORT void JNICALL
Java_com_xiaozhi_simple_service_OboePlayer_nativeRelease(JNIEnv* env, jobject thiz) {
    if (gPlayer != nullptr) {
        delete gPlayer;
        gPlayer = nullptr;
    }
}

}
