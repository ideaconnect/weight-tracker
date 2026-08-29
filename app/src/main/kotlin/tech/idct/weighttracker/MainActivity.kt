package tech.idct.weighttracker

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import tech.idct.weighttracker.data.repo.ThemePrefs
import tech.idct.weighttracker.ui.AppViewModel
import tech.idct.weighttracker.ui.nav.WeightTrackerApp

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ROUTE_HOME = "home"
        const val ROUTE_LOG = "log"
        const val ROUTE_PAYWALL = "paywall"
        const val ROUTE_PLACEMENT = "placement"
    }

    private val viewModel: AppViewModel by viewModels()

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Section 12: dark is true black, so the launch window must not flash white.
        val dark = ThemePrefs.isDark(this)
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()))
        enableEdgeToEdge(
            statusBarStyle = if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
        )
        super.onCreate(savedInstanceState)
        // The launch intent is sticky: a recreation — dark mode flipping, a font
        // size change, a return from Recents after process death — arrives with the
        // same intent, and the log sheet a reminder had opened hours ago came back.
        if (savedInstanceState == null) pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)

        setContent {
            WeightTrackerApp(
                viewModel = viewModel,
                initialRoute = pendingRoute,
                onRouteConsumed = { pendingRoute = null },
            )
        }

        // Section 4 rule 1: autosync runs on every app open (foreground resume).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.onAppResumed()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(EXTRA_ROUTE)
    }
}
