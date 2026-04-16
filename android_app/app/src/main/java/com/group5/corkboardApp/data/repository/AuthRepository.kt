package com.group5.corkboardApp.data.repository

import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.JsonObject

object AuthRepository {
    private val client = SupabaseClient.client

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, metadata: JsonObject) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            this.data = metadata
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    fun currentUser(): UserInfo? {
        return client.auth.currentUserOrNull()
    }

    suspend fun updateUser(data: JsonObject) {
        client.auth.updateUser { this.data = data }
    }

    suspend fun updateEmail(newEmail: String) {
        client.auth.updateUser { email = newEmail }
    }

    suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser { password = newPassword }
    }

    // Re-authenticates with the current password to verify it before allowing a change
    suspend fun verifyPassword(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }
}
