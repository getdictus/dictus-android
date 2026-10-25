#include <jni.h>
#include <android/log.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <sys/sysinfo.h>
#include <string.h>
#include "whisper.h"
#include "ggml.h"

#define UNUSED(x) (void)(x)
#define TAG "JNI"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,     TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,     TAG, __VA_ARGS__)

static inline int min(int a, int b) {
    return (a < b) ? a : b;
}

static inline int max(int a, int b) {
    return (a > b) ? a : b;
}

// Polled by whisper_full after each encoder pass and each decoder step: true makes it
// stop and return non-zero. An encoder pass under way runs to its end.
static bool abort_requested(void *flag) {
    return atomic_load((atomic_bool *) flag);
}

JNIEXPORT jlong JNICALL
Java_dev_pivisolutions_dictus_whisper_WhisperLib_00024Companion_newAbortFlag(
        JNIEnv *env, jobject thiz) {
    UNUSED(env);
    UNUSED(thiz);
    atomic_bool *flag = malloc(sizeof(atomic_bool));
    if (flag != NULL) atomic_init(flag, false);
    return (jlong) flag;
}

JNIEXPORT void JNICALL
Java_dev_pivisolutions_dictus_whisper_WhisperLib_00024Companion_setAbortFlag(
        JNIEnv *env, jobject thiz, jlong flag_ptr, jboolean abort) {
    UNUSED(env);
    UNUSED(thiz);
    atomic_store((atomic_bool *) flag_ptr, abort == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_dev_pivisolutions_dictus_whisper_WhisperLib_00024Companion_freeAbortFlag(
        JNIEnv *env, jobject thiz, jlong flag_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    free((atomic_bool *) flag_ptr);
}

JNIEXPORT jlong JNICALL
Java_dev_pivisolutions_dictus_whisper_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    struct whisper_context *context = NULL;
    const char *model_path_chars = (*env)->GetStringUTFChars(env, model_path_str, NULL);
    LOGI("Loading model from '%s'", model_path_chars);
    context = whisper_init_from_file_with_params(model_path_chars, whisper_context_default_params());
    (*env)->ReleaseStringUTFChars(env, model_path_str, model_path_chars);
    if (context == NULL) {
        LOGW("Failed to initialize whisper context from '%s'", model_path_chars);
    }
    return (jlong) context;
}

JNIEXPORT void JNICALL
Java_dev_pivisolutions_dictus_whisper_WhisperLib_00024Companion_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    whisper_free(context);
}

JNIEXPORT jint JNICALL
Java_dev_pivisolutions_dictus_whisper_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jlong abort_flag_ptr, jint num_threads,
        jfloatArray audio_data, jstring language_str) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    jfloat *audio_data_arr = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize audio_data_length = (*env)->GetArrayLength(env, audio_data);
    const char *language = (*env)->GetStringUTFChars(env, language_str, NULL);

    // Adapted from the whisper.cpp reference Android example
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.translate        = false;
    params.language         = language;
    params.n_threads        = num_threads;
    params.offset_ms        = 0;
    params.no_context       = true;
    params.single_segment   = false;
    params.abort_callback   = abort_requested;
    params.abort_callback_user_data = (void *) abort_flag_ptr;

    whisper_reset_timings(context);

    LOGI("About to run whisper_full with language='%s', threads=%d, samples=%d",
         language, num_threads, audio_data_length);

    const int result = whisper_full(context, params, audio_data_arr, audio_data_length);
    if (result != 0) {
        LOGI("whisper_full returned %d (aborted=%d)", result, abort_requested((void *) abort_flag_ptr));
    } else {
        whisper_print_timings(context);
    }

    (*env)->ReleaseStringUTFChars(env, language_str, language);
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_data_arr, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL
Java_dev_pivisolutions_dictus_whisper_WhisperLib_00024Companion_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_n_segments(context);
}

JNIEXPORT jstring JNICALL
Java_dev_pivisolutions_dictus_whisper_WhisperLib_00024Companion_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    const char *text = whisper_full_get_segment_text(context, index);
    jstring string = (*env)->NewStringUTF(env, text);
    return string;
}

JNIEXPORT jstring JNICALL
Java_dev_pivisolutions_dictus_whisper_WhisperLib_00024Companion_getSystemInfo(
        JNIEnv *env, jobject thiz) {
    UNUSED(thiz);
    const char *sysinfo = whisper_print_system_info();
    jstring string = (*env)->NewStringUTF(env, sysinfo);
    return string;
}
