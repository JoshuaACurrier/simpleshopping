package com.simpleshopping.adapter

import com.simpleshopping.AppMode
import com.simpleshopping.data.Item
import com.simpleshopping.data.Section

sealed class ListItem {
    data class SectionHeader(val section: Section, val isCollapsed: Boolean = false, val mode: AppMode = AppMode.CREATE) : ListItem()
    data class ShoppingItem(val item: Item, val mode: AppMode = AppMode.CREATE) : ListItem()
    data class InlineInput(val sectionId: Long) : ListItem()
}
