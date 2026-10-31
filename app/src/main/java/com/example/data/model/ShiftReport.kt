package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_reports")
data class ShiftReport(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val greeting: String, // "Boa Noite", "Bom Dia", "Boa Tarde"
    val postName: String, // "*PORTARIA*"
    val departingEmployee: String,
    val receivingEmployee: String,
    val reportContent: String, // The exact formatted WhatsApp message
    val cameraWorkingCount: String = "28 funcionando",
    val monitor1Details: String = "Câmera 15 fora do ar",
    val monitor2Details: String = "Câmeras 11,15 e 16 fora do ar",
    val generalNotes: String = ""
)
