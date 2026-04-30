package com.group5.corkboardApp.ui.message

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.util.SupabaseClient
import com.group5.corkboardApp.data.repository.MessageRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
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

    // Joined household_members row so we can display nickname/role
    @SerialName("household_members")
    val senderMember: SenderMember? = null
)

// Represents joined sender info from household_members
@Serializable
data class SenderMember(
    val member_id: String,
    val nickname: String? = null,
    val role: String? = null
)

// Used to get member IDs for a household
@Serializable
data class HouseholdMessageMember(
    val member_id: String
)

class MessageViewModel : ViewModel() {

    // Supabase client
    private val client = SupabaseClient.client

    // Logcat tag
    private val TAG = "MessageVM"

    // Holds messages for the UI
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    // Holds send/fetch error messages for the UI
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Fetch all messages for a household
    fun fetchMessages(householdId: String) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                _messages.value = MessageRepository.getMessagesForHousehold(householdId)
            } catch (e: Exception) {
                Log.e(TAG, "Fetch failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to fetch messages"
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

    // Clears message error text after it is shown
    fun clearError() {
        _errorMessage.value = null
    }
}