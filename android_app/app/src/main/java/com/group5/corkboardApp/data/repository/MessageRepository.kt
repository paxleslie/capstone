package com.group5.corkboardApp.data.repository

import com.group5.corkboardApp.ui.message.Message
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object MessageRepository {
    private val client by lazy { SupabaseClient.client }

    // Get all messages for one household
    suspend fun getMessagesForHousehold(householdId: String): List<Message> {
        return client.postgrest["messages"]
            .select(
                Columns.raw("*, household_members(member_id, nickname, role, users(display_name, name))")
            ) {
                filter {
                    eq("household_id", householdId)
                }
            }
            .decodeList<Message>()
            .sortedBy { it.sent_at }
    }

    // Send message through database function
    suspend fun sendMessage(
        householdId: String,
        memberId: String,
        content: String
    ) {
        client.postgrest.rpc(
            "send_message",
            buildJsonObject {
                put("p_household_id", householdId)
                put("p_from_member_id", memberId)
                put("p_content", content)
            }
        )
    }
}