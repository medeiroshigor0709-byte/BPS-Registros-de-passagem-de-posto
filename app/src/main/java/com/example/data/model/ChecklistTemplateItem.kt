package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checklist_templates")
data class ChecklistTemplateItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val emoji: String,
    val isRequired: Boolean = true, // Obrigatório ou Opcional
    val isCamerasItem: Boolean = false, // Special cameras/monitors inspection
    val orderIndex: Int = 0,
    val isEnabled: Boolean = true,
    val category: String = "Geral",
    val modelName: String = DEFAULT_MODEL_NAME
) {
    companion object {
        const val DEFAULT_MODEL_NAME = "BPS FACILITIES — PORTARIA"
    }
}
