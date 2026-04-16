package com.group5.corkboardApp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Household(
    val household_id: String,
    val household_name: String,
    val owner_member_id: String? = null,
    val created_at: String? = null
)

@Serializable
data class Profile(
    val display_name: String? = null,
    val name: String? = null,
    val email: String? = null
)

@Serializable
data class HouseholdMember(
    val member_id: String,
    val user_id: String,
    val household_id: String,
    val role: String,
    val nickname: String,
    // the actual table is called users
    @SerialName("users")
    val profile: Profile? = null
)

// Represents a row from the public users table
@Serializable
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val display_name: String = "",
    val phone: String = ""
)
