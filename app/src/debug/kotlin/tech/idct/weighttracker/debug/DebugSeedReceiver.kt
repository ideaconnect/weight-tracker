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
 * Extras: --ez behind   true (seed a plan the user is behind on)
 *         --ez unlock   true (grant the widget entitlement)
 *         --ez reminder true (leave the daily reminder switched on and armed)
 *         --ei minute   NNN  (reminder time as minutes past midnight, e.g. 1260 for 21:00)
 *         --ez clear    true (wipe everything instead of seeding)
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
                        reminder = intent.getBooleanExtra("reminder", false),
                        reminderMinute = intent.getIntExtra("minute", -1).takeIf { it in 0..1439 },
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
