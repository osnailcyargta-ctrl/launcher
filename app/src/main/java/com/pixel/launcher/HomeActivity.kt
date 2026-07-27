package com.pixel.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.pixel.launcher.ui.LauncherRoot

/**
 * The home screen itself.
 *
 * Declared as a singleTask HOME activity, so the system reuses this instance
 * every time HOME is pressed instead of building a new one.
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
