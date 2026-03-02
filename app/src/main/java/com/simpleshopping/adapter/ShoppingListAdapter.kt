package com.simpleshopping.adapter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.simpleshopping.AppMode
import com.simpleshopping.ShoppingListViewModel
import com.simpleshopping.data.Item
import com.simpleshopping.data.ItemHistory
import com.simpleshopping.data.Section
import com.simpleshopping.databinding.ItemInlineInputBinding
import com.simpleshopping.databinding.ItemSectionHeaderBinding
import com.simpleshopping.databinding.ItemShoppingBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ShoppingListAdapter(
    private val onItemChecked: (Item) -> Unit,
    private val onItemRecurringToggle: (Item) -> Unit,
    private val onItemDelete: (Item) -> Unit,
    private val onSectionEdit: (Section) -> Unit,
    private val onSectionTapped: (Long) -> Unit,
    private val onInlineItemAdd: (String, Long) -> Unit,
    private val onInlineDismiss: () -> Unit,
    private val onQuantityChange: (Item, Int) -> Unit,
    private val onSectionCollapseToggle: (Long) -> Unit = {},
    private val searchHistory: suspend (String, Long) -> List<ItemHistory> = { _, _ -> emptyList() },
    private val coroutineScope: CoroutineScope? = null
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(ListItemDiffCallback()) {

    // Mode is tracked for drag callback checks; binding uses mode from ListItem
    var mode: AppMode = AppMode.CREATE

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ListItem.SectionHeader -> VIEW_TYPE_HEADER
        is ListItem.ShoppingItem -> VIEW_TYPE_ITEM
        is ListItem.InlineInput -> VIEW_TYPE_INPUT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> SectionViewHolder(
                ItemSectionHeaderBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_ITEM -> ItemViewHolder(
                ItemShoppingBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_INPUT -> InlineInputViewHolder(
                ItemInlineInputBinding.inflate(inflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.SectionHeader -> (holder as SectionViewHolder).bind(item.section, item.mode, item.isCollapsed)
            is ListItem.ShoppingItem -> (holder as ItemViewHolder).bind(item.item, item.mode)
            is ListItem.InlineInput -> (holder as InlineInputViewHolder).bind(item.sectionId)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is ItemViewHolder -> holder.cleanup()
            is InlineInputViewHolder -> holder.cleanup()
        }
    }

    inner class SectionViewHolder(
        private val binding: ItemSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: Section, mode: AppMode, isCollapsed: Boolean) {
            binding.sectionName.text = section.name
            binding.btnEditSection.visibility =
                if (mode == AppMode.CREATE && !section.isDefault && section.id != ShoppingListViewModel.I_GOT_IT_SECTION_ID) View.VISIBLE else View.GONE
            binding.btnEditSection.setOnClickListener { onSectionEdit(section) }

            if (mode == AppMode.CREATE && section.id != ShoppingListViewModel.I_GOT_IT_SECTION_ID) {
                binding.root.setOnClickListener { onSectionTapped(section.id) }
            } else if (mode == AppMode.SHOPPING) {
                binding.root.setOnClickListener { onSectionCollapseToggle(section.id) }
            } else {
                binding.root.setOnClickListener(null)
            }
        }
    }

    inner class ItemViewHolder(
        private val binding: ItemShoppingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var crossOffAnimator: ValueAnimator? = null

        fun bind(item: Item, mode: AppMode) {
            crossOffAnimator?.cancel()
            binding.itemName.text = item.name

            applyCheckedState(item.isChecked)

            binding.quantityBadge.visibility = if (item.quantity > 1) View.VISIBLE else View.GONE
            binding.quantityBadge.text = item.quantity.toString()
            binding.quantityBadge.contentDescription = itemView.context.getString(
                com.simpleshopping.R.string.quantity_description, item.quantity
            )

            when (mode) {
                AppMode.CREATE -> {
                    binding.starIcon.visibility = View.VISIBLE
                    binding.starIcon.setImageResource(
                        if (item.isRecurring) android.R.drawable.btn_star_big_on
                        else android.R.drawable.btn_star_big_off
                    )
                    binding.starIcon.setOnClickListener { onItemRecurringToggle(item) }

                    binding.root.setOnClickListener {
                        onQuantityChange(item, item.quantity + 1)
                    }
                    binding.root.setOnLongClickListener {
                        onQuantityChange(item, item.quantity - 1)
                        true
                    }
                }
                AppMode.SHOPPING -> {
                    binding.starIcon.visibility = View.GONE

                    binding.root.setOnClickListener {
                        animateCrossOff(!item.isChecked)
                        onItemChecked(item)
                    }
                    binding.root.setOnLongClickListener(null)
                }
            }
        }

        fun cleanup() {
            crossOffAnimator?.cancel()
            crossOffAnimator = null
        }

        private fun applyCheckedState(isChecked: Boolean) {
            if (isChecked) {
                binding.itemName.paintFlags = binding.itemName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.root.alpha = 0.4f
            } else {
                binding.itemName.paintFlags = binding.itemName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.root.alpha = 1.0f
            }
        }

        private fun animateCrossOff(crossingOff: Boolean) {
            crossOffAnimator?.cancel()
            if (crossingOff) {
                binding.itemName.paintFlags = binding.itemName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                crossOffAnimator = ValueAnimator.ofFloat(1.0f, 0.4f).apply {
                    duration = 300
                    addUpdateListener { binding.root.alpha = it.animatedValue as Float }
                    start()
                }
            } else {
                binding.itemName.paintFlags = binding.itemName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                crossOffAnimator = ValueAnimator.ofFloat(0.4f, 1.0f).apply {
                    duration = 300
                    addUpdateListener { binding.root.alpha = it.animatedValue as Float }
                    start()
                }
            }
        }
    }

    inner class InlineInputViewHolder(
        private val binding: ItemInlineInputBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var boundSectionId: Long = -1L
        private var autocompleteJob: Job? = null
        private var textWatcher: TextWatcher? = null

        fun bind(sectionId: Long) {
            boundSectionId = sectionId
            cleanup()
            binding.inlineItemName.setText("")
            binding.inlineItemName.requestFocus()

            binding.inlineItemName.post {
                val imm = itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.inlineItemName, InputMethodManager.SHOW_IMPLICIT)
            }

            setupAutocomplete()

            binding.inlineItemName.setOnItemClickListener { _, _, _, _ ->
                val text = binding.inlineItemName.text.toString().trim()
                if (text.isNotEmpty()) {
                    onInlineItemAdd(text, boundSectionId)
                }
                binding.inlineItemName.setText("")
                binding.inlineItemName.clearFocus()
            }

            binding.inlineItemName.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val text = binding.inlineItemName.text.toString().trim()
                    if (text.isNotEmpty()) {
                        onInlineItemAdd(text, boundSectionId)
                    }
                    binding.inlineItemName.setText("")
                    binding.inlineItemName.clearFocus()
                    true
                } else {
                    false
                }
            }

            binding.inlineItemName.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    autocompleteJob?.cancel()
                    val text = binding.inlineItemName.text.toString().trim()
                    if (text.isNotEmpty()) {
                        onInlineItemAdd(text, boundSectionId)
                    }
                    onInlineDismiss()
                }
            }
        }

        fun cleanup() {
            autocompleteJob?.cancel()
            autocompleteJob = null
            textWatcher?.let { binding.inlineItemName.removeTextChangedListener(it) }
            textWatcher = null
        }

        private fun setupAutocomplete() {
            val scope = coroutineScope ?: return

            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString()?.trim() ?: return
                    if (query.length < 2) return

                    autocompleteJob?.cancel()
                    autocompleteJob = scope.launch {
                        delay(300)
                        val results = searchHistory(query, boundSectionId)
                        if (results.isNotEmpty()) {
                            val names = results.map { it.name }
                            val adapter = ArrayAdapter(
                                itemView.context,
                                android.R.layout.simple_dropdown_item_1line,
                                names
                            )
                            binding.inlineItemName.setAdapter(adapter)
                            if (binding.inlineItemName.hasFocus()) {
                                binding.inlineItemName.showDropDown()
                            }
                        }
                    }
                }
            }
            textWatcher = watcher
            binding.inlineItemName.addTextChangedListener(watcher)
        }
    }

    private class ListItemDiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return when {
                oldItem is ListItem.SectionHeader && newItem is ListItem.SectionHeader ->
                    oldItem.section.id == newItem.section.id
                oldItem is ListItem.ShoppingItem && newItem is ListItem.ShoppingItem ->
                    oldItem.item.id == newItem.item.id
                oldItem is ListItem.InlineInput && newItem is ListItem.InlineInput ->
                    oldItem.sectionId == newItem.sectionId
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem == newItem
        }
    }

    // --- Drag reorder support ---

    private var dragList: MutableList<ListItem>? = null

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val list = dragList ?: currentList.toMutableList().also { dragList = it }
        val item = list.removeAt(fromPosition)
        list.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getCurrentSectionOrder(): List<Long> {
        val list = dragList ?: currentList
        return list.filterIsInstance<ListItem.SectionHeader>().map { it.section.id }
    }

    fun clearDragState() {
        dragList = null
    }

    companion object {
        const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_INPUT = 2
    }
}
