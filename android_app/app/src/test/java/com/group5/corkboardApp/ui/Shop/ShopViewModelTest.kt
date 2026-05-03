package com.group5.corkboardApp.ui.Shop

import android.util.Log
import com.group5.corkboardApp.data.model.Household
import com.group5.corkboardApp.data.model.HouseholdMember
import com.group5.corkboardApp.data.model.ShopItem
import com.group5.corkboardApp.data.repository.AuthRepository
import com.group5.corkboardApp.data.repository.HouseholdRepository
import com.group5.corkboardApp.data.repository.ShopRepository
import com.group5.corkboardApp.util.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ShopViewModel

    private val testMember = HouseholdMember(
        member_id = "member-123",
        user_id = "user-123",
        household_id = "house-123",
        role = "member",
        total_points = 100
    )

    private val redNoteDb = ShopItem(
        id = "real-red-id",
        name = "Red Note",
        price = 50,
        type = "color",
        value = "#FF5252"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(AuthRepository, HouseholdRepository, ShopRepository)
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        every { AuthRepository.currentUser() } returns mockk(relaxed = true) {
            every { id } returns "user-123"
        }
        coEvery { HouseholdRepository.getUserHouseholds(any()) } returns listOf(
            Household(household_id = "house-123", household_name = "Test House")
        )
        coEvery { HouseholdRepository.getUserMemberships(any()) } returns listOf(testMember)
        coEvery { ShopRepository.getShopItems(any()) } returns listOf(redNoteDb)
        coEvery { ShopRepository.getOwnedItems(any(), any()) } returns emptyList()

        SessionManager.selectHousehold(null)
    }

    @After
    fun teardown() {
        SessionManager.selectHousehold(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadShopData merges DB items with universals and marks owned`() = runTest {
        coEvery { ShopRepository.getOwnedItems(any(), any()) } returns listOf("real-red-id")

        SessionManager.selectHousehold("house-123")
        viewModel = ShopViewModel()
        advanceUntilIdle()

        val state = viewModel.shopState.value
        assertTrue("Expected Success, got $state", state is ShopViewModel.ShopState.Success)
        val success = state as ShopViewModel.ShopState.Success
        // DB Red Note dedupes against the universal Red Note by name; the merged entry uses the DB id
        assertTrue(success.items.any { it.id == "real-red-id" && it.name == "Red Note" })
        assertEquals(listOf("real-red-id"), success.ownedItemIds)
        assertEquals(100, success.currentPoints)
    }

    @Test
    fun `buyItem rejects already-owned at UI level without RPC call`() = runTest {
        coEvery { ShopRepository.getOwnedItems(any(), any()) } returns listOf("real-red-id")

        SessionManager.selectHousehold("house-123")
        viewModel = ShopViewModel()
        advanceUntilIdle()

        viewModel.buyItem(redNoteDb)
        advanceUntilIdle()

        val state = viewModel.purchaseState.value
        assertTrue(state is ShopViewModel.PurchaseState.Error)
        assertEquals("Already owned", (state as ShopViewModel.PurchaseState.Error).message)
        coVerify(exactly = 0) { ShopRepository.buyItem(any(), any(), any(), any()) }
    }

    @Test
    fun `buyItem rejects via DB name-fallback when local state is stale`() = runTest {
        // Init runs with nothing owned, so UI-level dedup will pass.
        SessionManager.selectHousehold("house-123")
        viewModel = ShopViewModel()
        advanceUntilIdle()

        // Now the DB has a record of ownership, but the caller passed a different id (same name).
        coEvery { ShopRepository.getOwnedItems(any(), any()) } returns listOf("real-red-id")
        val itemWithDifferentId = redNoteDb.copy(id = "stale-client-id")

        viewModel.buyItem(itemWithDifferentId)
        advanceUntilIdle()

        val state = viewModel.purchaseState.value
        assertTrue(state is ShopViewModel.PurchaseState.Error)
        assertEquals("Already owned", (state as ShopViewModel.PurchaseState.Error).message)
        coVerify(exactly = 0) { ShopRepository.buyItem(any(), any(), any(), any()) }
    }

    @Test
    fun `buyItem fails when member has insufficient points`() = runTest {
        coEvery { HouseholdRepository.getUserMemberships(any()) } returns listOf(
            testMember.copy(total_points = 10)
        )

        SessionManager.selectHousehold("house-123")
        viewModel = ShopViewModel()
        advanceUntilIdle()

        viewModel.buyItem(redNoteDb)
        advanceUntilIdle()

        val state = viewModel.purchaseState.value
        assertTrue(state is ShopViewModel.PurchaseState.Error)
        coVerify(exactly = 0) { ShopRepository.buyItem(any(), any(), any(), any()) }
    }

    @Test
    fun `buyItem succeeds and resolves placeholder id to real DB id`() = runTest {
        coEvery { ShopRepository.buyItem(any(), any(), any(), any()) } returns Unit

        SessionManager.selectHousehold("house-123")
        viewModel = ShopViewModel()
        advanceUntilIdle()

        // Caller passes a stale/placeholder id; the VM should resolve it to the real DB id by name.
        viewModel.buyItem(redNoteDb.copy(id = "stale-client-id"))
        advanceUntilIdle()

        val state = viewModel.purchaseState.value
        assertTrue("Expected Success, got $state", state is ShopViewModel.PurchaseState.Success)
        coVerify(exactly = 1) {
            ShopRepository.buyItem("member-123", "house-123", "real-red-id", 50)
        }
    }

    @Test
    fun `buyItem maps duplicate-key DB error to Already owned`() = runTest {
        coEvery { ShopRepository.buyItem(any(), any(), any(), any()) } throws
            Exception("duplicate key value violates unique constraint")

        SessionManager.selectHousehold("house-123")
        viewModel = ShopViewModel()
        advanceUntilIdle()

        viewModel.buyItem(redNoteDb)
        advanceUntilIdle()

        val state = viewModel.purchaseState.value
        assertTrue(state is ShopViewModel.PurchaseState.Error)
        assertEquals("Already owned", (state as ShopViewModel.PurchaseState.Error).message)
    }
}
