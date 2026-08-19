#include <jni.h>

#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_robinying_paddlevision_NativeBridge_runtimeInfo(JNIEnv* env, jobject) {
    const std::string value = "Paddle Lite v2.10-rc Java runtime with C++ support bridge";
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_robinying_paddlevision_NativeBridge_isRuntimeAvailable(JNIEnv*, jobject) {
    return JNI_TRUE;
}
