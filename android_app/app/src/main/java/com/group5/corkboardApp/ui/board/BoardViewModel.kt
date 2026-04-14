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

    private val _householdLoadState = MutableStateFlow<HouseholdLoadState>(HouseholdLoadState.Loading)
    val householdLoadState: StateFlow<HouseholdLoadState> = _householdLoadState

    private val _createPostState = MutableStateFlow<CreatePostState>(CreatePostState.Idle)
    val createPostState: StateFlow<CreatePostState> = _createPostState

    private val _postsLoadState = MutableStateFlow<PostsLoadState>(PostsLoadState.Loading)
    val postsLoadState: StateFlow<PostsLoadState> = _postsLoadState

    init {
        loadHouseholds()
    }

    fun loadHouseholds() {
        viewModelScope.launch {
            _householdLoadState.value = HouseholdLoadState.Loading
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
            _postsLoadState.value = PostsLoadState.Loading
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
}
