// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <cstdint>

namespace unica {
constexpr bool IsOwnerApp(int64_t uid) { return uid >= 10000 && uid <= 19999; }
constexpr bool MayAttest(int64_t uid, bool asymmetric, bool attestation, bool protected_key) {
  return IsOwnerApp(uid) && asymmetric && attestation && !protected_key;
}
} // namespace unica
