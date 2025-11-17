#include <string>
#include <jni.h>

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <android/log.h> 
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#define LOG_TAG "Native-Lib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

cv::Mat processedMat;

extern "C" JNIEXPORT jstring JNICALL
Java_com_sharan_rnd_1opencv_1viewer_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {

    cv::Mat testMat;
    LOGD("OpenCV testMat created successfully.");

    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

// This is the C++ side of our 'processFrame' native method

extern "C" JNIEXPORT void JNICALL
Java_com_sharan_rnd_1opencv_1viewer_MainActivity_processFrame(
        JNIEnv *env,
        jobject /* this */,
        jint width,
        jint height,
        jbyteArray frameData) {

    // 1. Get the raw byte data from Java
    jbyte *data = env->GetByteArrayElements(frameData, 0);

    // 2. Create a Mat from the raw camera data (NV21 format)
    // The camera data is (height + height/2) x width,
    // as it contains the Y plane (grayscale) followed by the UV plane (color)
    cv::Mat matYUV(height + height / 2, width, CV_8UC1, (void*)data);

    // 3. Extract just the grayscale (Y) plane
    // This is a "zero-copy" operation; it just points to the first part of matYUV
    cv::Mat matGray = matYUV(cv::Rect(0, 0, width, height));

    // 4. Apply Canny Edge Detection
    // We'll re-use our global 'processedMat' to store the result
    // We can also use matGray as the output to save memory, then copy
    cv::Canny(matGray, processedMat, 80, 100);

    // 5. Release the Java byte array
    env->ReleaseByteArrayElements(frameData, data, 0);
}

// --- OPENGL RENDERER CODE ---

// Shader code (minimal)
const char* VERTEX_SHADER = R"(
    attribute vec4 vPosition;
    attribute vec2 vTexCoord;
    varying vec2 texCoord;
    void main() {
        gl_Position = vPosition;
        texCoord = vTexCoord;
    }
)";

const char* FRAGMENT_SHADER = R"(
    precision mediump float;
    varying vec2 texCoord;
    uniform sampler2D texSampler;
    void main() {
        // Canny gives a 1-channel (grayscale) image.
        // We'll show it in the Red channel, but you could also use gl_Luminance.
        gl_FragColor = vec4(texture2D(texSampler, texCoord).r, 
                            texture2D(texSampler, texCoord).r, 
                            texture2D(texSampler, texCoord).r, 
                            1.0);
    }
)";

GLuint gProgram;
GLuint gTexId;
GLuint gPositionHandle;
GLuint gTexCoordHandle;

GLuint loadShader(GLenum shaderType, const char* pSource) {
    GLuint shader = glCreateShader(shaderType);
    if (shader) {
        glShaderSource(shader, 1, &pSource, NULL);
        glCompileShader(shader);
        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (!compiled) {
            GLint infoLen = 0;
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
            if (infoLen) {
                char* buf = (char*) malloc(infoLen);
                if (buf) {
                    glGetShaderInfoLog(shader, infoLen, NULL, buf);
                    LOGD("Could not compile shader %d:\n%s", shaderType, buf);
                    free(buf);
                }
            }
            glDeleteShader(shader);
            shader = 0;
        }
    }
    return shader;
}

GLuint createProgram(const char* pVertexSource, const char* pFragmentSource) {
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, pVertexSource);
    if (!vertexShader) return 0;

    GLuint pixelShader = loadShader(GL_FRAGMENT_SHADER, pFragmentSource);
    if (!pixelShader) return 0;

    GLuint program = glCreateProgram();
    if (program) {
        glAttachShader(program, vertexShader);
        glAttachShader(program, pixelShader);
        glLinkProgram(program);
        GLint linkStatus = GL_FALSE;
        glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
        if (linkStatus != GL_TRUE) {
            GLint bufLength = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &bufLength);
            if (bufLength) {
                char* buf = (char*) malloc(bufLength);
                if (buf) {
                    glGetProgramInfoLog(program, bufLength, NULL, buf);
                    LOGD("Could not link program:\n%s", buf);
                    free(buf);
                }
            }
            glDeleteProgram(program);
            program = 0;
        }
    }
    return program;
}

// JNI Functions for the OpenGL Renderer
extern "C" JNIEXPORT void JNICALL
Java_com_sharan_rnd_1opencv_1viewer_MainActivity_nativeOnSurfaceCreated(JNIEnv *env, jobject thiz) {
    gProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    if (!gProgram) {
        LOGD("Could not create program.");
        return;
    }

    gPositionHandle = glGetAttribLocation(gProgram, "vPosition");
    gTexCoordHandle = glGetAttribLocation(gProgram, "vTexCoord");

    // Create a texture to hold our processed frame
    glGenTextures(1, &gTexId);
    glBindTexture(GL_TEXTURE_2D, gTexId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sharan_rnd_1opencv_1viewer_MainActivity_nativeOnSurfaceChanged(JNIEnv *env, jobject thiz, jint width, jint height) {
    glViewport(0, 0, width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sharan_rnd_1opencv_1viewer_MainActivity_nativeOnDrawFrame(JNIEnv *env, jobject thiz) {
    glClear(GL_COLOR_BUFFER_BIT);

    // Check if our processedMat is ready
    if (processedMat.empty()) {
        return; // Not yet processed
    }

    // Vertices for a full-screen quad
    GLfloat vertices[] = { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f };
    // Texture coordinates (flipped vertically)
    GLfloat texCoords[] = { 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f };

    glUseProgram(gProgram);

    // Upload the new frame data from processedMat to the texture
    glBindTexture(GL_TEXTURE_2D, gTexId);
    // Canny output is 1-channel, so we use GL_LUMINANCE
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, processedMat.cols, processedMat.rows, 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, processedMat.data);

    glVertexAttribPointer(gPositionHandle, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glEnableVertexAttribArray(gPositionHandle);

    glVertexAttribPointer(gTexCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, texCoords);
    glEnableVertexAttribArray(gTexCoordHandle);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(gPositionHandle);
    glDisableVertexAttribArray(gTexCoordHandle);
}