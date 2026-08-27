package tech.idct.weighttracker.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tech.idct.weighttracker.R
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.widget.WidgetUpdater

/**
 * Section 10: one non-consumable product unlocks every widget and removes the
 * banner permanently. No subscriptions, no tiers.
 *
 * Section 3: the entitlement is cached locally and re-verified against Play Billing
 * on launch, but a cached true stays true offline — this class only ever writes a
 * grant, never revokes one because the network was missing.
 */
class BillingManager(
    private val context: Context,
    private val repo: WeightRepository,
) {
    companion object {
        private const val TAG = "BillingManager"
    }

    /** The single non-consumable in-app product, from res/values/oauth.xml. */
    private val productId: String get() = context.getString(R.string.billing_product_id)

    enum class Availability { UNKNOWN, READY, UNAVAILABLE }

    data class State(
        val availability: Availability = Availability.UNKNOWN,
        /** Localised price from Play, e.g. "$1.50". Null until Play answers. */
        val price: String? = null,
        val purchaseInFlight: Boolean = false,
        val message: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null
    private var connecting = false

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun connect() {
        if (client.isReady || connecting) {
            if (client.isReady) refresh()
            return
        }
        connecting = true
        runCatching {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _state.value = _state.value.copy(availability = Availability.READY, message = null)
                        refresh()
                    } else {
                        _state.value = _state.value.copy(
                            availability = Availability.UNAVAILABLE,
                            message = result.debugMessage.ifBlank { "Google Play billing is unavailable" },
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                    _state.value = _state.value.copy(availability = Availability.UNAVAILABLE)
                }
            })
        }.onFailure {
            connecting = false
            _state.value = _state.value.copy(
                availability = Availability.UNAVAILABLE,
                message = "Google Play billing is unavailable on this device",
            )
        }
    }

    private fun refresh() {
        queryProduct()
        restorePurchases()
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Product query failed: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val details = queryResult.productDetailsList.firstOrNull { it.productId == productId }
            productDetails = details
            _state.value = _state.value.copy(
                price = details?.oneTimePurchaseOfferDetails?.formattedPrice
            )
        }
    }

    /**
     * Re-verify on launch. A missing or failed response leaves any cached grant
     * exactly as it was.
     */
    fun restorePurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any {
                productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (owned) {
                purchases.forEach { acknowledgeIfNeeded(it) }
                grant()
            }
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (details == null) {
            _state.value = _state.value.copy(
                message = "This device cannot reach Google Play right now.",
            )
            connect()
            return
        }
        _state.value = _state.value.copy(purchaseInFlight = true, message = null)
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(
                purchaseInFlight = false,
                message = result.debugMessage.ifBlank { "Could not open the Play billing sheet" },
            )
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        _state.value = _state.value.copy(purchaseInFlight = false)
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty()
                    .filter { productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach {
                        acknowledgeIfNeeded(it)
                        grant()
                    }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> Unit

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                grant()
                restorePurchases()
            }

            else -> _state.value = _state.value.copy(
                message = result.debugMessage.ifBlank { "The purchase did not complete" }
            )
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { /* A failed acknowledgement is retried on the next launch. */ }
    }

    private fun grant() {
        scope.launch {
            if (!repo.isUnlocked()) {
                repo.setUnlocked(true)
                WidgetUpdater.updateAll(context)
            }
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun release() {
        runCatching { client.endConnection() }
    }
}
