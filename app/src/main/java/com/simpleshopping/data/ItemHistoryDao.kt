package com.simpleshopping.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ItemHistoryDao {
    @Query("SELECT * FROM item_history WHERE name LIKE '%' || :query || '%' ORDER BY usage_count DESC LIMIT 10")
    suspend fun searchByName(query: String): List<ItemHistory>

    @Query("SELECT * FROM item_history WHERE name LIKE '%' || :query || '%' AND section_id = :sectionId ORDER BY usage_count DESC LIMIT 10")
    suspend fun searchByNameInSection(query: String, sectionId: Long): List<ItemHistory>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: ItemHistory): Long

    @Query("UPDATE item_history SET usage_count = usage_count + 1, section_id = :sectionId WHERE name = :name AND section_id = :sectionId")
    suspend fun incrementUsage(name: String, sectionId: Long): Int

    @Query("UPDATE item_history SET last_check_position = :position WHERE name = :name AND section_id = :sectionId")
    suspend fun updateCheckPosition(name: String, sectionId: Long, position: Int)

    @Query("SELECT last_check_position FROM item_history WHERE name = :name AND section_id = :sectionId")
    suspend fun getCheckPosition(name: String, sectionId: Long): Int?

    @Query("DELETE FROM item_history")
    suspend fun clearAll()

    @Query("SELECT * FROM item_history WHERE name = :name AND section_id = :sectionId LIMIT 1")
    suspend fun findByNameAndSection(name: String, sectionId: Long): ItemHistory?

    @Query("UPDATE item_history SET name = :newName WHERE name = :oldName AND section_id = :sectionId")
    suspend fun renameEntry(oldName: String, newName: String, sectionId: Long)

    @Query("UPDATE item_history SET usage_count = usage_count + :addCount WHERE name = :name AND section_id = :sectionId")
    suspend fun addUsageCount(name: String, sectionId: Long, addCount: Int)

    @Query("DELETE FROM item_history WHERE name = :name AND section_id = :sectionId")
    suspend fun deleteByNameAndSection(name: String, sectionId: Long)
}
