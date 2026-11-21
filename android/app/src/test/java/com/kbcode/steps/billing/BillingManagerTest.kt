package com.kbcode.steps.billing

import android.app.Activity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillingManagerTest {
    private class MemoryStore(override var owned: Boolean = false) : LogoOwnershipStore
    private class FakePlay : BillingGateway {
        override var onPurchases: (StoreResult<List<StorePurchase>>) -> Unit = {}
        var connection: StoreResult<Unit> = StoreResult.Success(Unit)
        var inventory: StoreResult<List<StorePurchase>> = StoreResult.Success(emptyList())
        var listing: StoreResult<LogoProduct> = StoreResult.Success(LogoProduct("Store product", "store price"))
        var ack: StoreResult<Unit> = StoreResult.Success(Unit)
        val ackCalls = mutableMapOf<String, Int>()
        var ackResults = mutableMapOf<String, StoreResult<Unit>>()
        var ackGate: CompletableDeferred<Unit>? = null
        var productGate: CompletableDeferred<Unit>? = null
        var acknowledgements = 0
        var queries = 0
        override suspend fun connect() = connection
        override suspend fun product(): StoreResult<LogoProduct> { productGate?.await(); return listing }
        override suspend fun purchases(): StoreResult<List<StorePurchase>> { queries++; return inventory }
        override suspend fun acknowledge(token: String): StoreResult<Unit> {
            acknowledgements++
            ackCalls[token] = (ackCalls[token] ?: 0) + 1
            ackGate?.await()
            return ackResults[token] ?: ack
        }
        override fun launch(activity: Activity): StoreResult<Unit> = StoreResult.Success(Unit)
        override fun close() {}
    }
    private fun purchase(ack: Boolean = false, pending: Boolean = false, id: String = REMOVE_LOGO, token: String = "synthetic-token") =
        StorePurchase(listOf(id), token, !pending, pending, ack)

    @Test fun `restoration hides logo on a fresh install without acknowledging twice`() = runTest {
        val play = FakePlay().apply { inventory = StoreResult.Success(listOf(purchase(ack = true))) }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        assertTrue(manager.state.value.showLogo)
        manager.refresh(); runCurrent()
        assertTrue(store.owned)
        assertFalse(manager.state.value.showLogo)
        assertEquals(0, play.acknowledgements)
    }

    @Test fun `purchase grants only after acknowledgement succeeds`() = runTest {
        val play = FakePlay().apply { ackGate = CompletableDeferred() }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        play.onPurchases(StoreResult.Success(listOf(purchase())))
        runCurrent()
        assertEquals(BillingStatus.ACKNOWLEDGING, manager.state.value.status)
        assertFalse(store.owned)
        assertTrue(manager.state.value.showLogo)
        play.ackGate!!.complete(Unit); runCurrent()
        assertTrue(store.owned)
        assertFalse(manager.state.value.showLogo)
    }

    @Test fun `failed acknowledgement is retried through restoration`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase()))
            ack = StoreResult.Failure(StoreError.OFFLINE)
        }
        val manager = BillingManager(play, MemoryStore(), this)
        manager.refresh(); runCurrent()
        assertTrue(manager.state.value.showLogo)
        assertEquals(BillingStatus.OFFLINE, manager.state.value.status)
        play.ack = StoreResult.Success(Unit)
        manager.refresh(); runCurrent()
        assertFalse(manager.state.value.showLogo)
        assertEquals(2, play.acknowledgements)
    }

    @Test fun `pending is never acknowledged or granted and later payment is restored`() = runTest {
        val play = FakePlay().apply { inventory = StoreResult.Success(listOf(purchase(pending = true))) }
        val manager = BillingManager(play, MemoryStore(), this)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.PENDING, manager.state.value.status)
        assertTrue(manager.state.value.showLogo)
        assertFalse(manager.state.value.canBuy)
        assertEquals(0, play.acknowledgements)
        play.inventory = StoreResult.Success(listOf(purchase()))
        manager.refresh(); runCurrent()
        assertFalse(manager.state.value.showLogo)
    }

    @Test fun `offline launch retains acknowledged cache without hiding logo for new users`() = runTest {
        for (cached in listOf(false, true)) {
            val play = FakePlay().apply { connection = StoreResult.Failure(StoreError.OFFLINE) }
            val store = MemoryStore(cached)
            val manager = BillingManager(play, store, this)
            assertEquals(!cached, manager.state.value.showLogo)
            manager.refresh(); runCurrent()
            assertEquals(cached, store.owned)
            assertEquals(!cached, manager.state.value.showLogo)
            assertEquals(BillingStatus.OFFLINE, manager.state.value.status)
        }
    }

    @Test fun `failed query never revokes cache but successful empty query does`() = runTest {
        val play = FakePlay().apply { inventory = StoreResult.Failure(StoreError.ERROR) }
        val store = MemoryStore(true)
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertTrue(store.owned)
        play.inventory = StoreResult.Success(emptyList())
        manager.refresh(); runCurrent()
        assertFalse(store.owned)
        assertTrue(manager.state.value.showLogo)
    }

    @Test fun `cancellation errors and unrelated events do not grant or revoke`() = runTest {
        for (cached in listOf(false, true)) {
            val play = FakePlay()
            val store = MemoryStore(cached)
            val manager = BillingManager(play, store, this)
            for ((error, status) in listOf(StoreError.CANCELED to BillingStatus.CANCELED,
                StoreError.ERROR to BillingStatus.ERROR, StoreError.OFFLINE to BillingStatus.OFFLINE)) {
                play.onPurchases(StoreResult.Failure(error)); runCurrent()
                assertEquals(status, manager.state.value.status)
                assertEquals(cached, store.owned)
            }
            play.onPurchases(StoreResult.Success(listOf(purchase(id = "other_product")))); runCurrent()
            assertEquals(cached, store.owned)
            assertEquals(0, play.acknowledgements)
        }
    }

    @Test fun `already owned queries Play instead of blindly granting`() = runTest {
        val play = FakePlay()
        val manager = BillingManager(play, MemoryStore(), this)
        play.onPurchases(StoreResult.Failure(StoreError.ALREADY_OWNED)); runCurrent()
        assertTrue(manager.state.value.showLogo)
        play.inventory = StoreResult.Success(listOf(purchase(ack = true)))
        play.onPurchases(StoreResult.Failure(StoreError.ALREADY_OWNED)); runCurrent()
        assertFalse(manager.state.value.showLogo)
    }

    @Test fun `missing product cannot block restoration or allow a purchase`() = runTest {
        val play = FakePlay().apply {
            listing = StoreResult.Failure(StoreError.UNAVAILABLE)
            inventory = StoreResult.Success(listOf(purchase(ack = true)))
        }
        val manager = BillingManager(play, MemoryStore(), this)
        manager.refresh(); runCurrent()
        assertFalse(manager.state.value.showLogo)
        assertFalse(manager.state.value.canBuy)
    }

    @Test fun `price and name come from Play and repeated refresh is coalesced`() = runTest {
        val play = FakePlay()
        val manager = BillingManager(play, MemoryStore(), this)
        manager.refresh(); manager.refresh(); runCurrent()
        assertEquals(1, play.queries)
        assertEquals("store price", manager.state.value.product?.price)
        assertEquals("Store product", manager.state.value.product?.name)
        assertTrue(manager.state.value.canBuy)
    }

    @Test fun `unrelated or unspecified inventory does not grant entitlement`() = runTest {
        val play = FakePlay().apply { inventory = StoreResult.Success(listOf(
            purchase(id = "other_product"), StorePurchase(listOf(REMOVE_LOGO), "synthetic", false, false, false)
        )) }
        val manager = BillingManager(play, MemoryStore(), this)
        manager.refresh(); runCurrent()
        assertTrue(manager.state.value.showLogo)
        assertEquals(0, play.acknowledgements)
    }

    @Test fun `delayed restoration and purchase event end with the acknowledged entitlement`() = runTest {
        val play = FakePlay().apply { ackGate = CompletableDeferred(); inventory = StoreResult.Success(listOf(purchase())) }
        val manager = BillingManager(play, MemoryStore(), this)
        manager.refresh(); runCurrent()
        play.onPurchases(StoreResult.Success(listOf(purchase(ack = true))))
        play.ackGate!!.complete(Unit); runCurrent()
        assertFalse(manager.state.value.showLogo)
    }
    @Test fun `refresh cannot purchase with stale product while new details are loading`() = runTest {
        val play = FakePlay()
        val manager = BillingManager(play, MemoryStore(), this)
        manager.refresh(); runCurrent()
        assertTrue(manager.state.value.canBuy)
        play.productGate = CompletableDeferred()
        manager.refresh(); runCurrent()
        assertFalse(manager.state.value.canBuy)
        // The product stays on screen while loading (clearing it made the
        // purchase section collapse and reappear, which read as flicker);
        // canBuy=false is what blocks the purchase during the refresh.
        assertNotNull(manager.state.value.product)
        play.productGate!!.complete(Unit); runCurrent()
        assertTrue(manager.state.value.canBuy)
    }

    @Test fun `two unacknowledged purchases are both acknowledged`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(token = "A"), purchase(token = "B")))
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertEquals(1, play.ackCalls["A"])
        assertEquals(1, play.ackCalls["B"])
        assertTrue(store.owned)
    }

    // 歩数ログ側で追加したテスト（PR #23 のレビュー指摘への対応）。
    // 先のトークンの承認が失敗しても、後のトークンの承認を試すこと。
    // 未承認分は個別に承認する、という課金テンプレートの所有ルールに合わせる。
    @Test fun `first acknowledgement failure does not skip the second purchase`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(token = "A"), purchase(token = "B")))
            ackResults["A"] = StoreResult.Failure(StoreError.ERROR)
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertEquals(1, play.ackCalls["A"])
        assertEquals(1, play.ackCalls["B"])
        // B の承認が成功したので所有は確定し、A の失敗は表示に残る
        assertTrue(store.owned)
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)
    }

    @Test fun `two acknowledgement failures are both attempted and nothing is owned`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(token = "A"), purchase(token = "B")))
            ackResults["A"] = StoreResult.Failure(StoreError.ERROR)
            ackResults["B"] = StoreResult.Failure(StoreError.ERROR)
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertEquals(1, play.ackCalls["A"])
        assertEquals(1, play.ackCalls["B"])
        assertFalse(store.owned)
        assertEquals(BillingStatus.ERROR, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)
    }

    @Test fun `acknowledgement success then failure still grants from the success`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(token = "A"), purchase(token = "B")))
            ackResults["B"] = StoreResult.Failure(StoreError.ERROR)
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertEquals(1, play.ackCalls["A"])
        assertEquals(1, play.ackCalls["B"])
        // A の承認が成功しているので所有は確定し、B の失敗は表示に残る
        assertTrue(store.owned)
        assertEquals(BillingStatus.ERROR, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)
    }

    @Test fun `mixed acknowledged and unacknowledged acknowledges only the unacknowledged token`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(ack = true, token = "A"), purchase(token = "B")))
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertFalse(play.ackCalls.containsKey("A"))
        assertEquals(1, play.ackCalls["B"])
        assertEquals(1, play.acknowledgements)
        assertTrue(store.owned)
    }

    @Test fun `mixed acknowledgement failure keeps entitlement from the acknowledged purchase`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(ack = true, token = "A"), purchase(token = "B")))
            ackResults["B"] = StoreResult.Failure(StoreError.ERROR)
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
        assertTrue(store.owned)
    }

    @Test fun `failed token is retried alone on the next query`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(token = "A"), purchase(token = "B")))
            ackResults["B"] = StoreResult.Failure(StoreError.OFFLINE)
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertTrue(store.owned)
        assertEquals(BillingStatus.OFFLINE, manager.state.value.status)
        assertEquals(1, play.ackCalls["A"])
        assertEquals(1, play.ackCalls["B"])
        play.inventory = StoreResult.Success(listOf(purchase(ack = true, token = "A"), purchase(token = "B")))
        play.ackResults["B"] = StoreResult.Success(Unit)
        manager.refresh(); runCurrent()
        assertEquals(1, play.ackCalls["A"])
        assertEquals(2, play.ackCalls["B"])
        assertTrue(store.owned)
        assertEquals(BillingStatus.READY, manager.state.value.status)
    }

    @Test fun `acknowledgement failure without entitlement keeps cached ownership`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(token = "A")))
            ack = StoreResult.Failure(StoreError.ERROR)
        }
        val store = MemoryStore(true)
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertTrue(store.owned)
        assertEquals(BillingStatus.ERROR, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
    }

    @Test fun `pending purchase alongside a completed one is never acknowledged`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(token = "A"), purchase(pending = true, token = "B")))
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertFalse(play.ackCalls.containsKey("B"))
        assertEquals(1, play.ackCalls["A"])
        assertTrue(store.owned)
    }

    @Test fun `duplicate token in one result is acknowledged once`() = runTest {
        val purchased = purchase(token = "A")
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchased, purchased))
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertEquals(1, play.ackCalls["A"])
        assertTrue(store.owned)
    }

    @Test fun `stale unacknowledged callback after restoration does not break ownership`() = runTest {
        val purchased = purchase(token = "A")
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchased))
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertTrue(store.owned)
        assertEquals(1, play.ackCalls["A"])
        play.inventory = StoreResult.Success(listOf(purchased.copy(acknowledged = true)))
        play.ack = StoreResult.Failure(StoreError.ERROR)
        play.onPurchases(StoreResult.Success(listOf(purchased))); runCurrent()
        assertTrue(store.owned)
        assertEquals(1, play.ackCalls["A"])
        assertEquals(BillingStatus.READY, manager.state.value.status)
    }

    @Test fun `acknowledgement failure from a purchase callback is retried by the next callback`() = runTest {
        val play = FakePlay().apply { ack = StoreResult.Failure(StoreError.ERROR) }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)
        val result = StoreResult.Success(listOf(purchase(token = "A")))
        play.onPurchases(result); runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.status)
        assertFalse(store.owned)
        assertEquals(1, play.ackCalls["A"])
        play.ack = StoreResult.Success(Unit)
        play.onPurchases(result); runCurrent()
        assertTrue(store.owned)
        assertEquals(2, play.ackCalls["A"])
        assertEquals(BillingStatus.READY, manager.state.value.status)
    }

    @Test fun `authoritative pending only result revokes cache and shows pending`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(pending = true)))
        }
        val store = MemoryStore(true)
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertFalse(store.owned)
        assertEquals(BillingStatus.PENDING, manager.state.value.status)
        assertEquals(BillingStatus.PENDING, manager.state.value.displayStatus)
    }

    @Test fun `owned state hides status messages`() = runTest {
        val play = FakePlay().apply { connection = StoreResult.Failure(StoreError.OFFLINE) }
        val store = MemoryStore(true)
        val manager = BillingManager(play, store, this)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.OFFLINE, manager.state.value.status)
        assertNull(manager.state.value.displayStatus)
    }

    @Test fun `grant clears a retained cancel notice`() = runTest {
        val play = FakePlay()
        val manager = BillingManager(play, MemoryStore(), this)
        play.onPurchases(StoreResult.Failure(StoreError.CANCELED)); runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)
        assertEquals(BillingStatus.CANCELED, manager.state.value.displayStatus)
        play.onPurchases(StoreResult.Success(listOf(purchase()))); runCurrent()
        assertTrue(manager.state.value.owned)
        assertNull(manager.state.value.notice)
        assertNull(manager.state.value.displayStatus)
    }

    @Test fun `pending detection supersedes a retained notice`() = runTest {
        val play = FakePlay()
        val manager = BillingManager(play, MemoryStore(), this)
        play.onPurchases(StoreResult.Failure(StoreError.CANCELED)); runCurrent()
        play.onPurchases(StoreResult.Success(listOf(purchase(pending = true)))); runCurrent()
        assertEquals(BillingStatus.PENDING, manager.state.value.status)
        assertNull(manager.state.value.notice)
        assertEquals(BillingStatus.PENDING, manager.state.value.displayStatus)
        assertFalse(manager.state.value.owned)
    }

    @Test fun `acknowledgement failure from a purchase callback is retained as a notice across refresh`() = runTest {
        val play = FakePlay().apply { ack = StoreResult.Failure(StoreError.ERROR) }
        val manager = BillingManager(play, MemoryStore(), this)
        play.onPurchases(StoreResult.Success(listOf(purchase()))); runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.notice)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
        play.inventory = StoreResult.Success(emptyList())
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.notice)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
    }

    @Test fun `refresh failure is not retained as a notice`() = runTest {
        val play = FakePlay().apply { connection = StoreResult.Failure(StoreError.OFFLINE) }
        val manager = BillingManager(play, MemoryStore(), this)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.OFFLINE, manager.state.value.status)
        assertNull(manager.state.value.notice)
        play.connection = StoreResult.Success(Unit)
        play.inventory = StoreResult.Success(emptyList())
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertNull(manager.state.value.displayStatus)
    }

    @Test fun `manual refresh with nothing to restore answers the tap but automatic refresh keeps a retained notice`() = runTest {
        val play = FakePlay()
        val manager = BillingManager(play, MemoryStore(), this)
        play.onPurchases(StoreResult.Failure(StoreError.CANCELED)); runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)
        play.inventory = StoreResult.Success(emptyList())
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)
        // A manual restore that finds nothing replaces the retained notice with
        // the restore result, so the user sees their tap did something.
        manager.refresh(manual = true); runCurrent()
        assertEquals(BillingStatus.NOTHING_TO_RESTORE, manager.state.value.notice)
        assertEquals(BillingStatus.NOTHING_TO_RESTORE, manager.state.value.displayStatus)
    }

    @Test fun `unresolved ack failure survives a callback for another token and clears on authoritative resolution`() = runTest {
        val play = FakePlay().apply {
            ack = StoreResult.Failure(StoreError.ERROR)
        }
        val manager = BillingManager(play, MemoryStore(), this)

        play.onPurchases(StoreResult.Success(listOf(purchase(token = "A"))))
        runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)

        play.ack = StoreResult.Success(Unit)
        play.onPurchases(StoreResult.Success(listOf(purchase(ack = true, token = "B"))))
        runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
        assertFalse(play.ackCalls.containsKey("B"))
        assertTrue(manager.state.value.owned)

        play.inventory = StoreResult.Success(listOf(
            purchase(ack = true, token = "A"),
            purchase(ack = true, token = "B")
        ))
        manager.refresh(); runCurrent()
        assertNull(manager.state.value.ackFailure)
        assertNull(manager.state.value.displayStatus)
    }

    @Test fun `unresolved ack failure clears when an authoritative query no longer lists the token`() = runTest {
        val play = FakePlay().apply {
            ack = StoreResult.Failure(StoreError.ERROR)
        }
        val manager = BillingManager(play, MemoryStore(), this)

        play.onPurchases(StoreResult.Success(listOf(purchase(token = "A"))))
        runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)

        play.inventory = StoreResult.Success(emptyList())
        manager.refresh(); runCurrent()
        assertNull(manager.state.value.ackFailure)
        assertFalse(manager.state.value.owned)
        assertEquals(BillingStatus.READY, manager.state.value.status)
        // Automatic refresh resolves ackFailure but retains the purchase-flow notice.
        assertEquals(BillingStatus.ERROR, manager.state.value.notice)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
    }

    @Test fun `unresolved ack failure survives an authoritative query that still lists the token unacknowledged`() = runTest {
        val play = FakePlay().apply {
            ack = StoreResult.Failure(StoreError.ERROR)
        }
        val manager = BillingManager(play, MemoryStore(), this)

        play.onPurchases(StoreResult.Success(listOf(purchase(token = "A"))))
        runCurrent()
        assertEquals(1, play.ackCalls["A"])
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)

        play.inventory = StoreResult.Success(listOf(purchase(token = "A")))
        manager.refresh(); runCurrent()
        assertEquals(2, play.ackCalls["A"])
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
    }

    @Test fun `ack failure display survives a failed refresh and clears after a successful retry`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(token = "A")))
            ack = StoreResult.Failure(StoreError.ERROR)
        }
        val store = MemoryStore(true)
        val manager = BillingManager(play, store, this)

        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)

        play.connection = StoreResult.Failure(StoreError.OFFLINE)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.OFFLINE, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)

        play.connection = StoreResult.Success(Unit)
        play.ack = StoreResult.Success(Unit)
        manager.refresh(); runCurrent()
        assertNull(manager.state.value.displayStatus)
        assertNull(manager.state.value.ackFailure)
        assertTrue(manager.state.value.owned)
        assertTrue(store.owned)
    }

    @Test fun `ack failure from a refresh does not set a notice and clears with an empty result`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(token = "A")))
            ack = StoreResult.Failure(StoreError.ERROR)
        }
        val manager = BillingManager(play, MemoryStore(), this)

        manager.refresh(); runCurrent()
        assertNull(manager.state.value.notice)
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)

        play.inventory = StoreResult.Success(emptyList())
        manager.refresh(); runCurrent()
        assertNull(manager.state.value.displayStatus)
        assertNull(manager.state.value.ackFailure)
        assertNull(manager.state.value.notice)
    }

    @Test fun `current failure takes precedence over a retained notice`() = runTest {
        val play = FakePlay()
        val manager = BillingManager(play, MemoryStore(), this)

        play.onPurchases(StoreResult.Failure(StoreError.CANCELED))
        runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)
        assertEquals(BillingStatus.CANCELED, manager.state.value.displayStatus)

        play.connection = StoreResult.Failure(StoreError.OFFLINE)
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)
        assertEquals(BillingStatus.OFFLINE, manager.state.value.displayStatus)

        play.connection = StoreResult.Success(Unit)
        play.inventory = StoreResult.Success(emptyList())
        manager.refresh(); runCurrent()
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)
        assertEquals(BillingStatus.CANCELED, manager.state.value.displayStatus)
    }

    @Test fun `manual refresh failure keeps a retained notice`() = runTest {
        val play = FakePlay()
        val manager = BillingManager(play, MemoryStore(), this)

        play.onPurchases(StoreResult.Failure(StoreError.CANCELED))
        runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)

        play.inventory = StoreResult.Failure(StoreError.ERROR)
        manager.refresh(manual = true); runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)
        assertEquals(BillingStatus.ERROR, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.displayStatus)
    }

    @Test fun `manual refresh requested during an automatic refresh runs after it and reports nothing to restore`() = runTest {
        val play = FakePlay()
        val manager = BillingManager(play, MemoryStore(), this)

        manager.refresh(); runCurrent()
        assertEquals(1, play.queries)
        play.onPurchases(StoreResult.Failure(StoreError.CANCELED))
        runCurrent()
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)

        val gate = CompletableDeferred<Unit>()
        play.productGate = gate
        manager.refresh(); runCurrent()
        assertEquals(2, play.queries)
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)

        manager.refresh(manual = true)
        runCurrent()
        assertEquals(2, play.queries)
        assertEquals(BillingStatus.CANCELED, manager.state.value.notice)

        gate.complete(Unit)
        runCurrent()
        assertEquals(3, play.queries)
        assertEquals(BillingStatus.READY, manager.state.value.status)
        assertEquals(BillingStatus.NOTHING_TO_RESTORE, manager.state.value.notice)
        assertEquals(BillingStatus.NOTHING_TO_RESTORE, manager.state.value.displayStatus)
    }

    @Test fun `acknowledged token record is washed out by an authoritative query`() = runTest {
        val play = FakePlay().apply {
            inventory = StoreResult.Success(listOf(purchase(token = "A")))
        }
        val store = MemoryStore()
        val manager = BillingManager(play, store, this)

        manager.refresh(); runCurrent()
        assertTrue(manager.state.value.owned)
        assertEquals(1, play.ackCalls["A"])

        play.inventory = StoreResult.Success(emptyList())
        manager.refresh(); runCurrent()
        assertFalse(manager.state.value.owned)
        assertFalse(store.owned)

        play.ack = StoreResult.Failure(StoreError.ERROR)
        play.onPurchases(StoreResult.Success(listOf(purchase(token = "A"))))
        runCurrent()
        assertEquals(2, play.ackCalls["A"])
        assertFalse(manager.state.value.owned)
        assertFalse(store.owned)
        assertEquals(BillingStatus.ERROR, manager.state.value.status)
        assertEquals(BillingStatus.ERROR, manager.state.value.ackFailure)
    }
}
