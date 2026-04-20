package com.group5.corkboardApp.data.repository

import com.group5.corkboardApp.data.model.Post
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PostRepository {
    private val client by lazy { SupabaseClient.client }

    suspend fun createPost(post: Post) {
        client.postgrest["posts"].insert(post)
    }

    suspend fun getPostsForHouseholds(householdIds: List<String>): List<Post> {
        if (householdIds.isEmpty()) return emptyList()
        return client.postgrest["posts"]
            .select { filter { isIn("household_id", householdIds) } }
            .decodeList<Post>()
    }

    suspend fun updatePost(postId: String, title: String?, body: String?, pointValue: Int?, dueAt: String?, color: String? = null) {
        client.postgrest["posts"].update({
            set("title", title)
            set("body", body)
            set("point_value", pointValue)
            set("due_at", dueAt)
            if (color != null) set("color", color)
        }) { filter { eq("post_id", postId) } }
    }

    suspend fun deletePost(postId: String) {
        client.postgrest["posts"].delete { filter { eq("post_id", postId) } }
    }

    suspend fun completeChore(postId: String, memberId: String) {
        client.postgrest.rpc("complete_chore", buildJsonObject {
            put("p_post_id", postId)
            put("p_member_id", memberId)
        })
    }
}
