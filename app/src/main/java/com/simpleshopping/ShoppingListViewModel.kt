package com.simpleshopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simpleshopping.adapter.ListItem
import com.simpleshopping.data.Item
import com.simpleshopping.data.ItemHistory
import com.simpleshopping.data.Section
import com.simpleshopping.data.ShoppingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel(
    private val repository: ShoppingRepository
) : ViewModel() {

    private val _mode = MutableStateFlow(AppMode.CREATE)
    val mode: StateFlow<AppMode> = _mode.asStateFlow()

    val allItemsChecked: StateFlow<Boolean> = repository.allItems
        .map { items -> items.isNotEmpty() && items.all { it.isChecked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _sortMode = MutableStateFlow(SortMode.MANUAL)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _inlineInputSectionId = MutableStateFlow<Long?>(null)
    val inlineInputSectionId: StateFlow<Long?> = _inlineInputSectionId.asStateFlow()

    private val _iGotItEnabled = MutableStateFlow(repository.isIGotItEnabled())
    val iGotItEnabled: StateFlow<Boolean> = _iGotItEnabled.asStateFlow()

    private val _collapsedSections = MutableStateFlow<Set<Long>>(emptySet())

    private val _isDraggingSections = MutableStateFlow(false)
    val isDraggingSections: StateFlow<Boolean> = _isDraggingSections.asStateFlow()

    private data class UiConfig(
        val inlineInputSectionId: Long?,
        val iGotItEnabled: Boolean,
        val collapsedSections: Set<Long>,
        val mode: AppMode
    )

    val listItems: StateFlow<List<ListItem>> = combine(
        repository.allSections,
        repository.allItems,
        combine(_sortMode, _isDraggingSections) { sort, drag -> sort to drag },
        combine(_inlineInputSectionId, _iGotItEnabled, _collapsedSections, _mode) { a, b, c, d ->
            UiConfig(a, b, c, d)
        }
    ) { sections, items, sortAndDrag, config ->
        val (sortMode, isDragging) = sortAndDrag
        if (isDragging) {
            sections.map { ListItem.SectionHeader(it) }
        } else {
            buildFlatList(sections, items, sortMode, config.inlineInputSectionId, config.iGotItEnabled, config.collapsedSections, config.mode)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sections: StateFlow<List<Section>> = repository.allSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildFlatList(
        sections: List<Section>,
        items: List<Item>,
        sortMode: SortMode,
        inlineInputSectionId: Long?,
        iGotItEnabled: Boolean,
        collapsedSections: Set<Long>,
        mode: AppMode
    ): List<ListItem> {
        val itemsBySection = items.groupBy { it.sectionId }
        val result = mutableListOf<ListItem>()
        val iGotItItems = mutableListOf<Item>()

        for (section in sections) {
            val sectionItems = itemsBySection[section.id] ?: emptyList()

            // In CREATE mode, never show checked items
            val visibleItems = if (mode == AppMode.CREATE) {
                sectionItems.filter { !it.isChecked }
            } else {
                sectionItems
            }

            val (checked, unchecked) = if (iGotItEnabled) {
                visibleItems.partition { it.isChecked }
            } else {
                emptyList<Item>() to visibleItems
            }

            if (iGotItEnabled) {
                iGotItItems.addAll(checked)
            }

            val displayItems = if (iGotItEnabled) unchecked else visibleItems

            if (displayItems.isNotEmpty() || section.isDefault || mode == AppMode.CREATE) {
                val isCollapsed = mode == AppMode.SHOPPING && section.id in collapsedSections
                result.add(ListItem.SectionHeader(section, isCollapsed, mode))

                if (!isCollapsed) {
                    if (inlineInputSectionId == section.id) {
                        result.add(ListItem.InlineInput(section.id))
                    }

                    val sorted = when (sortMode) {
                        SortMode.MANUAL -> displayItems.sortedBy { it.sortOrder }
                        SortMode.STORE_ROUTE -> displayItems.sortedBy { it.checkPosition ?: Int.MAX_VALUE }
                    }
                    result.addAll(sorted.map { ListItem.ShoppingItem(it, mode) })
                }
            }
        }

        if (iGotItEnabled && iGotItItems.isNotEmpty()) {
            val isCollapsed = I_GOT_IT_SECTION_ID in collapsedSections
            val virtualSection = Section(id = I_GOT_IT_SECTION_ID, name = "I got it!", sortOrder = Int.MAX_VALUE, isDefault = false)
            result.add(ListItem.SectionHeader(virtualSection, isCollapsed, mode))
            if (!isCollapsed) {
                result.addAll(
                    iGotItItems.sortedBy { it.checkPosition ?: Int.MAX_VALUE }
                        .map { ListItem.ShoppingItem(it, mode) }
                )
            }
        }

        return result
    }

    // --- Mode ---

    fun toggleMode() {
        _inlineInputSectionId.value = null
        _mode.value = when (_mode.value) {
            AppMode.CREATE -> AppMode.SHOPPING
            AppMode.SHOPPING -> AppMode.CREATE
        }
    }

    fun toggleSortMode() {
        _sortMode.value = when (_sortMode.value) {
            SortMode.MANUAL -> SortMode.STORE_ROUTE
            SortMode.STORE_ROUTE -> SortMode.MANUAL
        }
    }

    // --- Inline input ---

    fun showInlineInput(sectionId: Long) {
        _inlineInputSectionId.value = sectionId
    }

    fun hideInlineInput() {
        _inlineInputSectionId.value = null
    }

    // --- "I got it" ---

    fun setIGotItEnabled(enabled: Boolean) {
        _iGotItEnabled.value = enabled
        repository.setIGotItEnabled(enabled)
    }

    // --- Collapsible sections ---

    fun toggleSectionCollapse(sectionId: Long) {
        _collapsedSections.value = _collapsedSections.value.let { current ->
            if (sectionId in current) current - sectionId else current + sectionId
        }
    }

    // --- Items ---

    fun addItem(name: String, sectionId: Long, isRecurring: Boolean = false) {
        viewModelScope.launch {
            repository.addItem(name, sectionId, isRecurring)
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun toggleChecked(item: Item) {
        viewModelScope.launch {
            repository.toggleChecked(item)
        }
    }

    fun toggleRecurring(item: Item) {
        viewModelScope.launch {
            repository.toggleRecurring(item)
        }
    }

    fun updateItemQuantity(item: Item, newQuantity: Int) {
        viewModelScope.launch {
            if (newQuantity < 1) {
                repository.deleteItem(item)
            } else {
                repository.updateItem(item.copy(quantity = newQuantity))
            }
        }
    }

    // --- Section drag reorder ---

    fun startSectionDrag() {
        _inlineInputSectionId.value = null
        _isDraggingSections.value = true
    }

    fun stopSectionDrag() {
        _isDraggingSections.value = false
    }

    fun reorderSections(orderedIds: List<Long>) {
        viewModelScope.launch {
            repository.reorderSections(orderedIds)
        }
    }

    // --- Sections ---

    fun addSection(name: String) {
        viewModelScope.launch {
            repository.addSection(name)
        }
    }

    fun updateSection(section: Section, newName: String) {
        viewModelScope.launch {
            repository.updateSection(section.copy(name = newName))
        }
    }

    fun deleteSection(section: Section) {
        viewModelScope.launch {
            repository.deleteSection(section)
        }
    }

    // --- Trips ---

    fun startNewTrip() {
        viewModelScope.launch {
            repository.startNewTrip()
        }
    }

    fun finishShopping() {
        viewModelScope.launch {
            repository.startNewTrip()
            repository.setShoppingComplete(false)
            _mode.value = AppMode.CREATE
        }
    }

    fun checkAutoFinish() {
        if (repository.isShoppingComplete()) {
            finishShopping()
        }
    }

    fun markShoppingComplete() {
        repository.setShoppingComplete(true)
    }

    fun copyLastTrip(onNoSnapshot: () -> Unit) {
        viewModelScope.launch {
            if (repository.hasLastTripSnapshot()) {
                repository.copyLastTrip()
            } else {
                onNoSnapshot()
            }
        }
    }

    // --- Autocomplete ---

    suspend fun searchHistory(query: String): List<ItemHistory> {
        return repository.searchHistory(query)
    }

    suspend fun searchHistory(query: String, sectionId: Long): List<ItemHistory> {
        return repository.searchHistory(query, sectionId)
    }

    // --- Factory ---

    class Factory(
        private val repository: ShoppingRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShoppingListViewModel(repository) as T
        }
    }

    companion object {
        const val I_GOT_IT_SECTION_ID = -1L
    }
}
