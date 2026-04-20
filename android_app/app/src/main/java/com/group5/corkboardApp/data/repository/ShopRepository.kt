package com.group5.corkboardApp.data.repository

import android.util.Log
import com.group5.corkboardApp.data.model.MemberReward
import com.group5.corkboardApp.data.model.ShopItem
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ShopRepository {
    private val client = SupabaseClient.client
    private const val TAG = "ShopRepository"

    suspend fun getShopItems(householdId: String): List<ShopItem> {
        return try {
            val result = client.postgrest["rewards"]
                .select {
                    filter {
                        or {
                            filter("household_id", FilterOperator.IS, null)
                            eq("household_id", householdId)
                        }
                    }
                }
                .decodeList<ShopItem>()
            Log.d(TAG, "Fetched ${result.size} shop items")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching shop items: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getOwnedItems(memberId: String, householdId: String): List<String> {
        return try {
            client.postgrest["member_rewards"]
                .select {
                    filter {
                        eq("member_id", memberId)
                    }
                }
                .decodeList<MemberReward>()
                .map { it.reward_id }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching owned items: ${e.message}")
            emptyList()
        }
    }

    suspend fun buyItem(memberId: String, householdId: String, itemId: String, price: Int) {
        try {
            client.postgrest.rpc("purchase_shop_item", buildJsonObject {
                put("p_member_id", memberId)
                put("p_household_id", householdId)
                put("p_item_id", itemId)
                put("p_price", price)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error buying item: ${e.message}")
            throw e
        }
    }

    suspend fun createReward(title: String, cost: Int, type: String, value: String, householdId: String?): ShopItem {
        try {
            Log.d(TAG, "Attempting to create reward: $title")
            val response = client.postgrest["rewards"].insert(buildJsonObject {
                put("title", title)
                put("cost", cost)
                put("type", type)
                put("value", value)
                if (householdId != null) {
                    put("household_id", householdId)
                }
            }) {
                select()
            }
            val createdItem = response.decodeSingle<ShopItem>()
            Log.d(TAG, "Successfully created reward: ${createdItem.name} with ID: ${createdItem.id}")
            return createdItem
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create reward '$title': ${e.message}", e)
            throw e
        }
    }
}
