#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <mutex>
#include <cstring>

#define LOG_TAG "edgeproc"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

using namespace cv;

static std::mutex gMutex;
static int gWidth = 0;
static int gHeight = 0;
static unsigned char* gOutBuffer = nullptr;
static size_t gOutBufferSize = 0;

extern "C"
JNIEXPORT void JNICALL
Java_com_example_rudviwer_MainActivity_1nativeInit(
        JNIEnv* env,
        jobject /*thiz*/,
        jint width,
        jint height,
        jobject outBuffer
) {
    std::lock_guard<std::mutex> lock(gMutex);

    gWidth = width;
    gHeight = height;
    gOutBuffer = nullptr;
    gOutBufferSize = 0;

    if (outBuffer != nullptr) {
        void* addr = env->GetDirectBufferAddress(outBuffer);
        if (addr) {
            gOutBuffer = static_cast<unsigned char*>(addr);
            gOutBufferSize = static_cast<size_t>(width) * static_cast<size_t>(height) * 4;
            LOGI("nativeInit: w=%d h=%d buf=%p size=%zu",
                 width, height, gOutBuffer, gOutBufferSize);
        } else {
            LOGI("nativeInit: GetDirectBufferAddress returned null");
        }
    } else {
        LOGI("nativeInit: outBuffer is null");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_rudviwer_MainActivity_1nativeProcessFrame(
        JNIEnv* env,
        jobject /*thiz*/,
        jbyteArray nv21Arr,
        jint width,
        jint height
) {
    std::lock_guard<std::mutex> lock(gMutex);

    if (nv21Arr == nullptr) return;

    jbyte* nv21 = env->GetByteArrayElements(nv21Arr, nullptr);
    if (!nv21) return;

    // Wrap NV21 data: height + height/2 rows, width columns, 8-bit single channel
    Mat yuv(height + height / 2, width, CV_8UC1, reinterpret_cast<unsigned char*>(nv21));
    Mat rgba;
    cvtColor(yuv, rgba, COLOR_YUV2RGBA_NV21);

    // Convert to gray and run Canny
    Mat gray;
    cvtColor(rgba, gray, COLOR_RGBA2GRAY);

    Mat edges;
    Canny(gray, edges, 50, 150);

    // Convert edges to RGBA for GL texture
    Mat edgesRGBA;
    cvtColor(edges, edgesRGBA, COLOR_GRAY2RGBA);

    size_t needed = static_cast<size_t>(width) * static_cast<size_t>(height) * 4;

    if (gOutBuffer && gOutBufferSize >= needed) {
        std::memcpy(gOutBuffer, edgesRGBA.data, needed);
    } else {
        LOGI("nativeProcessFrame: output buffer missing or too small");
    }

    env->ReleaseByteArrayElements(nv21Arr, nv21, JNI_ABORT);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_rudviwer_MainActivity_1nativeRelease(
        JNIEnv* /*env*/,
        jobject /*thiz*/
) {
    std::lock_guard<std::mutex> lock(gMutex);
    gOutBuffer = nullptr;
    gOutBufferSize = 0;
    gWidth = gHeight = 0;
    LOGI("nativeRelease");
}
