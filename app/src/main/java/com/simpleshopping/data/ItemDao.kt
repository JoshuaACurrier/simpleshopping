package com.simpleshopping.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY section_id, sort_order ASC")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT * FROM items ORDER BY section_id, sort_order ASC")
    suspend fun getAllItemsList(): List<Item>

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("UPDATE items SET is_checked = 0, check_position = NULL")
    suspend fun uncheckAll()

    @Query("DELETE FROM items WHERE is_recurring = 0")
    suspend fun deleteNonRecurring()

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM items WHERE section_id = :sectionId")
    suspend fun getNextSortOrder(sectionId: Long): Int

    @Query("SELECT COALESCE(MAX(check_position), 0) + 1 FROM items WHERE check_position IS NOT NULL")
    suspend fun getNextCheckPosition(): Int

    @Query("SELECT * FROM items WHERE name = :name COLLATE NOCASE AND section_id = :sectionId AND is_checked = 0 LIMIT 1")
    suspend fun findByNameAndSection(name: String, sectionId: Long): Item?

    @Query("UPDATE items SET quantity = quantity + :amount WHERE id = :itemId")
    suspend fun incrementQuantity(itemId: Long, amount: Int = 1)
}
