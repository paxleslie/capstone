package com.group5.corkboardApp.data.repository

import com.group5.corkboardApp.data.model.Post
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

object PostRepository {
    private val client = SupabaseClient.client

    suspend fun createPost(post: Post) {
        client.postgrest["posts"].insert(post)
    }
}
