// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>
#include <string>
#include <vector>
#include <queue>
#include <unordered_map>
#include <mutex>
#include <atomic>
#include <map>

#include "AhoCorasick.h"
#include "io_github_muntashirakon_algo_AhoCorasick.h"

static std::mutex mutex;
static std::atomic<long long> lastId(0);
static std::map<long long, AhoCorasick *> instances;

extern "C"
JNIEXPORT jlong JNICALL
Java_io_github_muntashirakon_algo_AhoCorasick_createNative(JNIEnv *env, jobject,
                                                           jobjectArray patternArray) {
    jsize len = env->GetArrayLength(patternArray);
    std::vector<std::string> patterns(len);
    for (jsize i = 0; i < len; ++i) {
        auto jstr = (jstring) env->GetObjectArrayElement(patternArray, i);
        const char *chars = env->GetStringUTFChars(jstr, nullptr);
        patterns[i] = chars;
        env->ReleaseStringUTFChars(jstr, chars);
        env->DeleteLocalRef(jstr);
    }

    auto ac = new AhoCorasick();
    ac->buildTrie(patterns);
    ac->buildFailureLinks();

    long long id = ++lastId;
    {
        std::lock_guard<std::mutex> lock(mutex);
        instances[id] = ac;
    }
    return (jlong) id;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_io_github_muntashirakon_algo_AhoCorasick_searchNative(JNIEnv *env, jobject,
                                                           jlong instance_id, jstring text) {
    AhoCorasick *ac = nullptr;
    {
        std::lock_guard<std::mutex> lock(mutex);
        if (instances.count(instance_id) == 0) return nullptr;
        ac = instances[instance_id];
    }
    const char *ctext = env->GetStringUTFChars(text, nullptr);
    std::string input(ctext);
    env->ReleaseStringUTFChars(text, ctext);

    std::vector<int> matches = ac->search(input);
    jintArray result = env->NewIntArray(matches.size());
    if (!matches.empty()) {
        env->SetIntArrayRegion(result, 0, matches.size(), matches.data());
    }
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_muntashirakon_algo_AhoCorasick_destroyNative(JNIEnv *, jobject,
                                                            jlong instance_id) {
    std::lock_guard<std::mutex> lock(mutex);
    auto it = instances.find(instance_id);
    if (it != instances.end()) {
        delete it->second;
        instances.erase(it);
    }
}
