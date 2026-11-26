// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <string>
#include <unordered_map>
namespace pif {
struct Config {
    std::unordered_map<std::string, std::string> propMap;
    bool debug = false;
    std::string deviceInitialSdkInt;
    std::string securityPatch;
    std::string buildId;
};
}
