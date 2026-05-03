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
import com.group5.corkboardApp.util.BoardPrefs
import com.group5.corkboardApp.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BoardViewModel : ViewModel() {

    private var loadHouseholdsJob: Job? = null
    private var loadOwnedAssetsJob: Job? = null

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

    private val _ownedStickers = MutableStateFlow<List<ShopItem>>(emptyList())
    val ownedStickers: StateFlow<List<ShopItem>> = _ownedStickers

    private val _defaultPostColor = MutableStateFlow<String?>(null)
    val defaultPostColor: StateFlow<String?> = _defaultPostColor

    private val _defaultStickerUrl = MutableStateFlow<String?>(null)
    val defaultStickerUrl: StateFlow<String?> = _defaultStickerUrl

    fun selectHousehold(householdId: String) {
        SessionManager.selectHousehold(householdId)
    }

    private val _postActionState = MutableStateFlow<PostActionState>(PostActionState.Idle)
    val postActionState: StateFlow<PostActionState> = _postActionState

    init {
        // Initial load
        loadHouseholds()
        observeSelectedHousehold()
    }

    private fun observeSelectedHousehold() {
        viewModelScope.launch {
            selectedHouseholdId.collectLatest { hid ->
                if (hid != null) {
                    val userId = AuthRepository.currentUser()?.id
                    if (userId != null) {
                        refreshDataForHousehold(userId, hid)
                        _defaultPostColor.value = BoardPrefs.getDefaultColor(hid)
                        _defaultStickerUrl.value = BoardPrefs.getDefaultSticker(hid)
                    }
                }
                // When household changes, refresh posts for all memberships
                val userId = AuthRepository.currentUser()?.id
                if (userId != null) {
                    val memberships = HouseholdRepository.getUserMemberships(userId)
                    val householdIds = memberships.map { it.household_id }
                    loadPosts(householdIds)
                }
            }
        }
    }

    private fun refreshDataForHousehold(userId: String, householdId: String) {
        loadOwnedAssets(userId, householdId)
    }

    private fun loadOwnedAssets(userId: String, householdId: String) {
        loadOwnedAssetsJob?.cancel()
        loadOwnedAssetsJob = viewModelScope.launch {
            try {
                val memberships = HouseholdRepository.getUserMemberships(userId)
                val member = memberships.find { it.household_id == householdId }
                if (member?.member_id != null) {
                    val ownedRewardIds = ShopRepository.getOwnedItems(member.member_id, householdId)
                    val dbItems = ShopRepository.getShopItems(householdId)
                    
                    val ownedNames = dbItems.filter { it.id in ownedRewardIds }.map { it.name }.toSet()

                    val ownedItems = dbItems
                        .filter { item ->
                            item.id in ownedRewardIds || item.name in ownedNames
                        }

                    val newOwnedColors = ownedItems
                        .filter { it.type == "color" }
                        .map { item ->
                            if (item.value.startsWith("#") || item.value.length < 3) item 
                            else item.copy(value = "#${item.value}")
                        }
                    
                    val newOwnedStickers = ownedItems
                        .filter { it.type == "sticker" }

                    _ownedColors.value = newOwnedColors
                    _ownedStickers.value = newOwnedStickers
                    Log.d("BoardViewModel", "Updated assets: colors=${newOwnedColors.size}, stickers=${newOwnedStickers.size}")
                }
            } catch (e: Exception) {
                Log.e("BoardViewModel", "Error loading owned assets", e)
                _ownedColors.value = emptyList()
                _ownedStickers.value = emptyList()
            }
        }
    }

    fun loadHouseholds() {
        if (loadHouseholdsJob?.isActive == true) return
        
        loadHouseholdsJob = viewModelScope.launch {
            try {
                val userId = AuthRepository.currentUser()?.id
                    ?: throw Exception("User not authenticated")
                
                // 1. Fetch household details
                val unsortedHouseholds = HouseholdRepository.getUserHouseholds(userId)
                
                // 2. Create correctly ordered list based on created_at timestamp (oldest first)
                val sortedHouseholds = unsortedHouseholds.sortedBy { it.created_at }

                // 3. Atomic update of state to prevent flickering
                val currentState = _householdLoadState.value
                if (currentState !is HouseholdLoadState.Success || currentState.households != sortedHouseholds) {
                    _householdLoadState.value = HouseholdLoadState.Success(sortedHouseholds)
                }
                
                // 4. Sync selection and refresh relevant data
                val currentHid = SessionManager.selectedHouseholdId.value
                if (currentHid == null && sortedHouseholds.isNotEmpty()) {
                    selectHousehold(sortedHouseholds.first().household_id)
                } else if (currentHid != null) {
                    loadOwnedAssets(userId, currentHid)
                }
                
                loadPosts(sortedHouseholds.map { it.household_id })
            } catch (e: Exception) {
                if (_householdLoadState.value !is HouseholdLoadState.Success) {
                    _householdLoadState.value = HouseholdLoadState.Error(
                        e.localizedMessage ?: "Failed to load households"
                    )
                }
            }
        }
    }

    private fun loadPosts(householdIds: List<String>) {
        viewModelScope.launch {
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
                        color = color ?: _defaultPostColor.value,
                        sticker = _defaultStickerUrl.value
                    )
                )
                _createPostState.value = CreatePostState.Success
                refreshPosts()
            } catch (e: Exception) {
                _createPostState.value = CreatePostState.Error(
                    e.localizedMessage ?: "Failed to create post"
                )
            }
        }
    }

    fun setDefaultPostColor(color: String?) {
        val hid = selectedHouseholdId.value ?: return
        _defaultPostColor.value = color
        BoardPrefs.setDefaultColor(hid, color)
    }

    fun setDefaultStickerUrl(url: String?) {
        val hid = selectedHouseholdId.value ?: return
        _defaultStickerUrl.value = url
        BoardPrefs.setDefaultSticker(hid, url)
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
