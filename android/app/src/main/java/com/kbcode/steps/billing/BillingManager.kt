package com.kbcode.steps.billing

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val REMOVE_LOGO = "remove_logo"

enum class BillingStatus { IDLE, CHECKING, READY, PURCHASING, PENDING, ACKNOWLEDGING, CANCELED, OFFLINE, UNAVAILABLE, ERROR, NOTHING_TO_RESTORE }
enum class StoreError { OFFLINE, CANCELED, ALREADY_OWNED, UNAVAILABLE, ERROR }
sealed interface StoreResult<out T> {
    data class Success<T>(val value: T) : StoreResult<T>
    data class Failure(val error: StoreError) : StoreResult<Nothing>
}
data class LogoProduct(val name: String, val price: String)
data class StorePurchase(val products: List<String>, val token: String, val purchased: Boolean, val pending: Boolean, val acknowledged: Boolean)
data class LogoBillingState(
    val owned: Boolean = false,
    val product: LogoProduct? = null,
    val status: BillingStatus = BillingStatus.IDLE,
    /** Last purchase-flow outcome (cancel or failure), retained across refreshes. */
    val notice: BillingStatus? = null,
    /** Tracked per failed token and retained until resolution of those tokens is confirmed. */
    val ackFailure: BillingStatus? = null
) {
    val showLogo get() = !owned
    val busy get() = status in setOf(BillingStatus.CHECKING, BillingStatus.PURCHASING, BillingStatus.ACKNOWLEDGING)
    val canBuy get() = !owned && product != null && status in setOf(BillingStatus.READY, BillingStatus.CANCELED)
    /** Status whose message the UI displays; null displays no status message. */
    val displayStatus: BillingStatus? get() = when {
        status == BillingStatus.PENDING -> BillingStatus.PENDING
        ackFailure != null -> ackFailure
        !owned && status in setOf(
            BillingStatus.CANCELED,
            BillingStatus.OFFLINE,
            BillingStatus.UNAVAILABLE,
            BillingStatus.ERROR
        ) -> status
        notice != null && !owned -> notice
        owned -> null
        else -> status.takeUnless { it == BillingStatus.READY }
    }
}

interface LogoOwnershipStore {
    var owned: Boolean
}
interface BillingGateway {
    var onPurchases: (StoreResult<List<StorePurchase>>) -> Unit
    suspend fun connect(): StoreResult<Unit>
    suspend fun product(): StoreResult<LogoProduct>
    suspend fun purchases(): StoreResult<List<StorePurchase>>
    suspend fun acknowledge(token: String): StoreResult<Unit>
    fun launch(activity: Activity): StoreResult<Unit>
    fun close()
}

/** Serializes restoration and purchase callbacks. Tokens are never persisted or logged. */
class BillingManager(
    private val gateway: BillingGateway,
    private val store: LogoOwnershipStore,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(LogoBillingState(owned = store.owned))
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var refreshing = false
    private var manualPending = false
    /** Confirmed tokens are retained only in memory for this manager's lifetime. */
    private val acknowledgedTokens: MutableSet<String> = mutableSetOf()
    /** Failed tokens are retained only in memory, never persisted or logged. */
    private val ackFailedTokens: MutableSet<String> = mutableSetOf()

    init {
        gateway.onPurchases = { result ->
            scope.launch {
                mutex.withLock {
                    when (result) {
                        is StoreResult.Success -> process(
                            result.value,
                            authoritative = false,
                            purchaseFlow = true
                        )
                        is StoreResult.Failure -> if (result.error == StoreError.ALREADY_OWNED) {
                            restore(purchaseFlow = true)
                        } else {
                            setNotice(failStatus(result.error))
                            fail(result.error)
                        }
                    }
                }
            }
        }
    }

    fun refresh(manual: Boolean = false) {
        if (refreshing) {
            if (manual) manualPending = true
            return
        }
        refreshing = true
        scope.launch {
            try {
                mutex.withLock {
                    // A successful inventory query reconciles a missing purchase callback on resume.
                    // Keep the current product while checking: clearing it collapses the
                    // purchase section (button and price disappear, then return), which
                    // reads as flicker on every refresh, including manual restores.
                    mutableState.value = state.value.copy(status = BillingStatus.CHECKING)
                    when (val connected = gateway.connect()) {
                        is StoreResult.Failure -> { fail(connected.error); return@withLock }
                        is StoreResult.Success -> Unit
                    }
                    val restored = restore(purchaseFlow = false)
                    if (manual && restored) {
                        // A manual restore that finds nothing must say so; ending silently
                        // at READY leaves the user unsure the tap did anything.
                        if (!state.value.owned && state.value.status == BillingStatus.READY &&
                            state.value.ackFailure == null
                        ) {
                            setNotice(BillingStatus.NOTHING_TO_RESTORE)
                        } else {
                            setNotice(null)
                        }
                    }
                    // process() may already have settled on READY, but the refresh is
                    // not done: hold CHECKING across the product fetch so canBuy stays
                    // false for the whole refresh (the product itself stays visible).
                    val settledReady = state.value.status == BillingStatus.READY
                    if (settledReady) update(BillingStatus.CHECKING)
                    when (val product = gateway.product()) {
                        is StoreResult.Success -> {
                            mutableState.value = state.value.copy(product = product.value)
                            if (settledReady && state.value.status == BillingStatus.CHECKING) {
                                update(BillingStatus.READY)
                            }
                        }
                        is StoreResult.Failure -> {
                            mutableState.value = state.value.copy(product = null)
                            if (settledReady || state.value.status == BillingStatus.READY) fail(product.error)
                        }
                    }
                }
            } finally {
                refreshing = false
                if (manualPending) {
                    manualPending = false
                    refresh(manual = true)
                }
            }
        }
    }

    fun buy(activity: Activity) {
        if (refreshing || !state.value.canBuy) return
        setNotice(null)
        update(BillingStatus.PURCHASING)
        when (val result = gateway.launch(activity)) {
            is StoreResult.Success -> Unit // Completion comes from onPurchases, never from launch OK.
            is StoreResult.Failure -> if (result.error == StoreError.ALREADY_OWNED) {
                update(BillingStatus.IDLE)
                refresh()
            } else {
                setNotice(failStatus(result.error))
                fail(result.error)
            }
        }
    }

    private suspend fun restore(purchaseFlow: Boolean): Boolean {
        return when (val result = gateway.purchases()) {
            is StoreResult.Success -> {
                process(result.value, authoritative = true, purchaseFlow = purchaseFlow)
                true
            }
            is StoreResult.Failure -> {
                fail(result.error)
                false
            }
        }
    }

    private suspend fun process(
        purchases: List<StorePurchase>,
        authoritative: Boolean,
        purchaseFlow: Boolean
    ) {
        val relevant = purchases.filter { REMOVE_LOGO in it.products }
        if (authoritative) {
            val recordsByToken = relevant.groupBy { it.token }
            acknowledgedTokens.retainAll(recordsByToken.keys)
            ackFailedTokens.removeAll { token ->
                val records = recordsByToken[token]
                records == null || records.all { !it.purchased && it.pending }
            }
        }
        relevant.filter { it.acknowledged }.forEach {
            acknowledgedTokens.add(it.token)
            ackFailedTokens.remove(it.token)
        }
        if (ackFailedTokens.isEmpty()) setAckFailure(null)
        val completed = relevant.filter { it.purchased }
        if (completed.isNotEmpty()) {
            // Acknowledge each outstanding token while preserving proven entitlement on failure.
            var entitlementProven = completed.any {
                it.acknowledged || it.token in acknowledgedTokens
            }
            val unacknowledged = completed.filter {
                !it.acknowledged && it.token !in acknowledgedTokens
            }.distinctBy { it.token }
            if (unacknowledged.isNotEmpty()) {
                update(BillingStatus.ACKNOWLEDGING)
                for (purchase in unacknowledged) {
                    when (val result = gateway.acknowledge(purchase.token)) {
                        is StoreResult.Failure -> {
                            ackFailedTokens.add(purchase.token)
                            val failure = failStatus(result.error)
                            setAckFailure(failure)
                            if (purchaseFlow) setNotice(failure)
                            if (entitlementProven) setOwned(true)
                            fail(result.error)
                            return
                        }
                        is StoreResult.Success -> {
                            acknowledgedTokens.add(purchase.token)
                            ackFailedTokens.remove(purchase.token)
                            if (ackFailedTokens.isEmpty()) setAckFailure(null)
                            entitlementProven = true
                        }
                    }
                }
            }
            if (ackFailedTokens.isEmpty()) setAckFailure(null)
            if (entitlementProven) {
                setOwned(true)
                setNotice(null)
            }
            update(BillingStatus.READY)
        } else if (authoritative) {
            if (ackFailedTokens.isEmpty()) setAckFailure(null)
            setOwned(false)
            if (relevant.any { it.pending }) {
                setNotice(null)
                update(BillingStatus.PENDING)
            } else {
                update(BillingStatus.READY)
            }
        } else if (relevant.any { it.pending } && !state.value.owned) {
            setNotice(null)
            update(BillingStatus.PENDING)
        } else if (state.value.status == BillingStatus.PURCHASING) {
            // An empty/unrelated update is not proof of ownership or revocation.
            setNotice(BillingStatus.ERROR)
            update(BillingStatus.ERROR)
        }
    }

    private fun setOwned(owned: Boolean) {
        store.owned = owned
        mutableState.value = state.value.copy(owned = owned)
    }
    private fun setNotice(notice: BillingStatus?) { mutableState.value = state.value.copy(notice = notice) }
    private fun setAckFailure(ackFailure: BillingStatus?) { mutableState.value = state.value.copy(ackFailure = ackFailure) }
    private fun update(status: BillingStatus) { mutableState.value = state.value.copy(status = status) }
    private fun failStatus(error: StoreError): BillingStatus = when (error) {
        StoreError.OFFLINE -> BillingStatus.OFFLINE
        StoreError.CANCELED -> BillingStatus.CANCELED
        StoreError.UNAVAILABLE -> BillingStatus.UNAVAILABLE
        else -> BillingStatus.ERROR
    }
    private fun fail(error: StoreError) = update(failStatus(error))
    fun close() = gateway.close()
}
