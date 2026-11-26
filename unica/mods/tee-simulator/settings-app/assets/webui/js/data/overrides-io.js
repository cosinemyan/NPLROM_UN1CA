// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
export async function load() { return { ok: true, overrides: {} }; }
export async function save() { return { ok: false, error: "Frozen ROM identity is read-only" }; }
