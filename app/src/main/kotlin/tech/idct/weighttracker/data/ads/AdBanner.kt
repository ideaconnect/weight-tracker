package tech.idct.weighttracker.data.ads

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Section 10: one 320x50 banner at the bottom of the home screen only — never over
 * the chart, never on sheets, never interstitial. Removed permanently on purchase.
 */
object Ads {

    /**
     * Google's public test IDs. Swap for the real AdMob unit before release; the app
     * ID in AndroidManifest.xml has to change with it.
     */
    const val BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    private val initialised = AtomicBoolean(false)

    fun initialise(context: Context) {
        if (!initialised.compareAndSet(false, true)) return
        runCatching { MobileAds.initialize(context.applicationContext) }
            .onFailure { Log.w("Ads", "Mobile Ads failed to initialise", it) }
    }
}

@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.width(320.dp).height(50.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = Ads.BANNER_UNIT_ID
                // Section 10 asks for a 320x50 banner exactly. The AdView is filled to
                // the 320x50 box the composable reserves; letting it size itself lets
                // the fill widen to a 468x60 creative.
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                runCatching { loadAd(AdRequest.Builder().build()) }
                    .onFailure { Log.w("Ads", "Banner request failed", it) }
            }
        },
    )
}
