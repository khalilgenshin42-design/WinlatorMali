#include <jni.h>

JNIEXPORT jboolean JNICALL
Java_com_maliwinlator_epicbridge_EpicPythonRuntime_nativeInitialize(
        JNIEnv *env, jclass clazz, jstring home, jstring temp_dir) {
    (void) env; (void) clazz; (void) home; (void) temp_dir;
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_maliwinlator_epicbridge_EpicPythonRuntime_nativeCall(
        JNIEnv *env, jclass clazz, jstring command, jstring payload) {
    (void) clazz; (void) command; (void) payload;
    return (*env)->NewStringUTF(env, "{\"ok\":false,\"error\":\"RUNTIME_NOT_PACKAGED\"}");
}
