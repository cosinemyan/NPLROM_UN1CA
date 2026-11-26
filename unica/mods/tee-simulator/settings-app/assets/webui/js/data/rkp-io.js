// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
// Preserve ROM RKP policy: these controls are unavailable in the ROM edition.
export const RKP_PROPS = [];
export async function readRkpProps() { return []; }
export async function setRkpProp() { return { ok: false, error: "Managed by the ROM" }; }
export const deleteRkpProp = setRkpProp;
export const restoreRkpProp = setRkpProp;
