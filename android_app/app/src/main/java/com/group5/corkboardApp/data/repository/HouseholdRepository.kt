package com.group5.corkboardApp.data.repository

import android.util.Log
import com.group5.corkboardApp.data.model.Household
import com.group5.corkboardApp.data.model.HouseholdMember
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// RPC parameter classes — implementation details, not domain models
@Serializable
private data class CreateHouseholdParams(
    @SerialName("p_household_name") val householdName: String,
    @SerialName("p_user_id") val userID: String
)

@Serializable
private data class RemoveMemberParams(
    @SerialName("householdid") val householdId: String,
    @SerialName("userid") val memberId: String
)

@Serializable
private data class DeleteHouseholdParams(
    @SerialName("householdid") val householdId: String
)

object HouseholdRepository {
    private val TAG = "HouseholdRepository"
    private val client by lazy { SupabaseClient.client }

    suspend fun getUserHouseholds(userId: String): List<Household> {
        val memberships = getUserMemberships(userId)
        val householdIds = memberships.map { it.household_id }
        if (householdIds.isEmpty()) return emptyList()

        return client.postgrest["households"]
            .select { filter { isIn("household_id", householdIds) } }
            .decodeList<Household>()
    }

    // Returns full membership records so callers can access member_id and role
    suspend fun getUserMemberships(userId: String): List<HouseholdMember> {
        return client.postgrest["household_members"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<HouseholdMember>()
    }

    suspend fun getHouseholdDetails(householdId: String): Household? {
        return client.postgrest["households"]
            .select { filter { eq("household_id", householdId) } }
            .decodeList<Household>()
            .firstOrNull()
    }

    suspend fun getMembers(householdId: String): List<HouseholdMember> {
        return try {
            client.postgrest["household_members"]
                .select(Columns.raw("*, users(display_name, name, email)")) {
                    filter { eq("household_id", householdId) }
                }
                .decodeList<HouseholdMember>()
        } catch (e: Exception) {
            Log.e(TAG, "Join query failed, trying simple query", e)
            try {
                client.postgrest["household_members"]
                    .select { filter { eq("household_id", householdId) } }
                    .decodeList<HouseholdMember>()
            } catch (e2: Exception) {
                Log.e(TAG, "Simple query also failed", e2)
                emptyList()
            }
        }
    }

    suspend fun updateHouseholdName(householdId: String, newName: String) {
        client.postgrest["households"].update(
            { set("household_name", newName) }
        ) {
            filter { eq("household_id", householdId) }
        }
    }

    suspend fun createHousehold(name: String, userId: String) {
        client.postgrest.rpc(
            "create_household",
            CreateHouseholdParams(householdName = name, userID = userId)
        )
    }

    suspend fun addMemberByEmail(householdId: String, email: String) {
        val params = buildJsonObject {
            put("p_household_id", householdId)
            put("p_new_member_email", email)
        }
        client.postgrest.rpc("add_user_to_household_by_email", params)
    }

    suspend fun removeMember(householdId: String, memberId: String) {
        client.postgrest.rpc(
            "remove_household_member",
            RemoveMemberParams(householdId = householdId, memberId = memberId)
        )
    }

    suspend fun deleteHousehold(householdId: String) {
        client.postgrest.rpc(
            "delete_household",
            DeleteHouseholdParams(householdId = householdId)
        )
    }

    /**
     * Updates member points. 
     * Uses .select() to verify the update actually happened (catches RLS failures).
     */
    suspend fun updateMemberPoints(memberId: String, totalPoints: Int) {
        Log.d(TAG, "Updating total_points for memberId: $memberId to $totalPoints")
        val response = client.postgrest["household_members"].update(
            {
                set("total_points", totalPoints)
            }
        ) {
            filter { eq("member_id", memberId) }
            select() // Request data back to verify the update worked
        }.decodeSingleOrNull<HouseholdMember>()

        if (response == null) {
            throw Exception("Update failed. You may not have permission (check RLS policies).")
        }
    }
}
