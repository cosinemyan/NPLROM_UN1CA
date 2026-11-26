// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
import { call } from "./bridge/rom.js";
import { el } from "./ui/dom.js";
const card = el("aside", { class: "card", style: "margin:12px 16px", "aria-live": "polite" });
const main = document.querySelector("main") || document.body;
main.prepend(card);
let busy = false;
async function refresh() {
  if (busy || document.hidden) return;
  busy = true;
  try {
    const r = await call("patchStatus");
    card.replaceChildren(el("h2", { text: "TEE / PIF system patch" }));
    if (!r.ok) { card.append(el("p", { text: r.error })); return; }
    for (const target of document.querySelectorAll("[data-rom-patch-profile]")) {
      const p = r.profiles[target.dataset.romPatchProfile];
      target.textContent = p ? `Saved system patch: TEE ${p.effective || "not reported"} · PIF ${p.pif || "not configured"} · ${p.state === "match" ? "Match" : p.state === "mismatch" ? "Mismatch" : "Not verified"}` : "Save this profile to compare its patch with PIF";
    }
    for (const [name, p] of Object.entries(r.profiles)) {
      const word = p.state === "match" ? "Match" : p.state === "mismatch" ? "Mismatch" : "Not verified";
      card.append(el("p", { class: p.state === "mismatch" ? "banner" : "muted", text: `${name}: TEE ${p.effective || "not reported"} · PIF ${p.pif || "not configured"} · ${word}` }));
    }
    card.append(el("p", { class: "muted small", text: r.enabled === "true" ? "Resolved profile values; a matching month does not prove Integrity acceptance." : "ROM backend is off. Enable Spoof hardware attestation in ROM Settings. Saved profiles remain editable." }));
  } catch (_) { card.replaceChildren(el("p", { text: "Patch comparison unavailable" })); }
  finally { busy = false; }
}
refresh(); setInterval(refresh, 5000);
let lastEditor = null;
new MutationObserver(() => {
  const editor = document.querySelector('[data-rom-patch-profile]');
  if (editor && editor !== lastEditor) { lastEditor = editor; refresh(); }
  if (!editor) lastEditor = null;
}).observe(document.body, { childList: true, subtree: true });
