package com.group5.corkboardApp.ui.board

import com.group5.corkboardApp.data.model.HouseholdMember
import com.group5.corkboardApp.data.model.Post
import com.group5.corkboardApp.data.repository.AuthRepository
import com.group5.corkboardApp.data.repository.HouseholdRepository
import com.group5.corkboardApp.data.repository.PostRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
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
class BoardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: BoardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(AuthRepository, HouseholdRepository, PostRepository)

        // Stub calls made during ViewModel init -> loadHouseholds()
        every { AuthRepository.currentUser() } returns mockk(relaxed = true) {
            every { id } returns "user-123"
        }
        coEvery { HouseholdRepository.getUserMemberships(any()) } returns emptyList()
        coEvery { HouseholdRepository.getUserHouseholds(any()) } returns emptyList()
        coEvery { PostRepository.getPostsForHouseholds(any()) } returns emptyList()

        viewModel = BoardViewModel()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `createPost transitions to Success`() = runTest {
        coEvery { HouseholdRepository.getUserMemberships(any()) } returns listOf(
            HouseholdMember(member_id = "member-123", user_id = "user-123", household_id = "house-123", role = "member")
        )
        coEvery { PostRepository.createPost(any()) } returns Unit

        viewModel.createPost("Chores", "Take out bins", "note", "house-123")
        advanceUntilIdle()

        assertEquals(BoardViewModel.CreatePostState.Success, viewModel.createPostState.value)
    }

    @Test
    fun `createPost transitions to Error on failure`() = runTest {
        coEvery { PostRepository.createPost(any()) } throws Exception("Insert failed")

        viewModel.createPost("Chores", "Take out bins", "note", "house-123")
        advanceUntilIdle()

        assertTrue(viewModel.createPostState.value is BoardViewModel.CreatePostState.Error)
    }

    @Test
    fun `updatePost transitions to Success`() = runTest {
        coEvery { PostRepository.updatePost(any(), any(), any(), any(), any()) } returns Unit

        val post = Post(post_id = "post-123", type = "note", household_id = "house-123")
        viewModel.updatePost(post, "Updated Title", "Updated body")
        advanceUntilIdle()

        assertEquals(BoardViewModel.PostActionState.Success, viewModel.postActionState.value)
    }

    @Test
    fun `deletePost transitions to Success`() = runTest {
        coEvery { PostRepository.deletePost(any()) } returns Unit

        viewModel.deletePost("post-123")
        advanceUntilIdle()

        assertEquals(BoardViewModel.PostActionState.Success, viewModel.postActionState.value)
    }

    @Test
    fun `deletePost transitions to Error on failure`() = runTest {
        coEvery { PostRepository.deletePost(any()) } throws Exception("Network error")

        viewModel.deletePost("post-123")
        advanceUntilIdle()

        assertTrue(viewModel.postActionState.value is BoardViewModel.PostActionState.Error)
    }

    @Test
    fun `completeChore transitions to Success`() = runTest {
        coEvery { HouseholdRepository.getUserMemberships(any()) } returns listOf(
            HouseholdMember(member_id = "member-123", user_id = "user-123", household_id = "house-123", role = "member")
        )
        coEvery { PostRepository.completeChore(any(), any()) } returns Unit

        val post = Post(post_id = "post-123", type = "chore", household_id = "house-123")
        viewModel.completeChore(post)
        advanceUntilIdle()

        assertEquals(BoardViewModel.PostActionState.Success, viewModel.postActionState.value)
    }

    @Test
    fun `completeChore transitions to Error when not a member`() = runTest {
        coEvery { HouseholdRepository.getUserMemberships(any()) } returns emptyList()

        val post = Post(post_id = "post-123", type = "chore", household_id = "house-123")
        viewModel.completeChore(post)
        advanceUntilIdle()

        assertTrue(viewModel.postActionState.value is BoardViewModel.PostActionState.Error)
    }
}
