package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val role: String, // "Porteiro", "Vigilante", "Supervisor", "Folguista", "Controlador de Acesso"
    val shift: String, // "Noturno (19h às 07h)", "Diurno (07h às 19h)", "12x36", "Geral"
    val phone: String = "",
    val badgeNumber: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
