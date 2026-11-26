// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
import { call, must } from "../bridge/rom.js";
export async function load() {
  try { return await call("configLoad"); }
  catch (e) { return { ok: false, error: e.message, raw: "ROM configuration unavailable" }; }
}
export async function save(config) {
  const r = await call("configSave", { config });
  if (r.ok) config._romRevision = r.revision;
  return r;
}
export async function listKeyboxes() { return (await must("keyboxList")).names; }
