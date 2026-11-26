// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
import { call, must } from "../bridge/rom.js";
export function safeName(raw) {
  const name = String(raw || "").trim();
  if (!name || name.includes("..") || /[\x00-\x1f\x7f/\\]/.test(name) || name === "ROM-current.xml") return null;
  return name.endsWith(".xml") ? name : name + ".xml";
}
export async function listKeyboxes() { return (await must("keyboxList")).names; }
export async function importKeybox(raw, text) {
  const name = safeName(raw);
  if (!name) return { ok: false, error: "Choose a safe .xml filename other than ROM-current.xml" };
  return call("keyboxImport", { name, text });
}
export function renameKeybox(from, to) { return call("keyboxRename", { from, to }); }
export function deleteKeybox(name) { return call("keyboxDelete", { name }); }
