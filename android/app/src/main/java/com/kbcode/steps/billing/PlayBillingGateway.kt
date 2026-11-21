package com.kbcode.steps.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class PlayBillingGateway(context: Context) : BillingGateway {
    override var onPurchases: (StoreResult<List<StorePurchase>>) -> Unit = {}
    private val packageName = context.packageName
    private var details: ProductDetails? = null
    private var offerToken: String? = null
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            onPurchases(if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                StoreResult.Success(mapPurchases(purchases.orEmpty()))
            } else failure(result))
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override suspend fun connect(): StoreResult<Unit> {
        if (client.isReady) return StoreResult.Success(Unit)
        return timed {
            suspendCancellableCoroutine { continuation ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (continuation.isActive) continuation.resume(unitResult(result))
                    }
                    override fun onBillingServiceDisconnected() {
                        if (continuation.isActive) continuation.resume(StoreResult.Failure(StoreError.OFFLINE))
                    }
                })
            }
        }
    }

    override suspend fun product(): StoreResult<LogoProduct> = timed {
        details = null
        offerToken = null
        suspendCancellableCoroutine { continuation ->
            val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(REMOVE_LOGO).setProductType(BillingClient.ProductType.INAPP).build()
            )).build()
            client.queryProductDetailsAsync(params) { result, response ->
                if (!continuation.isActive) return@queryProductDetailsAsync
                val product = response.productDetailsList.firstOrNull { it.productId == REMOVE_LOGO }
                // One non-consumable buy option; never select a rental or a temporary discount.
                val offer = product?.oneTimePurchaseOfferDetailsList?.firstOrNull {
                    it.rentalDetails == null && it.discountDisplayInfo == null
                }
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    continuation.resume(failure(result))
                } else if (product == null || offer == null) {
                    continuation.resume(StoreResult.Failure(StoreError.UNAVAILABLE))
                } else {
                    details = product
                    offerToken = offer.offerToken
                    continuation.resume(StoreResult.Success(LogoProduct(product.name, offer.formattedPrice)))
                }
            }
        }
    }

    override suspend fun purchases(): StoreResult<List<StorePurchase>> = timed {
        suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                if (continuation.isActive) continuation.resume(
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        StoreResult.Success(mapPurchases(purchases))
                    } else failure(result)
                )
            }
        }
    }

    override suspend fun acknowledge(token: String): StoreResult<Unit> = timed {
        suspendCancellableCoroutine { continuation ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token).build()) { result ->
                if (continuation.isActive) continuation.resume(unitResult(result))
            }
        }
    }

    override fun launch(activity: Activity): StoreResult<Unit> {
        val product = details ?: return StoreResult.Failure(StoreError.UNAVAILABLE)
        val token = offerToken ?: return StoreResult.Failure(StoreError.UNAVAILABLE)
        if (activity.isFinishing || activity.isDestroyed) return StoreResult.Failure(StoreError.ERROR)
        return try { unitResult(client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product).setOfferToken(token).build())).build()))
        } catch (_: RuntimeException) {
            StoreResult.Failure(StoreError.ERROR)
        }
    }

    private fun mapPurchases(purchases: List<Purchase>) = purchases
        .filter { it.packageName == packageName }
        .map { StorePurchase(it.products, it.purchaseToken,
            it.purchaseState == Purchase.PurchaseState.PURCHASED,
            it.purchaseState == Purchase.PurchaseState.PENDING, it.isAcknowledged) }

    private fun unitResult(result: BillingResult): StoreResult<Unit> =
        if (result.responseCode == BillingClient.BillingResponseCode.OK) StoreResult.Success(Unit) else failure(result)

    private fun failure(result: BillingResult) = StoreResult.Failure(when (result.responseCode) {
        BillingClient.BillingResponseCode.USER_CANCELED -> StoreError.CANCELED
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> StoreError.ALREADY_OWNED
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> StoreError.OFFLINE
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> StoreError.UNAVAILABLE
        else -> StoreError.ERROR
    })

    private suspend fun <T> timed(block: suspend () -> StoreResult<T>): StoreResult<T> =
        try {
            withTimeoutOrNull(15_000) { block() } ?: StoreResult.Failure(StoreError.OFFLINE)
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (_: RuntimeException) {
            StoreResult.Failure(StoreError.ERROR)
        }

    override fun close() { client.endConnection() }
}
