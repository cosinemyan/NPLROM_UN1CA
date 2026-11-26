// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
package org.matrix.teesim

/** Version 1 describes the existing KMR derivation, not a new key format or migration. */
object StorageContract {
    fun validate(version: Int, contract: Int, derivation: String, key: ByteArray, hash: ByteArray) {
        require(version in 1..3) { "Unsupported frozen state version" }
        require(contract == 1) { "Unsupported storage contract" }
        require(derivation == "kmr-v1") { "Unsupported simulator key derivation" }
        require(key.size == 32 && hash.size == 32) { "Invalid frozen boot seed length" }
    }

    fun preserve(prior: ByteArray, next: ByteArray) {
        require(prior.contentEquals(next)) { "Refusing to replace the frozen simulator key seed" }
    }
}
