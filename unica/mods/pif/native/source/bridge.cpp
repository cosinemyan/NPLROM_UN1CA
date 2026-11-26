// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
#include "profile.hpp"
#include "vendor/property_virtualizer.hpp"
#include <android/log.h>
#include <jni.h>
#include <sys/system_properties.h>
#include <exception>
#include <string>

namespace {
std::string readProp(const std::string &key, const std::string &fallback) {
    const prop_info *info = __system_property_find(key.c_str());
    if (!info) return fallback;
    // Persistent fingerprint input can use bionic's long read-only encoding.
    std::string result;
    __system_property_read_callback(info, [](void *cookie, const char *, const char *v, uint32_t) {
        *static_cast<std::string *>(cookie) = v;
    }, &result);
    return result;
}
bool setBuild(JNIEnv *env, const unica_pif::Profile &profile) {
    jclass build = env->FindClass("android/os/Build");
    jclass version = env->FindClass("android/os/Build$VERSION");
    if (!build || !version || env->ExceptionCheck()) {
        env->ExceptionClear(); return false;
    }
    for (const auto &[key, value] : profile.config.propMap) {
        jclass target = key == "RELEASE" || key == "INCREMENTAL" || key == "SECURITY_PATCH"
            ? version : build;
        jfieldID field = env->GetStaticFieldID(target, key.c_str(), "Ljava/lang/String;");
        if (!field || env->ExceptionCheck()) { env->ExceptionClear(); return false; }
        jstring text = env->NewStringUTF(value.c_str());
        if (!text) { env->ExceptionClear(); return false; }
        env->SetStaticObjectField(target, field, text);
        env->DeleteLocalRef(text);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    }
    if (profile.initialSdk > 0) {
        jfieldID field = env->GetStaticFieldID(version, "DEVICE_INITIAL_SDK_INT", "I");
        if (!field || env->ExceptionCheck()) { env->ExceptionClear(); return false; }
        env->SetStaticIntField(version, field, profile.initialSdk);
    }
    env->DeleteLocalRef(build); env->DeleteLocalRef(version);
    if (env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    return true;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_mesalabs_unica_PifProcessHooks_nativeInitialize(JNIEnv *env, jclass, jstring name) {
    try {
        if (!name || !unica_pif::enabled(readProp("persist.sys.unica.pif", "true"))) return false;
        const char *raw = env->GetStringUTFChars(name, nullptr);
        if (!raw) { env->ExceptionClear(); return false; }
        std::string process(raw); env->ReleaseStringUTFChars(name, raw);
        bool gms = process == "com.google.android.gms.unstable";
        bool vending = process == "com.android.vending" &&
            unica_pif::enabled(readProp("persist.sys.pif.vending_build", "false"));
        if (!gms && !vending) return false;
        auto profile = unica_pif::loadProfile(readProp);
        if (!profile.error.empty()) {
            __android_log_print(ANDROID_LOG_ERROR, "UnicaPIF", "%s", profile.error.c_str());
            return false;
        }
        if (gms && profile.spoofProps && !pif::property_virtualizer::install(profile.config)) {
            __android_log_print(ANDROID_LOG_ERROR, "UnicaPIF", "Native property virtualization failed");
            return false;
        }
        if (profile.spoofBuild && !setBuild(env, profile)) {
            __android_log_print(ANDROID_LOG_ERROR, "UnicaPIF", "Build update failed");
            return false;
        }
        __android_log_print(ANDROID_LOG_INFO, "UnicaPIF", "Initialized %s: Build=%d nativeProps=%d",
            process.c_str(), profile.spoofBuild, gms && profile.spoofProps);
        return true;
    } catch (const std::exception &e) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, "UnicaPIF", "Startup error: %s", e.what());
        return false;
    }
}
