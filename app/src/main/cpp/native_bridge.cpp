#include <jni.h>

#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_robinying_paddlevision_NativeBridge_bridgeInfo(JNIEnv* env, jobject) {
    const std::string value = "Paddle Vision C++ support bridge";
    return env->NewStringUTF(value.c_str());
}
