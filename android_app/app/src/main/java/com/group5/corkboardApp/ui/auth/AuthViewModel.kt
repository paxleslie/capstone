package com.group5.corkboardApp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.util.SupabaseClient
import kotlinx.coroutines.launch

public class AuthViewModel : ViewModel() {
    // use client for anything to do with supabase
    private val client = SupabaseClient.client

    fun daFucntion() {
        // this makes it launch in the background and wont freeze the app if it
        // takes a while to respond.
        viewModelScope.launch {
            //code here
        }
    }
}