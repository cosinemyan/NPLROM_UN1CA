// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
package org.matrix.teesim

import android.app.ActivityThread
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.ServiceManager
import android.os.SystemClock
import android.os.SystemProperties
import java.io.File
import java.security.Security
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONArray
import org.json.JSONObject

/** ROM entry point. Settings retains XML import, validation, clear, and the original switch. */
object RomApp {
    private const val PROPERTY = "persist.sys.unica.keybox"
    private const val SETTING = "unica_keybox_data"
    private const val MAX_XML = 256 * 1024
    private val dirty = AtomicBoolean(true)
    private var observer: FileObserver? = null
    private var lastWire: String? = null
    private var profileConfig: String? = null
    private var cachedProfiles = JSONArray()
    @Volatile private var expectedProfiles = 0
    @Volatile private var configError = false
    @Volatile private var routingActive = false
    private var nextStatus = 0L
    private var lastEnabled = false
    private var nextRead = 0L
    private var nextPackages = 0L
    private var retryAfter = 0L
    private var apps = emptyMap<Int, List<String>>()
    private var epoch = System.currentTimeMillis()
    @Volatile private var activeEpoch = -1L
    @Volatile private var acknowledged = false

    @Volatile private var targetSnapshot = emptyMap<Int, String>()
    @Volatile private var scopeSnapshot = "[]"
    fun requestRefresh() { nextPackages = 0; dirty.set(true) }
    fun adminTargets(): Map<Int, String> = targetSnapshot
    fun adminScope(): JSONArray = JSONArray(scopeSnapshot)

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1)
        require(android.os.Process.myUid() == 0) { "ROM daemon requires uid 0" }
        // Fail before probes, persistence or injection when a standalone backend owns this boot.
        StartupGuard.check()
        ServiceManager.waitForService("package")
        Looper.prepareMainLooper()
        val thread = ActivityThread.systemMain()
        val context = thread.getSystemContext() as Any
        val app = Application()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }.invoke(app, context)
        ActivityThread::class.java.getDeclaredField("mInitialApplication")
            .apply { isAccessible = true }.set(thread, app)
        android.security.keystore2.AndroidKeyStoreProvider.install()
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        Packages.init(app)

        // Harvest before injection, and preserve its frozen boot key across daemon/ROM updates.
        // No resetprop, WebUI, key-database writes, or automatic existing-key deletion.
        val harvest = Harvester.run(app)
        Control.onRomAck = { msg ->
            acknowledged = msg.optLong("epoch", -1) == activeEpoch &&
                msg.optBoolean("ok") && msg.optInt("profilesApplied") == expectedProfiles &&
                msg.optInt("profilesFailed") == 0
            publishReady()
        }
        Control.onRomPong = { publishReady() }
        Control.onRomDisconnect = {
            acknowledged = false
            publishReady()
        }
        RomAdmin.start(harvest)
        Control.start()
        Injector(File(args[0])).start()
        observer = object : FileObserver(File("/data/system/users/0"),
            CLOSE_WRITE or MOVED_TO or DELETE or CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "settings_secure.xml") dirty.set(true)
            }
        }.also { it.startWatching() }

        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                try {
                    if (SystemClock.elapsedRealtime() >= retryAfter) update(harvest)
                    if (SystemClock.elapsedRealtime() >= nextStatus) {
                        publishReady()
                        nextStatus = SystemClock.elapsedRealtime() + 3000
                    }
                } catch (error: Exception) {
                    // Do not log exceptions containing XML, command output, or private keys.
                    SystemLogger.warning("RomApp: refresh failed (${error.javaClass.simpleName}); disabling new interception")
                    configError = true
                    push(harvest, false)
                    dirty.set(true)
                    retryAfter = SystemClock.elapsedRealtime() + 10_000
                } finally {
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(tick)
        SystemLogger.info("RomApp: existing UN1CA Settings bridge started")
        Looper.loop()
    }

    private fun update(harvest: Harvester.Record) {
        val now = SystemClock.elapsedRealtime()
        val enabled = SystemProperties.get(PROPERTY, "false") in setOf("1", "true", "y", "yes", "on")
        // Property reads are in-process. The settings command is event driven, with a periodic
        // fallback for missed file events, atomic SettingsProvider writes, and imports while off.
        val refresh = dirty.getAndSet(false) || enabled != lastEnabled || now >= nextRead
        if (refresh) {
            val xml = readSetting(SETTING)
            profileConfig = readSetting(RomProfiles.SETTING)
            if (enabled && (apps.isEmpty() || now >= nextPackages)) {
                // One binder enumeration; no labels, icons, launchers, or per-app IPC polling.
                val pm = checkNotNull(Packages.packageManagerService())
                apps = pm.getInstalledApplications(0L, 0).list
                    .filter { it.uid in 10000..19999 }
                    .groupBy({ it.uid }, { it.packageName })
                nextPackages = now + 30_000
            }
            val overrideText = readSetting("unica_teesim_overrides")
            val edits = overrideText?.let { JSONObject(it) } ?: JSONObject()
            val allowed = setOf("attestationSecurityLevel", "keymasterSecurityLevel", "attestationVersion", "keymasterVersion")
            require(edits.keys().asSequence().all { it in allowed })
            val effective = Harvester.applyUserOverrides(harvest, edits.keys().asSequence().associateWith { edits.getString(it) })
            RomAdmin.updateHarvest(effective)
            val resolved = RomProfiles.resolve(profileConfig, xml, apps, effective)
            // A cleared current signer disables new interception, but keep the previous signer
            // in memory for existing simulator blobs during this daemon lifetime.
            if (resolved.length() == 0) {
                for (i in 0 until cachedProfiles.length()) cachedProfiles.getJSONObject(i).apply {
                    put("packages", JSONArray()); put("packageUsers", JSONArray())
                    put("uids", JSONArray()); put("uidPackages", JSONArray())
                }
            } else cachedProfiles = resolved
            configError = false
            push(effective, enabled)
            nextRead = now + 30_000
        }
        lastEnabled = enabled
    }

    private fun readSetting(name: String): String? {
        // The command executes SettingsProvider's system-server shell interface. A bare
        // app_process is not an AMS app and cannot reliably acquire a ContentProvider itself.
        val process = ProcessBuilder("/system/bin/settings", "--user", "0", "get", "secure", name)
            .redirectError(File("/dev/null")).start()
        val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val future = reader.submit<String> {
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = process.inputStream.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= MAX_XML) { "setting exceeds backend limit" }
                    out.write(buffer, 0, count)
                }
                String(out.toByteArray(), Charsets.UTF_8).trim()
            }
            val xml = future.get(10, TimeUnit.SECONDS)
            check(process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0)
            return xml.takeUnless { it.isEmpty() || it == "null" }
        } finally {
            process.destroyForcibly()
            reader.shutdownNow()
        }
    }

    private fun push(h: Harvester.Record, active: Boolean) {
        val b64 = Base64.getEncoder()
        val boot = JSONObject().apply {
            put("verifiedBootKey", b64.encodeToString(h.effectiveBootKey()))
            put("verifiedBootHash", b64.encodeToString(h.effectiveBootHash()))
            h.effectiveModuleHash()?.let { put("moduleHash", b64.encodeToString(it)) }
            put("deviceLocked", true)
            put("verifiedBootState", 0)
            put("strongBoxAvailable", h.strongBoxAvailable)
            put("attestVersionTee", h.effectiveInt("attestationVersion", Harvester.effectiveAttestation(h).second))
            put("attestVersionStrongBox", h.strongBoxAttestationVersion)
        }
        // Keep prior signers in memory when disabled so existing simulator blobs remain usable.
        val profiles = JSONArray(cachedProfiles.toString())
        if (!active) for (i in 0 until profiles.length()) {
            profiles.getJSONObject(i).apply {
                put("packages", JSONArray()); put("packageUsers", JSONArray())
                put("uids", JSONArray()); put("uidPackages", JSONArray())
            }
        }
        routingActive = active && (0 until profiles.length()).any { profiles.getJSONObject(it).getJSONArray("uids").length() > 0 }
        KeyboxInspector.setRomSigners(profiles)
        targetSnapshot = buildMap {
            for (i in 0 until profiles.length()) {
                val p = profiles.getJSONObject(i); val uids = p.getJSONArray("uids")
                val names = p.getJSONArray("uidPackages")
                for (j in 0 until uids.length()) put(uids.getInt(j), names.optString(j, ""))
            }
        }
        scopeSnapshot = JSONArray().apply {
            for (i in 0 until profiles.length()) {
                val p = profiles.getJSONObject(i)
                put(JSONObject().put("id", p.getString("id")).put("uids", p.getJSONArray("uids"))
                    .put("packages", p.getJSONArray("packages")))
            }
        }.toString()
        expectedProfiles = profiles.length()
        val msg = JSONObject().apply {
            put("type", "config")
            put("redactSalt", Redact.saltBase64())
            put("bootInfo", boot)
            put("profiles", profiles)
        }
        // Compare without epoch; unchanged Settings must not recreate TAs every poll.
        val wire = msg.toString()
        if (wire == lastWire) return
        val nextEpoch = ++epoch
        activeEpoch = nextEpoch
        acknowledged = false
        publishReady()
        msg.put("epoch", nextEpoch)
        Control.push(msg.toString())
        lastWire = wire
        SystemLogger.info("RomApp: config queued, requested=${active && profiles.length() > 0}")
    }

    private fun publishReady() {
        val enabled = SystemProperties.get(PROPERTY, "false") in setOf("1", "true", "y", "yes", "on")
        val value = if (enabled && routingActive && acknowledged && expectedProfiles > 0 && !configError) SystemClock.elapsedRealtime().toString() else "0"
        SystemProperties.set("sys.unica.teesim.ready", value)
        SystemProperties.set("sys.unica.teesim.connected", if (Control.isConnected()) "1" else "0")
        SystemProperties.set("sys.unica.teesim.ack", if (acknowledged && !configError) "1" else "0")
        // Loaded means the interceptor accepted every configured signer, not just XML present.
        SystemProperties.set("sys.unica.teesim.loaded", if (expectedProfiles > 0 && acknowledged) "1" else "0")
        SystemProperties.set("sys.unica.teesim.config_error", if (configError) "1" else "0")
        SystemProperties.set("sys.unica.teesim.heartbeat", SystemClock.elapsedRealtime().toString())
    }
}
