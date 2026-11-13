package com.example.data.model

import com.example.ui.viewmodel.ItemCheckState
import org.json.JSONArray
import org.json.JSONObject

data class ChecklistSnapshotEntry(
    val id: Long,
    val title: String,
    val emoji: String,
    val category: String,
    val isRequired: Boolean,
    val isCamerasItem: Boolean,
    val orderIndex: Int,
    val status: String,
    val observation: String = "",
    val photoPath: String? = null
)

object ChecklistSnapshotHelper {

    fun toSnapshotJson(
        activeItems: List<ChecklistTemplateItem>,
        itemStates: Map<Long, ItemCheckState>
    ): String {
        val array = JSONArray()
        activeItems
            .filter { it.isEnabled }
            .sortedBy { it.orderIndex }
            .forEach { item ->
                val check = itemStates[item.id] ?: ItemCheckState("OK")
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("emoji", item.emoji)
                    put("category", item.category)
                    put("isRequired", item.isRequired)
                    put("isCamerasItem", item.isCamerasItem)
                    put("orderIndex", item.orderIndex)
                    put("status", check.status)
                    put("observation", check.observation)
                    put("photoPath", check.photoPath ?: "")
                }
                array.put(obj)
            }
        return array.toString()
    }

    fun fromSnapshotJson(json: String?): List<ChecklistSnapshotEntry>? {
        if (json.isNullOrBlank()) return null
        return try {
            val array = JSONArray(json)
            if (array.length() == 0) return null
            val list = mutableListOf<ChecklistSnapshotEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val photo = obj.optString("photoPath", "").ifBlank { null }
                list.add(
                    ChecklistSnapshotEntry(
                        id = obj.optLong("id", (i + 1).toLong()),
                        title = obj.optString("title", ""),
                        emoji = obj.optString("emoji", "📋"),
                        category = obj.optString("category", "Geral"),
                        isRequired = obj.optBoolean("isRequired", true),
                        isCamerasItem = obj.optBoolean("isCamerasItem", false),
                        orderIndex = obj.optInt("orderIndex", i),
                        status = obj.optString("status", "OK"),
                        observation = obj.optString("observation", ""),
                        photoPath = photo
                    )
                )
            }
            list.sortedBy { it.orderIndex }
        } catch (_: Exception) {
            null
        }
    }

    fun toItemsAndStates(
        entries: List<ChecklistSnapshotEntry>
    ): Pair<List<ChecklistTemplateItem>, Map<Long, ItemCheckState>> {
        val items = entries.mapIndexed { idx, entry ->
            val safeId = if (entry.id > 0) entry.id else (idx + 1).toLong()
            ChecklistTemplateItem(
                id = safeId,
                title = entry.title,
                emoji = entry.emoji,
                isRequired = entry.isRequired,
                isCamerasItem = entry.isCamerasItem,
                orderIndex = entry.orderIndex,
                isEnabled = true,
                category = entry.category
            )
        }
        val states = entries.mapIndexed { idx, entry ->
            val safeId = if (entry.id > 0) entry.id else (idx + 1).toLong()
            safeId to ItemCheckState(
                status = entry.status,
                observation = entry.observation,
                photoPath = entry.photoPath
            )
        }.toMap()
        return items to states
    }
}
