package com.simpleshopping

import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.simpleshopping.adapter.ListItem
import com.simpleshopping.adapter.ShoppingListAdapter
import com.simpleshopping.data.Section

class SectionDragCallback(
    private val adapter: ShoppingListAdapter,
    private val deleteZone: View,
    private val onDragStarted: () -> Unit,
    private val onReorder: (List<Long>) -> Unit,
    private val onDeleteDrop: (Section) -> Unit
) : ItemTouchHelper.Callback() {

    private var dragStarted = false

    override fun isLongPressDragEnabled(): Boolean = true
    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if (adapter.mode != AppMode.CREATE) return 0
        if (viewHolder.itemViewType != ShoppingListAdapter.VIEW_TYPE_HEADER) return 0
        // Don't allow dragging the virtual "I got it!" section
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return 0
        val item = adapter.currentList.getOrNull(position)
        if (item is ListItem.SectionHeader && item.section.id == ShoppingListViewModel.I_GOT_IT_SECTION_ID) return 0

        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && !dragStarted) {
            dragStarted = true
            onDragStarted()
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
        if (target.itemViewType != ShoppingListAdapter.VIEW_TYPE_HEADER) return false
        adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
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

        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
            val itemBottom = viewHolder.itemView.top + viewHolder.itemView.height + dY.toInt()
            val recyclerBottom = recyclerView.height

            // Highlight delete zone when item is near the bottom
            val isOverDeleteZone = itemBottom > recyclerBottom - deleteZone.height
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

        // Check if dropped over delete zone
        val itemBottom = viewHolder.itemView.bottom
        val recyclerBottom = recyclerView.height
        val isOverDeleteZone = itemBottom > recyclerBottom - deleteZone.height && deleteZone.visibility == View.VISIBLE

        if (isOverDeleteZone) {
            val position = viewHolder.bindingAdapterPosition
            val item = if (position != RecyclerView.NO_POSITION) {
                adapter.currentList.getOrNull(position)
            } else {
                // Use drag list position
                val dragOrder = adapter.getCurrentSectionOrder()
                val idx = viewHolder.layoutPosition
                if (idx in dragOrder.indices) {
                    adapter.currentList.getOrNull(idx)
                } else null
            }
            if (item is ListItem.SectionHeader) {
                onDeleteDrop(item.section)
            } else {
                onReorder(adapter.getCurrentSectionOrder())
            }
        } else {
            onReorder(adapter.getCurrentSectionOrder())
        }

        adapter.clearDragState()
    }
}
