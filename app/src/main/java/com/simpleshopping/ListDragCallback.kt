package com.simpleshopping

import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.simpleshopping.adapter.ListItem
import com.simpleshopping.adapter.ShoppingListAdapter
import com.simpleshopping.data.ItemReorderEntry
import com.simpleshopping.data.Section

class ListDragCallback(
    private val adapter: ShoppingListAdapter,
    private val onSectionDragStarted: () -> Unit,
    private val onItemDragStarted: () -> Unit,
    private val onSectionReorder: (List<Long>, Long?) -> Unit,
    private val onItemReorder: (List<ItemReorderEntry>, List<ListItem>) -> Unit,
    private val onDragEnded: () -> Unit
) : ItemTouchHelper.Callback() {

    private enum class DragType { SECTION, ITEM }

    private var dragType: DragType? = null
    private var dragStarted = false
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

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.animate()
            .alpha(1.0f)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator(2.0f))
            .withEndAction { viewHolder.itemView.elevation = 0f }
            .start()

        if (!dragStarted) return
        dragStarted = false

        // Capture snapshot before clearDragState() nulls dragList
        val itemSnapshot = if (dragType == DragType.ITEM) adapter.getDragSnapshot() else null

        when (dragType) {
            DragType.SECTION -> {
                onSectionReorder(adapter.getCurrentSectionOrder(), draggedSection?.id)
                onDragEnded()
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
