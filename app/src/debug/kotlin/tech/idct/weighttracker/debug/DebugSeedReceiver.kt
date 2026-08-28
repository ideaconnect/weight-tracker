package tech.idct.weighttracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only fixture loader, so the screens can be exercised without tapping a
 * couple of months of weights in by hand. Never compiled into a release build.
 *
 *   adb shell am broadcast -a tech.idct.weighttracker.debug.SEED \
 *     -n tech.idct.weighttracker.debug/tech.idct.weighttracker.debug.DebugSeedReceiver
 *
 * Extras: --ez behind true (seed a plan the user is behind on)
 *         --ez unlock true (grant the widget entitlement)
 *         --ez clear  true (wipe everything instead of seeding)
 */
class DebugSeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when {
                    intent.getBooleanExtra("hcwrite", false) -> SeedData.hcWrite(app)
                    intent.getBooleanExtra("clear", false) -> SeedData.clear(app)
                    else -> SeedData.seed(
                        app,
                        behind = intent.getBooleanExtra("behind", false),
                        unlock = intent.getBooleanExtra("unlock", false),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
