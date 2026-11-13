package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_drafts")
data class ShiftDraft(
    @PrimaryKey
    val id: Int = 1, // Singleton draft row
    val updatedAt: Long = System.currentTimeMillis(),
    val greeting: String = "Boa Noite",
    val postName: String = "*PORTARIA*",
    val receivingEmployee: String = "",
    val departingEmployee: String = "",
    val cameraWorkingCount: String = "Todas 32 funcionando",
    val monitor1Details: String = "1 Todas operantes (16/16)",
    val monitor2Details: String = "2 Todas operantes (16/16)",
    val generalNotes: String = "",
    val cameraStatesJson: String = ""
)
