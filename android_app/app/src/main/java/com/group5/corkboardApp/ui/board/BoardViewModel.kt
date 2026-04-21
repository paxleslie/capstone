package com.group5.corkboardApp.ui.board

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.data.model.Household
import com.group5.corkboardApp.data.model.Post
import com.group5.corkboardApp.data.model.ShopItem
import com.group5.corkboardApp.data.repository.AuthRepository
import com.group5.corkboardApp.data.repository.HouseholdRepository
import com.group5.corkboardApp.data.repository.PostRepository
import com.group5.corkboardApp.data.repository.ShopRepository
import com.group5.corkboardApp.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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

    val selectedHouseholdId: StateFlow<String?> = SessionManager.selectedHouseholdId

    private val _ownedColors = MutableStateFlow<List<ShopItem>>(emptyList())
    val ownedColors: StateFlow<List<ShopItem>> = _ownedColors

    private val _defaultPostColor = MutableStateFlow<String?>(null)
    val defaultPostColor: StateFlow<String?> = _defaultPostColor

    private val universalItems = listOf(
        ShopItem(
            id = "d82b1b39-3946-43e1-8fa0-2d5e938dc009",
            name = "Red Note",
            price = 50,
            type = "color",
            value = "#FF5252"
        ),
        ShopItem(
            id = "green_note_placeholder", 
            name = "Green Note",
            price = 50,
            type = "color",
            value = "#AED581"
        )
    )

    fun selectHousehold(householdId: String) {
        SessionManager.selectHousehold(householdId)
    }

    private val _postActionState = MutableStateFlow<PostActionState>(PostActionState.Idle)
    val postActionState: StateFlow<PostActionState> = _postActionState

    init {
        loadHouseholds()
        observeSelectedHousehold()
    }

    private fun observeSelectedHousehold() {
        viewModelScope.launch {
            selectedHouseholdId.collectLatest { hid ->
                if (hid != null) {
                    val userId = AuthRepository.currentUser()?.id
                    if (userId != null) {
                        loadOwnedColors(userId, hid)
                    }
                }
                // When household changes, refresh posts
                val memberships = AuthRepository.currentUser()?.id?.let { HouseholdRepository.getUserMemberships(it) }
                val householdIds = memberships?.map { m -> m.household_id } ?: emptyList()
                loadPosts(householdIds)
            }
        }
    }

    private fun loadOwnedColors(userId: String, householdId: String) {
        viewModelScope.launch {
            try {
                val memberships = HouseholdRepository.getUserMemberships(userId)
                val member = memberships.find { it.household_id == householdId }
                if (member?.member_id != null) {
                    // Fetch using the same two-step approach as ShopViewModel to avoid join permission issues
                    val ownedRewardIds = ShopRepository.getOwnedItems(member.member_id, householdId)
                    val dbItems = ShopRepository.getShopItems(householdId)
                    
                    val allPossibleItems = dbItems + universalItems
                    val ownedNames = allPossibleItems.filter { it.id in ownedRewardIds }.map { it.name }.toSet()

                    _ownedColors.value = allPossibleItems
                        .filter { it.type == "color" }
                        .filter { item ->
                            item.id in ownedRewardIds || item.name in ownedNames
                        }
                        .distinctBy { it.name }
                        .map { item ->
                            // Ensure the 'value' column (hex code) is properly used
                            if (item.value.startsWith("#") || item.value.length < 3) item 
                            else item.copy(value = "#${item.value}")
                        }
                    
                    Log.d("BoardViewModel", "Loaded ${_ownedColors.value.size} owned colors using two-step fetch")
                }
            } catch (e: Exception) {
                Log.e("BoardViewModel", "Error loading owned colors", e)
                _ownedColors.value = emptyList()
            }
        }
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
                
                // Only auto-select if nothing is currently selected
                if (SessionManager.selectedHouseholdId.value == null && households.isNotEmpty()) {
                    selectHousehold(households.first().household_id)
                }
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
        dueAt: String? = null,
        color: String? = null
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
                        household_id = householdId,
                        color = color ?: _defaultPostColor.value
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

    fun setDefaultPostColor(color: String?) {
        _defaultPostColor.value = color
    }

    fun resetCreateState() {
        _createPostState.value = CreatePostState.Idle
    }

    fun updatePost(
        post: Post,
        title: String,
        body: String,
        pointValue: Int? = null,
        dueAt: String? = null,
        color: String? = null
    ) {
        viewModelScope.launch {
            _postActionState.value = PostActionState.Loading
            try {
                val postId = post.post_id ?: return@launch
                PostRepository.updatePost(
                    postId = postId,
                    title = title.ifBlank { null },
                    body = body.ifBlank { null },
                    pointValue = pointValue,
                    dueAt = dueAt,
                    color = color
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
                val postId = post.post_id ?: return@launch
                PostRepository.completeChore(postId, memberId)
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
