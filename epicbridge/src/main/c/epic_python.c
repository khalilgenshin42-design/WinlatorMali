#define PY_SSIZE_T_CLEAN
#include <jni.h>
#include <limits.h>
#include <Python.h>
#include <android/log.h>
#include <pthread.h>
#include <stdlib.h>
#include <stdio.h>
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

    jbyteArray output;
    // JCA performs the CPU-heavy chunk authentication/decryption. Release the
    // CPython GIL so other Legendary download threads can continue concurrently.
    Py_BEGIN_ALLOW_THREADS
    output = (jbyteArray) (*env)->CallStaticObjectMethod(
            env, g_crypto_class, g_decrypt_method, jkey, jnonce, jciphertext, jtag);
    Py_END_ALLOW_THREADS
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
    (*env)->DeleteLocalRef(env, jkey);
    (*env)->DeleteLocalRef(env, jnonce);
    (*env)->DeleteLocalRef(env, jciphertext);
    (*env)->DeleteLocalRef(env, jtag);
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
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
    config.install_signal_handlers = 0;  // JNI may initialize Python on a Binder/worker thread.
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

    // Android embeds CPython below an app-private PYTHONHOME. Explicitly
    // prioritize the extracted stdlib and site-packages paths before the
    // first bridge import; this also avoids stale/partial path configuration
    // inherited from the Android process environment.
    PyObject *python_path = PySys_GetObject("path");  // borrowed reference
    if (!python_path || !PyList_Check(python_path)) {
        LOGE("CPython sys.path is unavailable after initialization");
        Py_FinalizeEx();
        goto init_failed;
    }
    char stdlib_path[PATH_MAX];
    char site_packages_path[PATH_MAX];
    int stdlib_len = snprintf(stdlib_path, sizeof(stdlib_path), "%s/lib/python3.15", home_utf8);
    int site_len = snprintf(site_packages_path, sizeof(site_packages_path), "%s/site-packages", stdlib_path);
    if (stdlib_len <= 0 || (size_t)stdlib_len >= sizeof(stdlib_path)
            || site_len <= 0 || (size_t)site_len >= sizeof(site_packages_path)) {
        LOGE("CPython runtime search path exceeds the supported path length");
        Py_FinalizeEx();
        goto init_failed;
    }
    PyObject *stdlib_entry = PyUnicode_DecodeFSDefault(stdlib_path);
    PyObject *site_entry = PyUnicode_DecodeFSDefault(site_packages_path);
    int path_ok = stdlib_entry && site_entry
            && PyList_Insert(python_path, 0, stdlib_entry) == 0
            && PyList_Insert(python_path, 1, site_entry) == 0;
    Py_XDECREF(stdlib_entry);
    Py_XDECREF(site_entry);
    if (!path_ok) {
        LOGE("Unable to configure CPython stdlib/site-packages search paths");
        PyErr_Clear();
        Py_FinalizeEx();
        goto init_failed;
    }

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
        if (PyErr_Occurred()) {
            PyObject *error_type = NULL;
            PyObject *error_value = NULL;
            PyObject *error_traceback = NULL;
            PyErr_Fetch(&error_type, &error_value, &error_traceback);
            PyErr_NormalizeException(&error_type, &error_value, &error_traceback);
            PyObject *type_name = error_type
                    ? PyObject_GetAttrString(error_type, "__name__") : NULL;
            PyObject *message = error_value ? PyObject_Str(error_value) : NULL;
            const char *type_utf8 = type_name ? PyUnicode_AsUTF8(type_name) : NULL;
            const char *message_utf8 = message ? PyUnicode_AsUTF8(message) : NULL;
            LOGE("CPython bridge call failed (%s): %s",
                 type_utf8 ? type_utf8 : "unknown exception",
                 message_utf8 ? message_utf8 : "no exception message");
            Py_XDECREF(message);
            Py_XDECREF(type_name);
            Py_XDECREF(error_traceback);
            Py_XDECREF(error_value);
            Py_XDECREF(error_type);
            PyErr_Clear();
        } else {
            LOGE("CPython bridge call produced no Unicode result and no Python exception");
        }
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
