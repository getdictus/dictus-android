#include <jni.h>

#include <memory>
#include <string>
#include <vector>

#include "dictus_ngram.h"
#include "dictus_proximity.h"
#include "dictus_scorer.h"
#include "dictus_trie.h"

namespace {

struct Engine {
    dictus::Trie trie;
    dictus::Scorer scorer;
    dictus::ProximityMap proximity;
    dictus::NgramEngine ngram;
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

std::string toBytes(JNIEnv* env, jbyteArray value) {
    if (value == nullptr) return {};
    const jsize length = env->GetArrayLength(value);
    if (length <= 0) return {};
    std::string result(static_cast<size_t>(length), '\0');
    env->GetByteArrayRegion(value, 0, length, reinterpret_cast<jbyte*>(result.data()));
    return env->ExceptionCheck() ? std::string{} : result;
}

jstring newUtf8String(JNIEnv* env, const std::string& value) {
    std::u16string utf16;
    utf16.reserve(value.size());
    size_t index = 0;
    while (index < value.size()) {
        const uint8_t first = static_cast<uint8_t>(value[index]);
        uint32_t codePoint = 0;
        size_t count = 0;
        if (first <= 0x7fU) {
            codePoint = first;
            count = 1;
        } else if (first >= 0xc2U && first <= 0xdfU) {
            codePoint = first & 0x1fU;
            count = 2;
        } else if (first >= 0xe0U && first <= 0xefU) {
            codePoint = first & 0x0fU;
            count = 3;
        } else if (first >= 0xf0U && first <= 0xf4U) {
            codePoint = first & 0x07U;
            count = 4;
        } else {
            return nullptr;
        }
        if (count > value.size() - index) return nullptr;
        for (size_t continuation = 1; continuation < count; ++continuation) {
            const uint8_t byte = static_cast<uint8_t>(value[index + continuation]);
            if ((byte & 0xc0U) != 0x80U) return nullptr;
            codePoint = (codePoint << 6U) | (byte & 0x3fU);
        }
        if ((count == 2 && codePoint < 0x80U) ||
            (count == 3 && codePoint < 0x800U) ||
            (count == 4 && codePoint < 0x10000U) ||
            codePoint > 0x10ffffU || (codePoint >= 0xd800U && codePoint <= 0xdfffU)) {
            return nullptr;
        }
        if (codePoint <= 0xffffU) {
            utf16.push_back(static_cast<char16_t>(codePoint));
        } else {
            codePoint -= 0x10000U;
            utf16.push_back(static_cast<char16_t>(0xd800U + (codePoint >> 10U)));
            utf16.push_back(static_cast<char16_t>(0xdc00U + (codePoint & 0x3ffU)));
        }
        index += count;
    }
    return env->NewString(
        reinterpret_cast<const jchar*>(utf16.data()),
        static_cast<jsize>(utf16.size()));
}

jobjectArray toPredictionArray(
    JNIEnv* env,
    const std::vector<dictus::NgramResult>& predictions
) {
    jclass predictionClass = env->FindClass("dev/pivisolutions/dictus/trie/NgramPrediction");
    if (predictionClass == nullptr) return nullptr;
    jmethodID constructor = env->GetMethodID(predictionClass, "<init>", "(Ljava/lang/String;I)V");
    if (constructor == nullptr) return nullptr;
    jobjectArray result = env->NewObjectArray(
        static_cast<jsize>(predictions.size()), predictionClass, nullptr);
    if (result == nullptr) return nullptr;
    for (size_t index = 0; index < predictions.size(); ++index) {
        jstring word = newUtf8String(env, predictions[index].word);
        if (word == nullptr) return result;
        jobject prediction = env->NewObject(
            predictionClass,
            constructor,
            word,
            static_cast<jint>(predictions[index].score));
        env->DeleteLocalRef(word);
        if (prediction == nullptr) return result;
        env->SetObjectArrayElement(result, static_cast<jsize>(index), prediction);
        env->DeleteLocalRef(prediction);
    }
    return result;
}

void throwOutOfMemory(JNIEnv* env) {
    jclass errorClass = env->FindClass("java/lang/OutOfMemoryError");
    if (errorClass != nullptr) env->ThrowNew(errorClass, "Native n-gram allocation failed");
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

extern "C" JNIEXPORT jint JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeFrequency(
    JNIEnv* env, jobject, jlong handle, jstring word
) {
    Engine* engine = fromHandle(handle);
    const std::u16string input = toUtf16(env, word);
    if (engine == nullptr || input.empty() || input.size() > 32) return 0;
    return static_cast<jint>(engine->trie.getFrequency(
        reinterpret_cast<const uint16_t*>(input.data()), static_cast<int>(input.size())));
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeMaxFrequency(
    JNIEnv*, jobject, jlong handle
) {
    Engine* engine = fromHandle(handle);
    return engine == nullptr ? 0 : static_cast<jlong>(engine->trie.maxFreq());
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

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeLoadNgram(
    JNIEnv* env, jobject, jlong handle, jbyteArray path
) {
    try {
        Engine* engine = fromHandle(handle);
        const std::string nativePath = toBytes(env, path);
        return engine != nullptr && !nativePath.empty() && engine->ngram.load(nativePath.c_str())
            ? JNI_TRUE : JNI_FALSE;
    } catch (const std::bad_alloc&) {
        throwOutOfMemory(env);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeUnloadNgram(
    JNIEnv*, jobject, jlong handle
) {
    Engine* engine = fromHandle(handle);
    if (engine != nullptr) engine->ngram.unload();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeIsNgramLoaded(
    JNIEnv*, jobject, jlong handle
) {
    Engine* engine = fromHandle(handle);
    return engine != nullptr && engine->ngram.isLoaded() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativePredictAfterWord(
    JNIEnv* env, jobject, jlong handle, jbyteArray word, jint maxResults
) {
    try {
        Engine* engine = fromHandle(handle);
        const std::string input = toBytes(env, word);
        return toPredictionArray(
            env,
            engine == nullptr ? std::vector<dictus::NgramResult>{} :
                engine->ngram.predictAfterWord(input.c_str(), static_cast<size_t>(maxResults))
        );
    } catch (const std::bad_alloc&) {
        throwOutOfMemory(env);
        return nullptr;
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativePredictAfterWords(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray firstWord,
    jbyteArray secondWord,
    jint maxResults
) {
    try {
        Engine* engine = fromHandle(handle);
        const std::string first = toBytes(env, firstWord);
        const std::string second = toBytes(env, secondWord);
        return toPredictionArray(
            env,
            engine == nullptr ? std::vector<dictus::NgramResult>{} :
                engine->ngram.predictAfterWords(
                    first.c_str(), second.c_str(), static_cast<size_t>(maxResults))
        );
    } catch (const std::bad_alloc&) {
        throwOutOfMemory(env);
        return nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_pivisolutions_dictus_trie_NativeTrie_nativeBigramScore(
    JNIEnv* env, jobject, jlong handle, jbyteArray previousWord, jbyteArray word
) {
    try {
        Engine* engine = fromHandle(handle);
        if (engine == nullptr) return 0;
        const std::string previous = toBytes(env, previousWord);
        const std::string target = toBytes(env, word);
        return static_cast<jint>(engine->ngram.bigramScore(previous.c_str(), target.c_str()));
    } catch (const std::bad_alloc&) {
        throwOutOfMemory(env);
        return 0;
    }
}