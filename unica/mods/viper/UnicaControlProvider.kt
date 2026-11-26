// SPDX-License-Identifier: GPL-3.0-or-later
package com.llsl.viper4android.unica

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.service.ViperService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Settings uses the same DataStore master switch as the app and QS tile. */
class UnicaControlProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access { fun repository(): ViperRepository }

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = requireNotNull(context)
        ctx.enforceCallingPermission("android.permission.WRITE_SECURE_SETTINGS", "Settings control")
        // In addition to the manifest permission, restrict this bridge to Settings.
        val callers = ctx.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if ("com.android.settings" !in callers) throw SecurityException("Settings only")
        require(method == "get" || method == "set") { "Unknown operation" }
        val identity = Binder.clearCallingIdentity()
        try {
            val repository = EntryPointAccessors.fromApplication(ctx, Access::class.java).repository()
            return runBlocking(Dispatchers.IO) {
                if (method == "set") {
                    require(extras?.containsKey("enabled") == true)
                    val next = extras.getBoolean("enabled")
                    val previous = repository.getBooleanPreference(ViperRepository.PREF_MASTER_ENABLE).first()
                    repository.setBooleanPreference(ViperRepository.PREF_MASTER_ENABLE, next)
                    try {
                        ViperService.toggleMaster(ctx, next)
                    } catch (failure: RuntimeException) {
                        repository.setBooleanPreference(ViperRepository.PREF_MASTER_ENABLE, previous)
                        throw failure
                    }
                }
                Bundle().apply {
                    putBoolean("enabled", repository.getBooleanPreference(ViperRepository.PREF_MASTER_ENABLE).first())
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
