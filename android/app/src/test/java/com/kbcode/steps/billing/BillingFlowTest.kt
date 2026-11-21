package com.kbcode.steps.billing

import android.app.Activity
import android.app.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BillingFlowTest {
    private class Store : LogoOwnershipStore { override var owned = false }
    private class Play : BillingGateway {
        override var onPurchases: (StoreResult<List<StorePurchase>>) -> Unit = {}
        var launches = 0
        var queries = 0
        var inventory: List<StorePurchase> = emptyList()
        var queryGate: CompletableDeferred<Unit>? = null
        var acknowledgements = 0
        var launchResult: StoreResult<Unit> = StoreResult.Success(Unit)
        override suspend fun connect(): StoreResult<Unit> = StoreResult.Success(Unit)
        override suspend fun product(): StoreResult<LogoProduct> = StoreResult.Success(LogoProduct("Name", "Price"))
        override suspend fun purchases(): StoreResult<List<StorePurchase>> {
            queries++
            queryGate?.await()
            return StoreResult.Success(inventory)
        }
        override suspend fun acknowledge(token: String): StoreResult<Unit> {
            acknowledgements++
            return StoreResult.Success(Unit)
        }
        override fun launch(activity: Activity): StoreResult<Unit> { launches++; return launchResult }
        override fun close() {}
    }

    @Test fun missingCallbackAndEmptyResumeQueryAllowRetryWithoutDuplicateLaunches() = runTest {
        val play = Play()
        val manager = BillingManager(play, Store(), this)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        manager.refresh(); runCurrent()
        manager.buy(activity); manager.buy(activity)
        assertEquals(1, play.launches)
        assertEquals(BillingStatus.PURCHASING, manager.state.value.status)
        assertTrue(manager.state.value.busy)
        assertFalse(manager.state.value.canBuy)
        controller.pause().stop().destroy()
        val recreated = Robolectric.buildActivity(Activity::class.java).setup().get()
        play.queryGate = CompletableDeferred()
        manager.refresh(); manager.refresh()
        manager.buy(recreated)
        runCurrent()
        manager.refresh(); manager.buy(recreated)
        assertEquals(2, play.queries)
        assertEquals(1, play.launches)
        assertTrue(manager.state.value.busy)
        play.queryGate!!.complete(Unit); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertTrue(manager.state.value.showLogo)
        assertFalse(manager.state.value.busy)
        assertTrue(manager.state.value.canBuy)
        manager.refresh(); runCurrent()
        assertEquals(3, play.queries)
        manager.buy(recreated); manager.buy(recreated)
        assertEquals(2, play.launches)
    }

    @Test fun purchasedCallbackGrantsOwnershipBeforeDuringOrAfterResumeQuery() = runTest {
        for (timing in listOf("before", "during", "after")) {
            val play = Play()
            val manager = BillingManager(play, Store(), this)
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            manager.refresh(); runCurrent()
            manager.buy(activity)
            val purchased = StorePurchase(listOf(REMOVE_LOGO), "synthetic", true, false, false)
            if (timing == "before") {
                play.onPurchases(StoreResult.Success(listOf(purchased))); runCurrent()
                play.inventory = listOf(purchased.copy(acknowledged = true))
            }
            play.queryGate = CompletableDeferred()
            manager.refresh(); runCurrent()
            if (timing == "during") play.onPurchases(StoreResult.Success(listOf(purchased)))
            play.queryGate!!.complete(Unit); runCurrent()
            if (timing == "after") {
                play.onPurchases(StoreResult.Success(listOf(purchased))); runCurrent()
            }
            assertTrue(manager.state.value.owned)
            assertFalse(manager.state.value.canBuy)
            assertEquals(1, play.acknowledgements)
            manager.buy(activity)
            assertEquals(1, play.launches)
        }
    }

    @Test fun pendingPreventsPurchaseUntilSuccessfulQueryConfirmsItIsGone() = runTest {
        val play = Play()
        val manager = BillingManager(play, Store(), this)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.refresh(); runCurrent()
        manager.buy(activity)
        play.inventory = listOf(StorePurchase(listOf(REMOVE_LOGO), "synthetic", false, true, false))
        play.onPurchases(StoreResult.Success(play.inventory)); runCurrent()
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.PENDING, manager.state.value.status)
        assertFalse(manager.state.value.owned)
        assertFalse(manager.state.value.canBuy)
        manager.buy(activity)
        assertEquals(1, play.launches)
        assertEquals(0, play.acknowledgements)
        play.inventory = emptyList()
        manager.refresh(); runCurrent()
        assertTrue(manager.state.value.canBuy)
        assertFalse(manager.state.value.busy)
        manager.buy(activity); manager.buy(activity)
        assertEquals(2, play.launches)
    }

    @Test fun synchronousLaunchFailureKeepsFreeStateAndAllowsRestoration() = runTest {
        val play = Play().apply { launchResult = StoreResult.Failure(StoreError.OFFLINE) }
        val manager = BillingManager(play, Store(), this)
        manager.refresh(); runCurrent()
        manager.buy(Robolectric.buildActivity(Activity::class.java).setup().get())
        assertTrue(manager.state.value.showLogo)
        assertEquals(BillingStatus.OFFLINE, manager.state.value.status)
        assertFalse(manager.state.value.busy)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
    }

    @Test fun synchronousLaunchFailureSetsNoticeThatSurvivesRefresh() = runTest {
        val play = Play().apply {
            launchResult = StoreResult.Failure(StoreError.OFFLINE)
        }
        val manager = BillingManager(play, Store(), this)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            manager.refresh(); runCurrent()
            manager.buy(controller.get())

            assertEquals(1, play.launches)
            assertEquals(BillingStatus.OFFLINE, manager.state.value.notice)
            assertEquals(BillingStatus.OFFLINE, manager.state.value.displayStatus)
            assertFalse(manager.state.value.owned)

            manager.refresh(); runCurrent()
            assertEquals(BillingStatus.READY, manager.state.value.status)
            assertEquals(BillingStatus.OFFLINE, manager.state.value.notice)
            assertEquals(BillingStatus.OFFLINE, manager.state.value.displayStatus)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun alreadyOwnedLaunchFailureRestoresInsteadOfGranting() = runTest {
        val play = Play().apply { launchResult = StoreResult.Failure(StoreError.ALREADY_OWNED) }
        val store = Store()
        val manager = BillingManager(play, store, this)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.refresh(); runCurrent()
        val queries = play.queries
        manager.buy(activity); runCurrent()
        assertTrue(play.queries > queries)
        assertFalse(store.owned)
        assertFalse(manager.state.value.owned)
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(1, play.launches)
    }

    @Test fun cancelNoticeSurvivesRefreshAndRetryLaunchesAgain() = runTest {
        val play = Play()
        val manager = BillingManager(play, Store(), this)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.refresh(); runCurrent()
        manager.buy(activity)
        assertEquals(1, play.launches)
        play.onPurchases(StoreResult.Failure(StoreError.CANCELED)); runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.displayStatus)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.CANCELED, manager.state.value.displayStatus)
        assertTrue(manager.state.value.canBuy)
        manager.buy(activity)
        assertEquals(2, play.launches)
        assertEquals(BillingStatus.PURCHASING, manager.state.value.displayStatus)
    }

    @Test fun cancelNoticeArrivingDuringRefreshIsRetained() = runTest {
        val play = Play()
        val manager = BillingManager(play, Store(), this)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.refresh(); runCurrent()
        manager.buy(activity)
        play.queryGate = CompletableDeferred()
        manager.refresh(); runCurrent()
        play.onPurchases(StoreResult.Failure(StoreError.CANCELED))
        play.queryGate!!.complete(Unit); runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.displayStatus)
        assertTrue(manager.state.value.canBuy)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.CANCELED, manager.state.value.displayStatus)
    }

    @Test fun errorNoticeSurvivesRefreshAndAllowsRetry() = runTest {
        val play = Play()
        val manager = BillingManager(play, Store(), this)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.refresh(); runCurrent()
        manager.buy(activity)
        play.onPurchases(StoreResult.Failure(StoreError.ERROR)); runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
        assertTrue(manager.state.value.canBuy)
        manager.buy(activity)
        assertEquals(2, play.launches)
    }

    @Test fun cancelNoticeArrivingAfterRefreshCompletesIsRetained() = runTest {
        val play = Play()
        val manager = BillingManager(play, Store(), this)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.refresh(); runCurrent()
        manager.buy(activity)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        play.onPurchases(StoreResult.Failure(StoreError.CANCELED)); runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.displayStatus)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.CANCELED, manager.state.value.displayStatus)
    }

    @Test fun errorNoticeArrivingAfterRefreshCompletesIsRetained() = runTest {
        val play = Play()
        val manager = BillingManager(play, Store(), this)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.refresh(); runCurrent()
        manager.buy(activity)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        play.onPurchases(StoreResult.Failure(StoreError.ERROR)); runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
    }

    @Test fun errorNoticeArrivingDuringRefreshIsRetained() = runTest {
        val play = Play()
        val manager = BillingManager(play, Store(), this)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.refresh(); runCurrent()
        manager.buy(activity)
        play.queryGate = CompletableDeferred()
        manager.refresh(); runCurrent()
        play.onPurchases(StoreResult.Failure(StoreError.ERROR))
        play.queryGate!!.complete(Unit); runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
    }

    @Test fun emptyCallbackDuringPurchaseSetsRetainedErrorNotice() = runTest {
        val play = Play()
        val manager = BillingManager(play, Store(), this)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.refresh(); runCurrent()
        manager.buy(activity)
        play.onPurchases(StoreResult.Success(emptyList())); runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.notice)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
    }

    @Test fun manualRestoreWithNoPurchasesReportsNothingToRestoreAndKeepsProduct() = runTest {
        val play = Play()
        val manager = BillingManager(play, Store(), this)
        // Automatic refresh stays silent when there is nothing to restore.
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertNull(manager.state.value.notice)
        assertNotNull(manager.state.value.product)
        // Manual restore keeps the product on screen while checking (no flicker)
        // and reports that there was nothing to restore.
        play.queryGate = CompletableDeferred()
        manager.refresh(manual = true); runCurrent()
        assertEquals(BillingStatus.CHECKING, manager.state.value.status)
        assertNotNull(manager.state.value.product)
        play.queryGate!!.complete(Unit); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.NOTHING_TO_RESTORE, manager.state.value.notice)
        assertEquals(BillingStatus.NOTHING_TO_RESTORE, manager.state.value.displayStatus)
        assertNotNull(manager.state.value.product)
    }
}
