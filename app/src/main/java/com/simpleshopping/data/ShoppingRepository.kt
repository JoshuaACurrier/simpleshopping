package com.simpleshopping.data

import android.content.SharedPreferences
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class ShoppingRepository(
    private val database: ShoppingDatabase,
    private val sectionDao: SectionDao,
    private val itemDao: ItemDao,
    private val itemHistoryDao: ItemHistoryDao,
    private val tripSnapshotDao: TripSnapshotDao,
    private val prefs: SharedPreferences
) {
    val allSections: Flow<List<Section>> = sectionDao.getAllSections()
    val allItems: Flow<List<Item>> = itemDao.getAllItems()

    // --- Preferences ---

    fun isShoppingComplete(): Boolean = prefs.getBoolean(PREF_SHOPPING_COMPLETE, false)

    fun setShoppingComplete(complete: Boolean) {
        prefs.edit().putBoolean(PREF_SHOPPING_COMPLETE, complete).apply()
    }

    fun isIGotItEnabled(): Boolean = prefs.getBoolean(PREF_I_GOT_IT_ENABLED, false)

    fun setIGotItEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_I_GOT_IT_ENABLED, enabled).apply()
    }

    companion object {
        const val PREF_SHOPPING_COMPLETE = "shopping_complete"
        const val PREF_I_GOT_IT_ENABLED = "i_got_it_enabled"
    }

    // --- Sections ---

    suspend fun addSection(name: String): Long {
        val sortOrder = sectionDao.getNextSortOrder()
        return sectionDao.insert(Section(name = name, sortOrder = sortOrder))
    }

    suspend fun updateSection(section: Section) = sectionDao.update(section)

    suspend fun deleteSection(section: Section) = sectionDao.delete(section)

    suspend fun reorderSections(orderedIds: List<Long>) {
        database.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                sectionDao.updateSortOrder(id, index)
            }
        }
    }

    // --- Items ---

    suspend fun addItem(name: String, sectionId: Long, isRecurring: Boolean = false): Long {
        return database.withTransaction {
            // Duplicate detection: if same unchecked item exists in same section, increment qty
            val existing = itemDao.findByNameAndSection(name, sectionId)
            if (existing != null) {
                itemDao.incrementQuantity(existing.id)
                recordHistory(name, sectionId)
                existing.id
            } else {
                val sortOrder = itemDao.getNextSortOrder(sectionId)
                val id = itemDao.insert(
                    Item(
                        name = name,
                        sectionId = sectionId,
                        isRecurring = isRecurring,
                        sortOrder = sortOrder
                    )
                )
                recordHistory(name, sectionId)
                id
            }
        }
    }

    private suspend fun recordHistory(name: String, sectionId: Long) {
        val inserted = itemHistoryDao.insert(
            ItemHistory(name = name, sectionId = sectionId)
        )
        if (inserted == -1L) {
            itemHistoryDao.incrementUsage(name, sectionId)
        }
    }

    suspend fun updateItem(item: Item) = itemDao.update(item)

    suspend fun deleteItem(item: Item) = itemDao.delete(item)

    suspend fun toggleChecked(item: Item): Item {
        val newChecked = !item.isChecked
        val checkPosition = if (newChecked) itemDao.getNextCheckPosition() else null
        val updated = item.copy(isChecked = newChecked, checkPosition = checkPosition)
        itemDao.update(updated)
        return updated
    }

    suspend fun toggleRecurring(item: Item): Item {
        val updated = item.copy(isRecurring = !item.isRecurring)
        itemDao.update(updated)
        return updated
    }

    // --- History (autocomplete) ---

    suspend fun searchHistory(query: String): List<ItemHistory> {
        return itemHistoryDao.searchByName(query)
    }

    suspend fun searchHistory(query: String, sectionId: Long): List<ItemHistory> {
        return itemHistoryDao.searchByNameInSection(query, sectionId)
    }

    // --- Trips ---

    suspend fun startNewTrip() {
        database.withTransaction {
            // 1. Snapshot current items
            val currentItems = itemDao.getAllItemsList()
            tripSnapshotDao.clearAll()
            tripSnapshotDao.insertAll(
                currentItems.map { item ->
                    TripSnapshot(
                        itemName = item.name,
                        sectionId = item.sectionId,
                        wasRecurring = item.isRecurring
                    )
                }
            )

            // 2. Update history with check positions from this trip
            currentItems.filter { it.checkPosition != null }.forEach { item ->
                itemHistoryDao.updateCheckPosition(item.name, item.sectionId, item.checkPosition!!)
            }

            // 3. Uncheck all items and clear check positions
            itemDao.uncheckAll()

            // 4. Delete non-recurring items
            itemDao.deleteNonRecurring()
        }
    }

    suspend fun copyLastTrip() {
        val snapshots = tripSnapshotDao.getAll()
        val existingItems = itemDao.getAllItemsList()
        val existingKeys = existingItems.map { "${it.name}|${it.sectionId}" }.toSet()

        snapshots.forEach { snapshot ->
            val key = "${snapshot.itemName}|${snapshot.sectionId}"
            if (key !in existingKeys) {
                addItem(
                    name = snapshot.itemName,
                    sectionId = snapshot.sectionId,
                    isRecurring = snapshot.wasRecurring
                )
            }
        }
    }

    suspend fun hasLastTripSnapshot(): Boolean = tripSnapshotDao.hasSnapshot()

    // --- Store route sort ---

    suspend fun getHistoryCheckPosition(name: String, sectionId: Long): Int? {
        return itemHistoryDao.getCheckPosition(name, sectionId)
    }
}
