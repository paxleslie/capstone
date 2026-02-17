package com.example.myapplication

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "YOUR_URL_HERE",
        supabaseKey = "YOUR_KEY_HERE"
    ) {
        install(Auth)
        install(Postgrest)
    }
}