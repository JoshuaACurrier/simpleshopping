package com.simpleshopping.data

data class ItemReorderEntry(
    val itemId: Long,
    val targetSectionId: Long,
    val newSortOrder: Int
)
