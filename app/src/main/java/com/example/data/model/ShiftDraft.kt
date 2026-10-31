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
    val receivingEmployee: String = "Higor",
    val departingEmployee: String = "",
    val cameraWorkingCount: String = "28 funcionando",
    val monitor1Details: String = "1 Câmera 15 fora do ar",
    val monitor2Details: String = "2 Câmeras 11,15 e 16 fora do ar",
    val generalNotes: String = ""
)
