// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include "vendor/pif_config.hpp"
#include "defaults.hpp"
#include <array>
#include <functional>
#include <string>
#include <vector>

namespace unica_pif {
using Reader = std::function<std::string(const std::string &, const std::string &)>;
struct Profile {
    pif::Config config;
    bool spoofBuild = true;
    bool spoofProps = true;
    int initialSdk = 32;
    std::string error;
};
inline bool enabled(const std::string &s) { return s == "true" || s == "1"; }
inline Profile loadProfile(const Reader &read) {
    Profile p;
    p.spoofBuild = enabled(read("persist.sys.pif.spoof_build", "true"));
    p.spoofProps = enabled(read("persist.sys.pif.spoof_props", "true"));
    auto fingerprint = read("persist.sys.pif.fingerprint", kFingerprint);
    std::vector<std::string> parts;
    size_t start = 0;
    for (size_t i = 0; i <= fingerprint.size(); ++i) {
        if (i == fingerprint.size() || fingerprint[i] == '/' || fingerprint[i] == ':') {
            parts.emplace_back(fingerprint.substr(start, i - start)); start = i + 1;
        }
    }
    if (parts.size() != 8 || fingerprint.size() >= 92) {
        p.error = "Fingerprint must have eight nonempty components and fit native property storage";
        return p;
    }
    for (const auto &part : parts) if (part.empty()) {
        p.error = "Empty fingerprint component"; return p;
    }
    // Existing Settings stores literal null for unspecified identity fields.
    // In the native backend these mean automatic fingerprint-derived identity.
    auto identity = [&](const char *key, const std::string &fallback) {
        auto value = read(std::string("persist.sys.pif.") + key, "null");
        return value.empty() || value == "null" ? fallback : value;
    };
    auto &map = p.config.propMap;
    map["FINGERPRINT"] = fingerprint;
    static constexpr std::array<const char *, 8> fields = {
        "BRAND", "PRODUCT", "DEVICE", "RELEASE", "ID", "INCREMENTAL", "TYPE", "TAGS"};
    static constexpr std::array<const char *, 8> props = {
        "brand", "product", "device", "release", "id", "incremental", "type", "tags"};
    for (size_t i = 0; i < fields.size(); ++i) map[fields[i]] = identity(props[i], parts[i]);
    // Do not infer Google manufacturer/model for arbitrary imported devices.
    const bool bluejay = parts[0] == "google" && parts[2] == "bluejay";
    auto manufacturer = identity("manufacturer", bluejay ? "Google" : "");
    auto model = identity("model", bluejay ? "Pixel 6a" : "");
    if (!manufacturer.empty()) map["MANUFACTURER"] = manufacturer;
    if (!model.empty()) map["MODEL"] = model;
    auto patch = read("persist.sys.pif.security_patch", kSecurityPatch);
    if (!patch.empty() && patch != "null") {
        if (patch.size() != 10 || patch[4] != '-' || patch[7] != '-') {
            p.error = "Invalid security patch date"; return p;
        }
        for (size_t i = 0; i < patch.size(); ++i) {
            if (i != 4 && i != 7 && (patch[i] < '0' || patch[i] > '9')) {
                p.error = "Invalid security patch date"; return p;
            }
        }
        p.config.securityPatch = patch; map["SECURITY_PATCH"] = patch;
    }
    const auto api = read("persist.sys.pif.first_api_level", kFirstApi);
    if (!api.empty() && api != "null") {
        if (api.size() > 2 || api.find_first_not_of("0123456789") != std::string::npos) {
            p.error = "Invalid first API level"; return p;
        }
        p.initialSdk = std::stoi(api);
        if (p.initialSdk < 1) { p.error = "Invalid first API level"; return p; }
        p.config.deviceInitialSdkInt = std::to_string(p.initialSdk);
    } else p.initialSdk = 0;
    p.config.buildId = map["ID"];
    for (const auto &[key, value] : map) {
        if (value.size() >= 92) { p.error = "Identity field exceeds property storage"; return p; }
    }
    return p;
}
}
