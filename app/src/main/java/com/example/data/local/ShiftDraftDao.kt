package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ShiftDraft
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDraftDao {
    @Query("SELECT * FROM shift_drafts WHERE id = 1 LIMIT 1")
    fun getDraft(): Flow<ShiftDraft?>

    @Query("SELECT * FROM shift_drafts WHERE id = 1 LIMIT 1")
    suspend fun getDraftDirect(): ShiftDraft?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: ShiftDraft)

    @Query("DELETE FROM shift_drafts WHERE id = 1")
    suspend fun clearDraft()
}
