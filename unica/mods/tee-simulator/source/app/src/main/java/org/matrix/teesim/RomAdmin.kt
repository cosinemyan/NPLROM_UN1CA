// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
package org.matrix.teesim

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.os.SystemProperties
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Native Settings bridge. No HTTP, root-exec bridge, arbitrary commands or exported key XML. */
object RomAdmin : Binder() {
    const val SERVICE = "unica.teesim"
    const val DESCRIPTOR = "io.mesalabs.unica.teesim.Admin"
    @Volatile private var harvest: Harvester.Record? = null
    fun updateHarvest(record: Harvester.Record) { harvest = record }
    fun start(record: Harvester.Record) {
        harvest = record
        ServiceManager.addService(SERVICE, this)
    }
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == IBinder.INTERFACE_TRANSACTION) { reply?.writeString(DESCRIPTOR); return true }
        if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
        require(Binder.getCallingUid() in setOf(0, 1000) || Packages.isRomSettings(Binder.getCallingUid())) { "Settings caller required" }
        data.enforceInterface(DESCRIPTOR)
        val raw = data.readString() ?: "{}"
        require(raw.toByteArray().size <= 32768) { "Request too large" }
        val identity = Binder.clearCallingIdentity()
        try {
            val request = JSONObject(raw)
            val result = try { dispatch(request) } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", when (e) {
                    is IllegalArgumentException -> e.message ?: "Invalid request"
                    else -> "Backend operation unavailable; check diagnostics"
                })
            }
            val encoded = result.toString()
            val body = if (encoded.toByteArray().size <= 600000) encoded else
                JSONObject().put("ok", false).put("error", "Response too large; narrow the filter").toString()
            reply?.writeNoException()
            reply?.writeString(body)
            return true
        } finally { Binder.restoreCallingIdentity(identity) }
    }
    @Synchronized private fun dispatch(r: JSONObject): JSONObject {
        val out = JSONObject().put("ok", true)
        when (r.getString("action")) {
            "system" -> {
                out.put("harvest", Harvester.toJson(checkNotNull(harvest)))
                    .put("hook", Control.libHook ?: "unknown").put("api", Control.libApi)
                    .put("scope", RomApp.adminScope()).put("rkp", JSONObject().apply {
                        for (name in RkpStore.KNOWN) put(name, DeviceProps.prop(name, "unset"))
                        put("persist.device_config.remote_key_provisioning_native.enable_rkpd",
                            DeviceProps.prop("persist.device_config.remote_key_provisioning_native.enable_rkpd", "unset"))
                    })
            }
            "keys" -> {
                require(File("/data/misc/keystore/persistent.sqlite").canRead()) { "Keystore database is unavailable" }
                val all = KeystoreDb.listKeys(RomApp.adminTargets(), strict = true)
                val query = r.optString("query", "").lowercase()
                val only = r.optBoolean("spoofedOnly", true)
                val filtered = all.filter { (!only || it.optString("class") != "untouched") &&
                    listOf("alias", "package", "keybox", "class").any { field -> it.optString(field).lowercase().contains(query) } }
                val offset = r.optInt("offset", 0).coerceAtLeast(0)
                out.put("keys", JSONArray(filtered.drop(offset).take(100)))
                    .put("total", filtered.size).put("offset", offset)
            }
            "deleteKeys" -> {
                require(r.optBoolean("confirmed")) { "Explicit deletion confirmation required" }
                val ids = r.getJSONArray("ids")
                require(ids.length() in 1..100)
                // Owner-scoped binder deletion only. Never edit the live SQLite database.
                val wanted = (0 until ids.length()).map { ids.getString(it).toLong() }.toSet()
                val targets = RomApp.adminTargets()
                val eligible = KeystoreDb.listKeys(targets, strict = true).filter {
                    it.getString("id").toLong() in wanted && it.getInt("uid") in targets
                }
                var deleted = 0
                for (key in eligible) if (Keystore2Service.deleteKeyByIdAsUid(
                        key.getString("id").toLong(), key.getInt("uid")) == 0) deleted++
                out.put("deleted", deleted).put("requested", wanted.size)
            }
            "rescan" -> { RomApp.requestRefresh(); out.put("queued", true) }
            "usage" -> out.put("apps", Control.fetchUsage() ?: JSONArray())
            else -> throw IllegalArgumentException("Unsupported operation")
        }
        return out
    }
}
