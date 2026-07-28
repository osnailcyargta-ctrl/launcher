package com.pixel.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.launcher.ui.LauncherRoot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The home screen itself.
 *
 * Declared as a singleTask HOME activity, so the system reuses this instance
 * every time HOME is pressed instead of building a new one.
 *
 * The window is configured here rather than in a styles resource: the launcher
 * keeps its look in Kotlin, which means the wallpaper can be switched on and off
 * from settings without recreating the activity.
 */
class HomeActivity : ComponentActivity() {

    /** Bumped on every HOME press so the drawer can scroll back to the top. */
    private val homePresses = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val app = application as PixelLauncherApp

        // Drawing the wallpaper costs real power, so the flag is only set while
        // the user actually asked to see it.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.prefs.settings
                    .map { it.showWallpaper }
                    .collect { show ->
                        if (show) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                        }
                    }
            }
        }

        setContent {
            LauncherRoot(homePresses = homePresses)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homePresses.intValue++
    }

    override fun onResume() {
        super.onResume()
        // Decode the sound bank while the user is still looking at the drawer,
        // so the first tap is never the one that pays for it.
        (application as PixelLauncherApp).sfx.warmUp()
    }
}
