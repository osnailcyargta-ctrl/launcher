package com.pixel.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import com.pixel.launcher.audio.Sfx
import com.pixel.launcher.core.AppRepository
import com.pixel.launcher.core.IconLoader
import com.pixel.launcher.core.Prefs
import com.pixel.launcher.core.UsageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Holds the launcher's singletons.
 *
 * A launcher process is long-lived, so everything here is created once and
 * nothing is rebuilt on rotation or when the user comes back from another app.
 */
class PixelLauncherApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var usage: UsageStore
        private set
    lateinit var icons: IconLoader
        private set
    lateinit var sfx: Sfx
        private set
    lateinit var apps: AppRepository
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        prefs = Prefs(this)
        usage = UsageStore(this)
        icons = IconLoader(this)
        sfx = Sfx(this, prefs)
        apps = AppRepository(this)

        icons.applySignature(prefs.settings.value.iconSignature)
        apps.start()

        // Forget ranking data for apps that are no longer installed.
        scope.launch {
            apps.apps.collectLatest { list ->
                usage.prune(list.mapTo(HashSet(list.size)) { it.key })
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // The drawer is off screen; give the icon cache back to the system.
            icons.trim()
        }
    }
}
