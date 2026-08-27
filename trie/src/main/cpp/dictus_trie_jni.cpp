#include <jni.h>

#include <memory>
#include <string>
#include <vector>

#include "dictus_proximity.h"
#include "dictus_scorer.h"
#include "dictus_trie.h"

namespace {

struct Engine {
    dictus::Trie trie;
    dictus::Scorer scorer;
    dictus::ProximityMap proximity;
};

Engine* fromHandle(jlong handle) {
    return reinterpret_cast<Engine*>(handle);
}

std::u16string toUtf16(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    std::u16string result(reinterpret_cast<const char16_t*>(chars), length);
    env->ReleaseStringChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new Engine());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete fromHandle(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeLoad(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring path
) {
    Engine* engine = fromHandle(handle);
    if (engine == nullptr || path == nullptr) return JNI_FALSE;
    const char* utf8 = env->GetStringUTFChars(path, nullptr);
    if (utf8 == nullptr) return JNI_FALSE;
    const bool loaded = engine->trie.loadMmap(utf8);
    env->ReleaseStringUTFChars(path, utf8);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeSetLayout(
    JNIEnv*,
    jobject,
    jlong handle,
    jint layout
) {
    Engine* engine = fromHandle(handle);
    if (engine == nullptr) return;
    if (layout == 0) {
        engine->proximity.buildAZERTY();
    } else {
        engine->proximity.buildQWERTY();
    }
    engine->scorer.setProximityMap(&engine->proximity);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeWordExists(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring word
) {
    Engine* engine = fromHandle(handle);
    const std::u16string input = toUtf16(env, word);
    if (engine == nullptr || input.empty()) return JNI_FALSE;
    return engine->trie.wordExists(
        reinterpret_cast<const uint16_t*>(input.data()),
        static_cast<int>(input.size())
    ) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeCorrect(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring word,
    jfloat maxEditDistance,
    jint maxResults
) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;
    Engine* engine = fromHandle(handle);
    const std::u16string input = toUtf16(env, word);
    if (engine == nullptr || input.empty() || input.size() > 32 ||
        maxEditDistance <= 0.0f || maxEditDistance > 2.0f || maxResults <= 0 || maxResults > 20) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }
    const std::vector<dictus::Candidate> candidates = engine->scorer.correct(
        engine->trie,
        reinterpret_cast<const uint16_t*>(input.data()),
        static_cast<int>(input.size()),
        maxEditDistance,
        maxResults
    );
    jobjectArray result = env->NewObjectArray(
        static_cast<jsize>(candidates.size()),
        stringClass,
        nullptr
    );
    if (result == nullptr) return nullptr;
    for (size_t index = 0; index < candidates.size(); ++index) {
        jstring candidate = env->NewStringUTF(candidates[index].word);
        if (candidate == nullptr) return result;
        env->SetObjectArrayElement(result, static_cast<jsize>(index), candidate);
        env->DeleteLocalRef(candidate);
    }
    return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeComplete(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring prefix,
    jint maxResults
) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;
    Engine* engine = fromHandle(handle);
    const std::u16string input = toUtf16(env, prefix);
    if (engine == nullptr || input.empty() || input.size() > 32 ||
        maxResults <= 0 || maxResults > 20) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }
    const std::vector<std::u16string> completions = engine->trie.complete(
        reinterpret_cast<const uint16_t*>(input.data()),
        static_cast<int>(input.size()),
        maxResults
    );
    jobjectArray result = env->NewObjectArray(
        static_cast<jsize>(completions.size()), stringClass, nullptr);
    if (result == nullptr) return nullptr;
    for (size_t index = 0; index < completions.size(); ++index) {
        const std::u16string& completion = completions[index];
        jstring value = env->NewString(
            reinterpret_cast<const jchar*>(completion.data()),
            static_cast<jsize>(completion.size()));
        if (value == nullptr) return result;
        env->SetObjectArrayElement(result, static_cast<jsize>(index), value);
        env->DeleteLocalRef(value);
    }
    return result;
}