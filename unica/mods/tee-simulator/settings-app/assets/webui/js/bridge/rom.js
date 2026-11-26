// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
// Fixed operations only; asynchronous Binder/Settings work never blocks the UI thread.
let serial = 0;
const pending = new Map();
window.romReply = (id, raw) => {
  const p = pending.get(id); if (!p) return;
  pending.delete(id); clearTimeout(p.timer);
  try { p.resolve(JSON.parse(raw)); } catch (_) { p.reject(new Error("Invalid ROM response")); }
};
export function call(action, args = {}) {
  return new Promise((resolve, reject) => {
    const id = String(++serial);
    if (!window.RomBridge) { reject(new Error("Open this interface in TEE Simulator Settings")); return; }
    const timer = setTimeout(() => { pending.delete(id); reject(new Error("ROM operation timed out")); }, 30000);
    pending.set(id, { resolve, reject, timer });
    window.RomBridge.request(id, action, JSON.stringify(args));
  });
}
export async function must(action, args = {}) {
  const r = await call(action, args);
  if (!r.ok) throw new Error(r.error || "ROM operation failed");
  return r;
}
