package com.group5.corkboardApp.data.repository

import com.group5.corkboardApp.data.model.UserProfile
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

object UserRepository {
    private val client = SupabaseClient.client

    suspend fun getUserProfile(userId: String): UserProfile {
        return client.postgrest["users"]
            .select { filter { eq("user_id", userId) } }
            .decodeSingle<UserProfile>()
    }

    suspend fun updateProfile(userId: String, name: String, displayName: String, phone: String) {
        client.postgrest["users"].update(
            {
                set("name", name)
                set("display_name", displayName)
                set("phone", phone)
            }
        ) {
            filter { eq("user_id", userId) }
        }
    }

    suspend fun updateEmail(userId: String, newEmail: String) {
        client.postgrest["users"].update(
            { set("email", newEmail) }
        ) {
            filter { eq("user_id", userId) }
        }
    }
}
