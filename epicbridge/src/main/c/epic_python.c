#define PY_SSIZE_T_CLEAN
#include <jni.h>
#include <Python.h>
#include <android/log.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "EpicBridgePython"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM *g_vm;
static jclass g_crypto_class;
static jmethodID g_decrypt_method;
static pthread_mutex_t g_init_mutex = PTHREAD_MUTEX_INITIALIZER;
static int g_initialized = 0;

static PyObject *py_decrypt_aes_gcm(PyObject *self, PyObject *args) {
    (void) self;
    const char *key, *nonce, *ciphertext, *tag;
    Py_ssize_t key_len, nonce_len, ciphertext_len, tag_len;
    if (!PyArg_ParseTuple(args, "y#y#y#y#",
                          &key, &key_len, &nonce, &nonce_len,
                          &ciphertext, &ciphertext_len, &tag, &tag_len)) {
        return NULL;
    }

    JNIEnv *env = NULL;
    int attached = 0;
    jint get_env = (*g_vm)->GetEnv(g_vm, (void **) &env, JNI_VERSION_1_6);
    if (get_env == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != JNI_OK) {
            PyErr_SetString(PyExc_RuntimeError, "Unable to attach AES-GCM worker to the JVM");
            return NULL;
        }
        attached = 1;
    } else if (get_env != JNI_OK) {
        PyErr_SetString(PyExc_RuntimeError, "Unable to access the Android JVM");
        return NULL;
    }

    jbyteArray jkey = (*env)->NewByteArray(env, (jsize) key_len);
    jbyteArray jnonce = (*env)->NewByteArray(env, (jsize) nonce_len);
    jbyteArray jciphertext = (*env)->NewByteArray(env, (jsize) ciphertext_len);
    jbyteArray jtag = (*env)->NewByteArray(env, (jsize) tag_len);
    if (!jkey || !jnonce || !jciphertext || !jtag) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        PyErr_NoMemory();
        goto cleanup;
    }

    (*env)->SetByteArrayRegion(env, jkey, 0, (jsize) key_len, (const jbyte *) key);
    (*env)->SetByteArrayRegion(env, jnonce, 0, (jsize) nonce_len, (const jbyte *) nonce);
    (*env)->SetByteArrayRegion(env, jciphertext, 0, (jsize) ciphertext_len, (const jbyte *) ciphertext);
    (*env)->SetByteArrayRegion(env, jtag, 0, (jsize) tag_len, (const jbyte *) tag);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        PyErr_SetString(PyExc_RuntimeError, "Unable to prepare AES-GCM input for Android JCA");
        goto cleanup;
    }

    jbyteArray output = (jbyteArray) (*env)->CallStaticObjectMethod(
            env, g_crypto_class, g_decrypt_method, jkey, jnonce, jciphertext, jtag);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        PyErr_SetString(PyExc_ValueError, "AES-GCM authentication failed or Android JCA rejected the input");
        goto cleanup;
    }
    if (!output) {
        PyErr_SetString(PyExc_RuntimeError, "Android JCA returned no AES-GCM plaintext");
        goto cleanup;
    }

    jsize output_len = (*env)->GetArrayLength(env, output);
    jbyte *output_bytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (!output_bytes) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, output);
        PyErr_NoMemory();
        goto cleanup;
    }
    PyObject *result = PyBytes_FromStringAndSize((const char *) output_bytes, output_len);
    (*env)->ReleaseByteArrayElements(env, output, output_bytes, JNI_ABORT);
    (*env)->DeleteLocalRef(env, output);
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
    (*env)->DeleteLocalRef(env, jkey);
    (*env)->DeleteLocalRef(env, jnonce);
    (*env)->DeleteLocalRef(env, jciphertext);
    (*env)->DeleteLocalRef(env, jtag);
    return result;

cleanup:
    if (jkey) (*env)->DeleteLocalRef(env, jkey);
    if (jnonce) (*env)->DeleteLocalRef(env, jnonce);
    if (jciphertext) (*env)->DeleteLocalRef(env, jciphertext);
    if (jtag) (*env)->DeleteLocalRef(env, jtag);
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
    return NULL;
}

static PyMethodDef crypto_methods[] = {
    {"decrypt_aes_gcm", py_decrypt_aes_gcm, METH_VARARGS,
     "Decrypt and authenticate AES-GCM data using Android JCA."},
    {NULL, NULL, 0, NULL}
};

static struct PyModuleDef crypto_module = {
    PyModuleDef_HEAD_INIT, "_epiccrypto", NULL, -1, crypto_methods,
    NULL, NULL, NULL, NULL
};

PyMODINIT_FUNC PyInit__epiccrypto(void) {
    return PyModule_Create(&crypto_module);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    g_vm = vm;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass local = (*env)->FindClass(env, "com/maliwinlator/epicbridge/EpicCryptoBridge");
    if (!local) return JNI_ERR;
    g_crypto_class = (jclass) (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    g_decrypt_method = (*env)->GetStaticMethodID(env, g_crypto_class, "decryptAesGcm",
                                                 "([B[B[B[B)[B");
    if (!g_decrypt_method) return JNI_ERR;
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_maliwinlator_epicbridge_EpicPythonRuntime_nativeInitialize(
        JNIEnv *env, jclass clazz, jstring home, jstring temp_dir) {
    (void) clazz;
    pthread_mutex_lock(&g_init_mutex);
    if (g_initialized) {
        pthread_mutex_unlock(&g_init_mutex);
        return JNI_TRUE;
    }

    const char *home_utf8 = (*env)->GetStringUTFChars(env, home, NULL);
    const char *temp_utf8 = (*env)->GetStringUTFChars(env, temp_dir, NULL);
    if (!home_utf8 || !temp_utf8) {
        if (home_utf8) (*env)->ReleaseStringUTFChars(env, home, home_utf8);
        if (temp_utf8) (*env)->ReleaseStringUTFChars(env, temp_dir, temp_utf8);
        pthread_mutex_unlock(&g_init_mutex);
        return JNI_FALSE;
    }

    setenv("TMPDIR", temp_utf8, 1);
    setenv("PYTHONHOME", home_utf8, 1);
    if (PyImport_AppendInittab("_epiccrypto", PyInit__epiccrypto) == -1) {
        LOGE("Failed to register Android AES-GCM module");
        goto init_failed;
    }

    PyConfig config;
    PyConfig_InitPythonConfig(&config);
    config.use_environment = 1;
    config.site_import = 1;
    char *argv[] = {(char *) "epicbridge", NULL};
    PyStatus status = PyConfig_SetBytesArgv(&config, 1, argv);
    if (!PyStatus_Exception(status)) {
        status = PyConfig_SetBytesString(&config, &config.home, home_utf8);
    }
    if (!PyStatus_Exception(status)) {
        status = Py_InitializeFromConfig(&config);
    }
    if (PyStatus_Exception(status)) {
        LOGE("CPython initialization failed: %s", status.err_msg ? status.err_msg : "unknown error");
        PyConfig_Clear(&config);
        goto init_failed;
    }
    PyConfig_Clear(&config);
    g_initialized = 1;
    PyEval_SaveThread();
    (*env)->ReleaseStringUTFChars(env, home, home_utf8);
    (*env)->ReleaseStringUTFChars(env, temp_dir, temp_utf8);
    pthread_mutex_unlock(&g_init_mutex);
    return JNI_TRUE;

init_failed:
    (*env)->ReleaseStringUTFChars(env, home, home_utf8);
    (*env)->ReleaseStringUTFChars(env, temp_dir, temp_utf8);
    pthread_mutex_unlock(&g_init_mutex);
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_maliwinlator_epicbridge_EpicPythonRuntime_nativeCall(
        JNIEnv *env, jclass clazz, jstring command, jstring payload) {
    (void) clazz;
    if (!g_initialized) return (*env)->NewStringUTF(env, "{\"ok\":false,\"error\":\"RUNTIME_NOT_INITIALIZED\"}");
    const char *command_utf8 = (*env)->GetStringUTFChars(env, command, NULL);
    const char *payload_utf8 = (*env)->GetStringUTFChars(env, payload, NULL);
    if (!command_utf8 || !payload_utf8) {
        if (command_utf8) (*env)->ReleaseStringUTFChars(env, command, command_utf8);
        if (payload_utf8) (*env)->ReleaseStringUTFChars(env, payload, payload_utf8);
        return (*env)->NewStringUTF(env, "{\"ok\":false,\"error\":\"JNI_ARGUMENT_ERROR\"}");
    }

    PyGILState_STATE gil = PyGILState_Ensure();
    PyObject *module = PyImport_ImportModule("epicbridge_runner");
    PyObject *callable = module ? PyObject_GetAttrString(module, "handle") : NULL;
    PyObject *py_command = PyUnicode_FromString(command_utf8);
    PyObject *py_payload = PyUnicode_FromString(payload_utf8);
    PyObject *result = (callable && py_command && py_payload)
            ? PyObject_CallFunctionObjArgs(callable, py_command, py_payload, NULL) : NULL;

    const char *result_utf8 = NULL;
    if (result && PyUnicode_Check(result)) {
        result_utf8 = PyUnicode_AsUTF8(result);
    }
    jstring output = result_utf8 ? (*env)->NewStringUTF(env, result_utf8) : NULL;
    if (!output) {
        PyErr_Print();
        output = (*env)->NewStringUTF(env, "{\"ok\":false,\"error\":\"PYTHON_RUNTIME_ERROR\"}");
    }

    Py_XDECREF(result);
    Py_XDECREF(py_payload);
    Py_XDECREF(py_command);
    Py_XDECREF(callable);
    Py_XDECREF(module);
    PyGILState_Release(gil);
    (*env)->ReleaseStringUTFChars(env, command, command_utf8);
    (*env)->ReleaseStringUTFChars(env, payload, payload_utf8);
    return output;
}
