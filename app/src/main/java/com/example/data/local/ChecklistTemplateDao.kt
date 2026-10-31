package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChecklistTemplateItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistTemplateDao {
    @Query("SELECT * FROM checklist_templates ORDER BY orderIndex ASC")
    fun getAllTemplateItems(): Flow<List<ChecklistTemplateItem>>

    @Query("SELECT * FROM checklist_templates WHERE isEnabled = 1 ORDER BY orderIndex ASC")
    fun getActiveTemplateItems(): Flow<List<ChecklistTemplateItem>>

    @Query("SELECT COUNT(*) FROM checklist_templates")
    suspend fun getTemplateCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ChecklistTemplateItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChecklistTemplateItem>)

    @Update
    suspend fun updateItem(item: ChecklistTemplateItem)

    @Delete
    suspend fun deleteItem(item: ChecklistTemplateItem)

    @Query("DELETE FROM checklist_templates WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM checklist_templates")
    suspend fun clearAll()
}
