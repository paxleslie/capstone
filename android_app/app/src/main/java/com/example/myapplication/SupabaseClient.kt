package com.example.myapplication

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://qdggpxcywnqbapwyqgkw.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFkZ2dweGN5d25xYmFwd3lxZ2t3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEzNjAyNzksImV4cCI6MjA4NjkzNjI3OX0.J3RnsRjkP_mBrEBIfuLBezZvXciSUz0xwqoTAgNYKBs"
    ) {
        httpEngine = OkHttp.create()
        install(Auth)
        install(Postgrest)
    }
}
