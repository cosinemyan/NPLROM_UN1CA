// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
import { must } from "../bridge/rom.js";
export async function iconDataUrl() { return null; }
export async function keyAdmin(action, args = {}) {
  if (action === "keysDb") {
    const first = await must(action, { offset: 0 });
    // Existing ROM API pages keys. Keep pagination bounded, report truncation visibly.
    for (let offset = 100; offset < Math.min(first.total || 0, 1000); offset += 100) {
      const page = await must(action, { offset }); first.keys.push(...page.keys);
    }
    first.truncated = first.total > first.keys.length;
    return first;
  }
  if (action === "keysDbDelete") {
    if (!Array.isArray(args.ids) || args.ids.length > 100) throw new Error("Select at most 100 stored keys per deletion");
    // The upstream controller already presents a confirmation dialog.
    return must(action, { ids: args.ids, confirmed: true });
  }
  return must(action, args);
}
