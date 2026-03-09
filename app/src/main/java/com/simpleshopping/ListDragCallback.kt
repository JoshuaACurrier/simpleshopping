package com.simpleshopping

import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.simpleshopping.adapter.ListItem
import com.simpleshopping.adapter.ShoppingListAdapter
import com.simpleshopping.data.ItemReorderEntry
import com.simpleshopping.data.Section

class ListDragCallback(
    private val adapter: ShoppingListAdapter,
    private val deleteZone: View,
    private val onSectionDragStarted: () -> Unit,
    private val onItemDragStarted: () -> Unit,
    private val onSectionReorder: (List<Long>) -> Unit,
    private val onSectionDeleteDrop: (Section) -> Unit,
    private val onItemReorder: (List<ItemReorderEntry>, List<ListItem>) -> Unit,
    private val onDragEnded: () -> Unit
) : ItemTouchHelper.Callback() {

    private enum class DragType { SECTION, ITEM }

    private var dragType: DragType? = null
    private var dragStarted = false
    private var lastOverDeleteZone = false
    private var draggedSection: Section? = null

    override fun isLongPressDragEnabled(): Boolean = false
    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if (adapter.mode != AppMode.CREATE) return 0

        return when (viewHolder.itemViewType) {
            ShoppingListAdapter.VIEW_TYPE_HEADER -> {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return 0
                val item = adapter.currentList.getOrNull(position)
                if (item is ListItem.SectionHeader && item.section.id == ShoppingListViewModel.I_GOT_IT_SECTION_ID) return 0
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            }
            ShoppingListAdapter.VIEW_TYPE_ITEM -> {
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            }
            else -> 0
        }
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && !dragStarted) {
            dragStarted = true
            val pos = viewHolder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
            if (pos != RecyclerView.NO_POSITION) {
                when (viewHolder?.itemViewType) {
                    ShoppingListAdapter.VIEW_TYPE_HEADER -> {
                        dragType = DragType.SECTION
                        val item = adapter.currentList.getOrNull(pos)
                        if (item is ListItem.SectionHeader) draggedSection = item.section
                        onSectionDragStarted()
                    }
                    ShoppingListAdapter.VIEW_TYPE_ITEM -> {
                        dragType = DragType.ITEM
                        onItemDragStarted()
                    }
                }
            }
            viewHolder?.itemView?.apply {
                alpha = 0.9f
                scaleX = 1.05f
                scaleY = 1.05f
                elevation = 12f
            }
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromType = viewHolder.itemViewType
        val toType = target.itemViewType
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition

        return when {
            // Section over section
            fromType == ShoppingListAdapter.VIEW_TYPE_HEADER && toType == ShoppingListAdapter.VIEW_TYPE_HEADER -> {
                adapter.moveItem(from, to)
                true
            }
            // Item over item — only within the same section
            fromType == ShoppingListAdapter.VIEW_TYPE_ITEM && toType == ShoppingListAdapter.VIEW_TYPE_ITEM -> {
                if (adapter.getSectionIdAt(from) != adapter.getSectionIdAt(to)) return false
                adapter.moveItem(from, to)
                true
            }
            // Item over section header — block (cross-section moves use the context menu)
            fromType == ShoppingListAdapter.VIEW_TYPE_ITEM && toType == ShoppingListAdapter.VIEW_TYPE_HEADER -> false
            else -> false
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No swipe support
    }

    override fun onChildDraw(
        c: android.graphics.Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

        // Delete zone highlight only for section drags
        if (dragType == DragType.SECTION && actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
            val itemBottom = viewHolder.itemView.top + viewHolder.itemView.height + dY.toInt()
            val recyclerBottom = recyclerView.height
            val isOverDeleteZone = itemBottom > recyclerBottom - deleteZone.height
            lastOverDeleteZone = isOverDeleteZone && deleteZone.visibility == View.VISIBLE
            if (deleteZone.visibility == View.VISIBLE) {
                val colorRes = if (isOverDeleteZone) R.color.notepad_delete_zone_active else R.color.notepad_delete_zone
                deleteZone.setBackgroundColor(ContextCompat.getColor(recyclerView.context, colorRes))
                deleteZone.scaleX = if (isOverDeleteZone) 1.05f else 1.0f
                deleteZone.scaleY = if (isOverDeleteZone) 1.05f else 1.0f
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.apply {
            alpha = 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
            elevation = 0f
        }

        if (!dragStarted) return
        dragStarted = false

        // Capture snapshot before clearDragState() nulls dragList
        val itemSnapshot = if (dragType == DragType.ITEM) adapter.getDragSnapshot() else null

        when (dragType) {
            DragType.SECTION -> {
                val isOverDeleteZone = lastOverDeleteZone
                lastOverDeleteZone = false
                if (isOverDeleteZone) {
                    val section = draggedSection
                    if (section != null) {
                        onSectionDeleteDrop(section)
                    } else {
                        onSectionReorder(adapter.getCurrentSectionOrder())
                        onDragEnded()
                    }
                } else {
                    onSectionReorder(adapter.getCurrentSectionOrder())
                    onDragEnded()
                }
                draggedSection = null
            }
            DragType.ITEM -> {
                onItemReorder(adapter.getCurrentItemOrderWithSections(), itemSnapshot!!)
                onDragEnded()
            }
            null -> onDragEnded()
        }

        dragType = null
        adapter.clearDragState(itemSnapshot)
    }
}
