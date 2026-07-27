package com.pixel.launcher.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The list of every launchable app on the device, including work-profile apps.
 *
 * Uses [LauncherApps] rather than `PackageManager.queryIntentActivities` because
 * it is the API built for home screens: it reports multi-user profiles and pushes
 * install/remove/update callbacks, so the drawer never has to poll or re-scan.
 */
class AppRepository(private val context: Context) {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var refreshJob: Job? = null
    private var registered = false

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = scheduleRefresh()

        override fun onPackageAdded(packageName: String, user: UserHandle) = scheduleRefresh()

        override fun onPackageChanged(packageName: String, user: UserHandle) = scheduleRefresh()

        override fun onPackagesAvailable(
            packageNames: Array<String>,
            user: UserHandle,
            replacing: Boolean,
        ) = scheduleRefresh()

        override fun onPackagesUnavailable(
            packageNames: Array<String>,
            user: UserHandle,
            replacing: Boolean,
        ) = scheduleRefresh()
    }

    fun start() {
        if (!registered) {
            runCatching { launcherApps.registerCallback(callback, mainHandler) }
            registered = true
        }
        scheduleRefresh(immediate = true)
    }

    /**
     * Reloads the app list. Installs arrive as a burst of callbacks, so the work
     * is debounced instead of scanning the package manager several times in a row.
     */
    fun scheduleRefresh(immediate: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            val next = query()
            if (next.isNotEmpty() || _loaded.value) _apps.value = next
            _loaded.value = true
        }
    }

    private fun query(): List<AppEntry> {
        val profiles = runCatching { userManager.userProfiles }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(Process.myUserHandle())

        val result = ArrayList<AppEntry>(192)
        for (user in profiles) {
            val activities = runCatching { launcherApps.getActivityList(null, user) }
                .onFailure { Log.w(TAG, "activity list failed for $user", it) }
                .getOrNull()
                ?: continue
            val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(0L)
            for (info in activities) {
                val component = info.componentName
                val label = runCatching { info.label?.toString() }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: component.packageName
                result += AppEntry(
                    packageName = component.packageName,
                    className = component.className,
                    user = user,
                    userSerial = serial,
                    label = label,
                    isSystem = info.applicationInfo.flags and
                        (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    info = info,
                )
            }
        }
        result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        return result
    }

    // ----------------------------------------------------------------- actions

    /** Launches [entry]; [source] is used for the system's zoom animation. */
    fun launch(entry: AppEntry, source: Rect?, opts: Bundle?): Boolean {
        val component = ComponentName(entry.packageName, entry.className)
        val direct = runCatching {
            launcherApps.startMainActivity(component, entry.user, source, opts)
        }
        if (direct.isSuccess) return true
        Log.w(TAG, "startMainActivity failed for ${entry.key}", direct.exceptionOrNull())

        // Fallback for the rare activity that LauncherApps refuses to start.
        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .setSourceBounds(source)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent, opts)
        }.isSuccess
    }

    fun openAppInfo(entry: AppEntry, source: Rect?): Boolean = runCatching {
        launcherApps.startAppDetailsActivity(
            ComponentName(entry.packageName, entry.className),
            entry.user,
            source,
            null,
        )
    }.isSuccess

    fun requestUninstall(entry: AppEntry): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_USER, entry.user)
        context.startActivity(intent)
    }.isSuccess

    fun find(key: String): AppEntry? = _apps.value.firstOrNull { it.key == key }

    private companion object {
        const val TAG = "AppRepository"
        const val DEBOUNCE_MS = 400L
    }
}
