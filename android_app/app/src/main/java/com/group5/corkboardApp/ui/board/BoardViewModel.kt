package com.group5.corkboardApp.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.data.model.Household
import com.group5.corkboardApp.data.model.Post
import com.group5.corkboardApp.data.repository.AuthRepository
import com.group5.corkboardApp.data.repository.HouseholdRepository
import com.group5.corkboardApp.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BoardViewModel : ViewModel() {

    sealed class HouseholdLoadState {
        data object Loading : HouseholdLoadState()
        data class Success(val households: List<Household>) : HouseholdLoadState()
        data class Error(val message: String) : HouseholdLoadState()
    }

    sealed class CreatePostState {
        data object Idle : CreatePostState()
        data object Loading : CreatePostState()
        data object Success : CreatePostState()
        data class Error(val message: String) : CreatePostState()
    }

    sealed class PostsLoadState {
        data object Loading : PostsLoadState()
        data class Success(val posts: List<Post>) : PostsLoadState()
        data class Error(val message: String) : PostsLoadState()
    }

    sealed class PostActionState {
        data object Idle : PostActionState()
        data object Loading : PostActionState()
        data object Success : PostActionState()
        data class Error(val message: String) : PostActionState()
    }

    private val _householdLoadState = MutableStateFlow<HouseholdLoadState>(HouseholdLoadState.Loading)
    val householdLoadState: StateFlow<HouseholdLoadState> = _householdLoadState

    private val _createPostState = MutableStateFlow<CreatePostState>(CreatePostState.Idle)
    val createPostState: StateFlow<CreatePostState> = _createPostState

    private val _postsLoadState = MutableStateFlow<PostsLoadState>(PostsLoadState.Loading)
    val postsLoadState: StateFlow<PostsLoadState> = _postsLoadState

    private val _postActionState = MutableStateFlow<PostActionState>(PostActionState.Idle)
    val postActionState: StateFlow<PostActionState> = _postActionState

    init {
        loadHouseholds()
    }

    fun loadHouseholds() {
        viewModelScope.launch {
            if (_householdLoadState.value !is HouseholdLoadState.Success) {
                _householdLoadState.value = HouseholdLoadState.Loading
            }
            try {
                val userId = AuthRepository.currentUser()?.id
                    ?: throw Exception("User not authenticated")
                val memberships = HouseholdRepository.getUserMemberships(userId)
                val householdIds = memberships.map { it.household_id }
                val households = if (householdIds.isEmpty()) emptyList()
                else HouseholdRepository.getUserHouseholds(userId)
                _householdLoadState.value = HouseholdLoadState.Success(households)
                loadPosts(householdIds)
            } catch (e: Exception) {
                _householdLoadState.value = HouseholdLoadState.Error(
                    e.localizedMessage ?: "Failed to load households"
                )
            }
        }
    }

    private fun loadPosts(householdIds: List<String>) {
        viewModelScope.launch {
            if (_postsLoadState.value !is PostsLoadState.Success) {
                _postsLoadState.value = PostsLoadState.Loading
            }
            try {
                val posts = PostRepository.getPostsForHouseholds(householdIds)
                _postsLoadState.value = PostsLoadState.Success(posts)
            } catch (e: Exception) {
                _postsLoadState.value = PostsLoadState.Error(
                    e.localizedMessage ?: "Failed to load posts"
                )
            }
        }
    }

    fun createPost(
        title: String,
        body: String,
        type: String,
        householdId: String,
        pointValue: Int? = null,
        dueAt: String? = null
    ) {
        viewModelScope.launch {
            _createPostState.value = CreatePostState.Loading
            try {
                val userId = AuthRepository.currentUser()?.id
                    ?: throw Exception("User not authenticated")
                val memberships = HouseholdRepository.getUserMemberships(userId)
                val memberId = memberships.find { it.household_id == householdId }?.member_id

                PostRepository.createPost(
                    Post(
                        author_id = userId,
                        member_id = memberId,
                        type = type,
                        title = title.ifBlank { null },
                        body = body.ifBlank { null },
                        point_value = pointValue,
                        status = if (type == "chore") "pending" else null,
                        due_at = dueAt?.ifBlank { null },
                        household_id = householdId
                    )
                )
                _createPostState.value = CreatePostState.Success
                // Refresh post list after successful creation
                val currentHouseholds = (_householdLoadState.value as? HouseholdLoadState.Success)
                    ?.households?.map { it.household_id } ?: emptyList()
                loadPosts(currentHouseholds)
            } catch (e: Exception) {
                _createPostState.value = CreatePostState.Error(
                    e.localizedMessage ?: "Failed to create post"
                )
            }
        }
    }

    fun resetCreateState() {
        _createPostState.value = CreatePostState.Idle
    }

    fun updatePost(
        post: Post,
        title: String,
        body: String,
        pointValue: Int? = null,
        dueAt: String? = null
    ) {
        viewModelScope.launch {
            _postActionState.value = PostActionState.Loading
            try {
                PostRepository.updatePost(
                    postId = post.post_id!!,
                    title = title.ifBlank { null },
                    body = body.ifBlank { null },
                    pointValue = pointValue,
                    dueAt = dueAt?.ifBlank { null }
                )
                _postActionState.value = PostActionState.Success
                refreshPosts()
            } catch (e: Exception) {
                _postActionState.value = PostActionState.Error(
                    e.localizedMessage ?: "Failed to update post"
                )
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            _postActionState.value = PostActionState.Loading
            try {
                PostRepository.deletePost(postId)
                _postActionState.value = PostActionState.Success
                refreshPosts()
            } catch (e: Exception) {
                _postActionState.value = PostActionState.Error(
                    e.localizedMessage ?: "Failed to delete post"
                )
            }
        }
    }

    fun completeChore(post: Post) {
        viewModelScope.launch {
            _postActionState.value = PostActionState.Loading
            try {
                val userId = AuthRepository.currentUser()?.id
                    ?: throw Exception("User not authenticated")
                val memberships = HouseholdRepository.getUserMemberships(userId)
                val memberId = memberships.find { it.household_id == post.household_id }?.member_id
                    ?: throw Exception("Not a member of this household")
                PostRepository.completeChore(post.post_id!!, memberId)
                _postActionState.value = PostActionState.Success
                refreshPosts()
            } catch (e: Exception) {
                _postActionState.value = PostActionState.Error(
                    e.localizedMessage ?: "Failed to complete chore"
                )
            }
        }
    }

    fun resetActionState() {
        _postActionState.value = PostActionState.Idle
    }

    private fun refreshPosts() {
        val householdIds = (_householdLoadState.value as? HouseholdLoadState.Success)
            ?.households?.map { it.household_id } ?: emptyList()
        loadPosts(householdIds)
    }
}
