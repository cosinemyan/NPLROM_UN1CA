// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
package org.matrix.teesim

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Settings-owned configuration. No files, key deletion, root-of-trust edits or RKP changes. */
object RomProfiles {
    const val SETTING = "unica_keybox_profiles"
    const val LIMIT = 256 * 1024
    private val idPattern = Regex("[A-Za-z0-9_-]{1,40}")
    private val pkgPattern = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    val identityFields = listOf("brand", "device", "product", "manufacturer", "model", "serial", "imei", "meid", "imei2")

    val defaultApps = listOf("io.github.vvb2060.keyattestation", "com.google.android.apps.walletnfcrel",
        "com.google.android.gms", "com.android.vending")
    private fun validKeyboxName(name: String) = name !in setOf("current", ".", "..") &&
        name.isNotBlank() && name.length <= 255 && name.none { it.code < 32 || it.code == 127 || it == '/' || it == '\\' }

    fun defaults(): JSONObject = JSONObject().put("version", 1)
        .put("keyboxes", JSONObject()).put("profiles", JSONArray().put(
            JSONObject().put("id", "unica").put("keybox", "current").put("scope", "selected")
                .put("apps", JSONArray(defaultApps)).put("mode", "patch")))

    /** Reject the entire edit on ambiguity. 'all' is a fallback for unclaimed owner-user UIDs. */
    fun resolve(raw: String?, current: String?, apps: Map<Int, List<String>>,
                h: Harvester.Record): JSONArray {
        require(raw == null || raw.toByteArray().size <= LIMIT)
        val root = if (raw.isNullOrBlank()) defaults() else JSONObject(raw)
        require(root.getInt("version") == 1)
        val boxes = root.getJSONObject("keyboxes")
        require(boxes.length() <= 8)
        for (name in boxes.keys()) {
            require(validKeyboxName(name))
            require(boxes.getString(name).toByteArray().size <= LIMIT)
        }
        val profiles = root.getJSONArray("profiles")
        require(profiles.length() in 1..8)
        val ids = HashSet<String>()
        val claimed = HashMap<Int, Int>()
        val named = HashSet<String>()
        var fallback = -1
        val selected = Array(profiles.length()) { mutableMapOf<Int, List<String>>() }
        for (i in 0 until profiles.length()) {
            val p = profiles.getJSONObject(i)
            require(idPattern.matches(p.getString("id")) && ids.add(p.getString("id")))
            require(p.optString("mode", "patch") in setOf("patch", "generation"))
            val keybox = p.getString("keybox")
            require(keybox == "current" || boxes.has(keybox))
            when (p.getString("scope")) {
                "all" -> { require(fallback == -1); fallback = i }
                "selected" -> Unit
                else -> error("scope")
            }
            val entries = p.getJSONArray("apps")
            require(entries.length() <= 4096)
            for (n in 0 until entries.length()) {
                val pkg = entries.getString(n)
                require(pkgPattern.matches(pkg))
                // Repeated packages inside a shared-UID row in one profile are legitimate.
                require(named.add(pkg) || (0 until n).any { entries.getString(it) == pkg })
                for ((uid, names) in apps) if (pkg in names) {
                    require(uid in 10000..19999)
                    val old = claimed.put(uid, i)
                    require(old == null || old == i)
                    selected[i][uid] = names
                }
            }
        }
        if (fallback >= 0) for ((uid, names) in apps) {
            require(uid in 10000..19999)
            if (uid !in claimed) selected[fallback][uid] = names
        }
        val result = JSONArray()
        for (i in 0 until profiles.length()) {
            val p = profiles.getJSONObject(i)
            val key = p.getString("keybox")
            val xml = if (key == "current") current else boxes.getString(key)
            if (xml.isNullOrBlank()) continue
            require(xml.toByteArray().size <= LIMIT)
            val idsObject = JSONObject()
            // ROM profiles use captured identity defaults; retired UI overrides are ignored.
            for (name in identityFields) {
                val value = if (name in listOf("brand", "device", "product", "manufacturer", "model")) h.effective(name) else ""
                require(value.length <= 128 && value.none { it.code < 32 })
                if (value.isNotBlank()) idsObject.put(name, value)
            }
            val packages = JSONArray(); val users = JSONArray(); val uids = JSONArray(); val uidPackages = JSONArray()
            for ((uid, names) in selected[i].toSortedMap()) {
                uids.put(uid); uidPackages.put(names.firstOrNull() ?: "")
                for (pkg in names.sorted()) { packages.put(pkg); users.put(0) }
            }
            val out = JSONObject().put("id", p.getString("id"))
                .put("keyboxB64", Base64.getEncoder().encodeToString(xml.toByteArray(Charsets.UTF_8)))
                .put("mode", p.optString("mode", "patch"))
                .put("securityLevel", h.effectiveInt("attestationSecurityLevel", Harvester.effectiveAttestation(h).first))
                .put("deviceIds", idsObject).put("packages", packages).put("packageUsers", users)
                .put("uids", uids).put("uidPackages", uidPackages)
            val os = p.optString("osVersion", "system_property")
            val resolvedOs = when (os) {
                "system_property" -> DeviceProps.propOsVersion()
                "", "harvested" -> h.osVersion
                else -> { require(Regex("[0-9]{1,6}(\\.[0-9]{1,2}){0,2}").matches(os)); DeviceProps.parseOsVersion(os) }
            }
            resolvedOs?.let { out.put("osVersion", it) }
            val patch = p.optJSONObject("patchLevel") ?: JSONObject()
            for ((field, kind, captured) in listOf(
                Triple("osPatchLevel", "system", h.osPatchLevel),
                Triple("vendorPatchLevel", "vendor", h.vendorPatchLevel),
                Triple("bootPatchLevel", "boot", h.bootPatchLevel))) {
                val value = patch.optString(kind, "system_property")
                require(value in setOf("system_property", "harvested", "", "no", "today", "YYYY-MM-05") ||
                    Regex("[0-9]{4}-[0-9]{2}(-[0-9]{2})?").matches(value))
                if (value.matches(Regex("[0-9].*"))) java.time.LocalDate.parse(if (value.length == 7) "$value-01" else value)
                DeviceProps.resolvePatch(if (value == "harvested") "" else value, kind, captured)?.let { out.put(field, it) }
            }
            result.put(out)
        }
        return result
    }
}
