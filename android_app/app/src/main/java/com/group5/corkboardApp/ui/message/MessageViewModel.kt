package com.group5.corkboardApp.ui.message

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.data.repository.MessageRepository
import com.group5.corkboardApp.util.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Represents one row from the messages table
@Serializable
data class Message(
    val message_id: String,
    val author_id: String,
    val from_member_id: String,
    val content: String,
    val sent_at: String,
    val edited_at: String? = null,

    // Joined household_members row so we can display nickname/role
    @SerialName("household_members")
    val senderMember: SenderMember? = null
)

// Represents joined sender info from household_members
@Serializable
data class SenderMember(
    val member_id: String,
    val nickname: String? = null,
    val user_id: String? = null,
    val role: String? = null,

    @SerialName("user")
    val user: SenderUser? = null
)

@Serializable
data class SenderUser(
    val display_name: String? = null,
    val name: String? = null
)

// Used to get member IDs for a household
@Serializable
data class HouseholdMessageMember(
    val member_id: String
)

class MessageViewModel : ViewModel() {

    // Logcat tag
    private val TAG = "MessageVM"

    // Holds messages for the UI
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    // Holds send/fetch error messages for the UI
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // True while a fetch is in flight; UI shows a spinner instead of the (briefly empty) list
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Fetch all messages for a household
    fun fetchMessages(householdId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // remove stale data before trying to pull fresh
                _messages.value = emptyList()
                _errorMessage.value = null
                _messages.value = MessageRepository.getMessagesForHousehold(householdId)
            } catch (e: Exception) {
                Log.e(TAG, "Fetch failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to fetch messages"
            } finally {
                _isLoading.value = false
            }
        }
    }
    // Send a new message using the database function
    fun sendMessage(
        householdId: String,
        memberId: String,
        content: String
    ) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null

                MessageRepository.sendMessage(
                    householdId = householdId,
                    memberId = memberId,
                    content = content
                )

                fetchMessages(householdId)

            } catch (e: Exception) {
                Log.e(TAG, "Send failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to send message"
            }
        }
    }

    // Edit message through database function
    fun editMessage(messageId: String, householdId: String, newContent: String) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null

                MessageRepository.editMessage(
                    messageId = messageId,
                    newContent = newContent
                )

                fetchMessages(householdId)

            } catch (e: Exception) {
                Log.e(TAG, "Edit failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to edit message"
            }
        }
    }

    // Delete message using database function
    fun deleteMessage(messageId: String, householdId: String) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null

                MessageRepository.deleteMessage(messageId)

                fetchMessages(householdId)

            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to delete message"
            }
        }
    }

    // Clears message error text after it is shown
    fun clearError() {
        _errorMessage.value = null
    }
}