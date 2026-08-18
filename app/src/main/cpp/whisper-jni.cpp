#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper/include/whisper.h"

#define TAG "WhisperJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,    TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,     TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jlong JNICALL
Java_com_rakshaksetu_app_pipeline_WhisperEngine_initContext(JNIEnv *env, jobject thiz,
                                                            jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing whisper context with model: %s", path);
    
    struct whisper_context * ctx = whisper_init_from_file_with_params(path, whisper_context_default_params());
    env->ReleaseStringUTFChars(model_path, path);
    
    if (ctx == nullptr) {
        LOGE("Failed to initialize whisper context");
        return 0L;
    }
    
    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_rakshaksetu_app_pipeline_WhisperEngine_transcribeNative(JNIEnv *env, jobject thiz,
                                                                 jlong context_ptr,
                                                                 jfloatArray samples,
                                                                 jstring language,
                                                                 jstring initial_prompt,
                                                                 jboolean translate,
                                                                 jboolean no_timestamps) {
    
    auto * ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx == nullptr) return env->NewStringUTF("");

    int n_samples = env->GetArrayLength(samples);
    jfloat * p_samples = env->GetFloatArrayElements(samples, nullptr);
    
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress   = false;
    params.print_special    = false;
    params.print_realtime   = false;
    params.print_timestamps = !no_timestamps;
    params.translate        = translate;
    
    const char *lang_str = env->GetStringUTFChars(language, nullptr);
    params.language = lang_str;
    
    const char *prompt_str = env->GetStringUTFChars(initial_prompt, nullptr);
    params.initial_prompt = prompt_str;
    
    if (whisper_full(ctx, params, p_samples, n_samples) != 0) {
        LOGE("Failed to process audio");
        env->ReleaseStringUTFChars(language, lang_str);
        env->ReleaseStringUTFChars(initial_prompt, prompt_str);
        env->ReleaseFloatArrayElements(samples, p_samples, JNI_ABORT);
        return env->NewStringUTF("");
    }
    
    std::string result = "";
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char * text = whisper_full_get_segment_text(ctx, i);
        result += text;
    }
    
    env->ReleaseStringUTFChars(language, lang_str);
    env->ReleaseStringUTFChars(initial_prompt, prompt_str);
    env->ReleaseFloatArrayElements(samples, p_samples, JNI_ABORT);
    
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_rakshaksetu_app_pipeline_WhisperEngine_freeContext(JNIEnv *env, jobject thiz,
                                                            jlong context_ptr) {
    auto * ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("Whisper context freed.");
    }
}
