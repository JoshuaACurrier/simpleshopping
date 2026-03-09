package com.simpleshopping

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simpleshopping.adapter.ListItem
import com.simpleshopping.adapter.ShoppingListAdapter
import com.simpleshopping.data.Item
import com.simpleshopping.data.Section
import com.simpleshopping.data.ShoppingDatabase
import com.simpleshopping.data.ShoppingRepository
import com.simpleshopping.databinding.ActivityMainBinding
import com.simpleshopping.dialog.AddSectionDialogFragment
import com.simpleshopping.dialog.EditItemDialogFragment
import com.simpleshopping.theme.ThemeManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val viewModel: ShoppingListViewModel by viewModels {
        val db = ShoppingDatabase.getInstance(applicationContext)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val repository = ShoppingRepository(
            db, db.sectionDao(), db.itemDao(), db.itemHistoryDao(), db.tripSnapshotDao(), prefs
        )
        ShoppingListViewModel.Factory(repository)
    }

    private lateinit var adapter: ShoppingListAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var tutorialManager: TutorialManager
    private var currentMode: AppMode? = null
    private var hasShownDoneToast = false
    private var modeColorAnimator: ValueAnimator? = null
    private var deleteZoneAnimator: ObjectAnimator? = null
    private val touchRect = Rect()

    private var toolbarColorCreate = 0
    private var toolbarColorShopping = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbarColorCreate = ContextCompat.getColor(this, R.color.notepad_toolbar_bg)
        toolbarColorShopping = ContextCompat.getColor(this, R.color.notepad_shopping_toolbar_bg)

        setupToolbar()
        setupRecyclerView()
        setupDragReorder()
        setupFab()
        observeState()
        setupTutorial()
    }

    override fun onDestroy() {
        super.onDestroy()
        tutorialManager.finish()
        modeColorAnimator?.cancel()
        deleteZoneAnimator?.cancel()
        binding.recyclerView.animate().cancel()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_toggle_mode -> {
                    hideKeyboard()
                    viewModel.toggleMode()
                    true
                }
                R.id.action_add_section -> {
                    showAddSectionDialog()
                    true
                }
                R.id.action_sort_mode -> {
                    viewModel.toggleSortMode()
                    true
                }
                R.id.action_new_trip -> {
                    showNewTripConfirmation()
                    true
                }
                R.id.action_copy_last_trip -> {
                    viewModel.copyLastTrip {
                        Toast.makeText(this, R.string.no_last_trip, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_i_got_it -> {
                    val newValue = !viewModel.iGotItEnabled.value
                    viewModel.setIGotItEnabled(newValue)
                    menuItem.isChecked = newValue
                    true
                }
                R.id.action_show_tutorial -> {
                    showTutorial()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ShoppingListAdapter(
            onItemChecked = { viewModel.toggleChecked(it) },
            onItemRecurringToggle = { viewModel.toggleRecurring(it) },
            onItemLongPress = { item, anchor -> showItemContextMenu(item, anchor) },
            onSectionLongPress = { section, anchor -> showSectionContextMenu(section, anchor) },
            onSectionTapped = { sectionId ->
                viewModel.showInlineInput(sectionId)
            },
            onInlineItemAdd = { name, sectionId ->
                viewModel.addItem(name, sectionId)
            },
            onInlineDismiss = {
                viewModel.hideInlineInput()
                hideKeyboard()
            },
            onQuantityChange = { item, newQty ->
                viewModel.updateItemQuantity(item, newQty)
            },
            onSectionCollapseToggle = { sectionId ->
                viewModel.toggleSectionCollapse(sectionId)
            },
            onItemDragHandleTouched = { vh -> itemTouchHelper.startDrag(vh) },
            onSectionDragHandleTouched = { vh -> itemTouchHelper.startDrag(vh) },
            searchHistory = { query, sectionId -> viewModel.searchHistory(query, sectionId) },
            coroutineScope = lifecycleScope
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null  // prevents swap animation after drag
        binding.recyclerView.addItemDecoration(NotepadItemDecoration(this))

        ViewCompat.setWindowInsetsAnimationCallback(
            binding.recyclerView,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat = insets

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    val imeVisible = ViewCompat.getRootWindowInsets(binding.recyclerView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    if (imeVisible) {
                        val inputIndex = adapter.currentList
                            .indexOfFirst { it is ListItem.InlineInput }
                        if (inputIndex >= 0) {
                            binding.recyclerView.scrollToPosition(inputIndex)
                        }
                    }
                }
            }
        )
    }

    private fun setupDragReorder() {
        val dragCallback = ListDragCallback(
            adapter = adapter,
            deleteZone = binding.deleteZone,
            onSectionDragStarted = {
                viewModel.startSectionDrag()
                showDeleteZone()
            },
            onItemDragStarted = {
                viewModel.startItemDrag()
            },
            onSectionReorder = { orderedIds ->
                viewModel.reorderSections(orderedIds)
                viewModel.stopSectionDrag()
                hideDeleteZone()
            },
            onSectionDeleteDrop = { section ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_section_confirm_title)
                    .setMessage(getString(R.string.delete_section_confirm_message, section.name))
                    .setPositiveButton(R.string.delete_section) { _, _ ->
                        viewModel.deleteSection(section)
                        viewModel.stopSectionDrag()
                        hideDeleteZone()
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        viewModel.stopSectionDrag()
                        hideDeleteZone()
                    }
                    .setOnCancelListener {
                        viewModel.stopSectionDrag()
                        hideDeleteZone()
                    }
                    .show()
            },
            onItemReorder = { triples, snapshot ->
                viewModel.reorderItems(triples, snapshot)
                // stopItemDrag() is called inside reorderItems after the DB write completes
            },
            onDragEnded = {}
        )
        itemTouchHelper = ItemTouchHelper(dragCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    private fun showDeleteZone() {
        deleteZoneAnimator?.cancel()
        val zone = binding.deleteZone
        zone.visibility = View.VISIBLE
        zone.post {
            val h = zone.height.toFloat()
            zone.translationY = h
            deleteZoneAnimator = ObjectAnimator.ofFloat(zone, "translationY", h, 0f).apply {
                duration = 200
                start()
            }
        }
    }

    private fun hideDeleteZone() {
        deleteZoneAnimator?.cancel()
        binding.deleteZone.apply {
            deleteZoneAnimator = ObjectAnimator.ofFloat(this, "translationY", 0f, height.toFloat()).apply {
                duration = 200
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        visibility = View.GONE
                    }
                })
                start()
            }
        }
    }

    private fun setupFab() {
        binding.fabStartShopping.setOnClickListener {
            hideKeyboard()
            viewModel.toggleMode()
        }
        binding.fabDone.setOnClickListener {
            viewModel.finishShopping()
        }
    }

    private fun observeState() {
        viewModel.checkAutoFinish()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.listItems.collect { items ->
                        adapter.submitList(items)

                        val inputIndex = items.indexOfFirst { it is ListItem.InlineInput }
                        if (inputIndex >= 0) {
                            binding.recyclerView.post {
                                binding.recyclerView.smoothScrollToPosition(inputIndex)
                            }
                        }
                    }
                }

                launch {
                    viewModel.mode.collect { mode ->
                        adapter.mode = mode
                        hasShownDoneToast = false
                        updateModeUi(mode)
                    }
                }

                launch {
                    combine(viewModel.mode, viewModel.sortMode, viewModel.iGotItEnabled) { mode, sortMode, iGotIt ->
                        Triple(mode, sortMode, iGotIt)
                    }.collect { (mode, sortMode, iGotIt) ->
                        updateMenuState(mode, sortMode, iGotIt)
                    }
                }

                launch {
                    combine(viewModel.allItemsChecked, viewModel.mode) { allChecked, mode ->
                        allChecked to mode
                    }.collect { (allChecked, mode) ->
                        if (allChecked && mode == AppMode.SHOPPING) {
                            binding.fabDone.show()
                            viewModel.markShoppingComplete()
                            if (!hasShownDoneToast) {
                                hasShownDoneToast = true
                                showDoneToast()
                            }
                        } else {
                            binding.fabDone.hide()
                        }
                    }
                }
            }
        }
    }

    private fun showDoneToast() {
        val messages = resources.getStringArray(R.array.done_shopping_messages)
        Snackbar.make(binding.fabDone, messages.random(), Snackbar.LENGTH_LONG)
            .setAnchorView(binding.fabDone)
            .show()
    }

    private fun updateModeUi(mode: AppMode) {
        val isFirstRun = currentMode == null
        val didChange = currentMode != mode
        currentMode = mode

        val toggleItem = binding.toolbar.menu.findItem(R.id.action_toggle_mode)

        when (mode) {
            AppMode.CREATE -> {
                binding.toolbar.title = getString(R.string.mode_create)
                toggleItem?.title = getString(R.string.switch_to_shopping)
                toggleItem?.setIcon(android.R.drawable.ic_menu_agenda)
                binding.fabStartShopping.show()
            }
            AppMode.SHOPPING -> {
                binding.toolbar.title = getString(R.string.mode_shopping)
                toggleItem?.title = getString(R.string.switch_to_create)
                toggleItem?.setIcon(android.R.drawable.ic_menu_edit)
                binding.fabStartShopping.hide()
            }
        }

        if (didChange && !isFirstRun) {
            modeColorAnimator?.cancel()
            binding.recyclerView.animate().cancel()
            binding.recyclerView.alpha = 1f

            val fromColor = if (mode == AppMode.SHOPPING) toolbarColorCreate else toolbarColorShopping
            val toColor = if (mode == AppMode.SHOPPING) toolbarColorShopping else toolbarColorCreate
            modeColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
                duration = 300
                addUpdateListener {
                    val color = it.animatedValue as Int
                    binding.toolbar.setBackgroundColor(color)
                    binding.appBarLayout.setBackgroundColor(color)
                }
                start()
            }

            binding.recyclerView.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    binding.recyclerView.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        } else if (isFirstRun) {
            val color = if (mode == AppMode.SHOPPING) toolbarColorShopping else toolbarColorCreate
            binding.toolbar.setBackgroundColor(color)
            binding.appBarLayout.setBackgroundColor(color)
        }
    }

    private fun updateMenuState(mode: AppMode, sortMode: SortMode, iGotIt: Boolean) {
        val sortItem = binding.toolbar.menu.findItem(R.id.action_sort_mode)
        sortItem?.title = when (sortMode) {
            SortMode.MANUAL -> getString(R.string.sort_by_store_route)
            SortMode.STORE_ROUTE -> getString(R.string.sort_manual)
        }

        val addSectionItem = binding.toolbar.menu.findItem(R.id.action_add_section)
        addSectionItem?.isVisible = mode == AppMode.CREATE

        val iGotItItem = binding.toolbar.menu.findItem(R.id.action_i_got_it)
        iGotItItem?.isChecked = iGotIt
        iGotItItem?.isVisible = mode == AppMode.SHOPPING
    }

    private fun showAddSectionDialog() {
        AddSectionDialogFragment.newInstance(null)
            .show(supportFragmentManager, "add_section")
    }

    private fun showNewTripConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_trip_confirm_title)
            .setMessage(R.string.new_trip_confirm_message)
            .setPositiveButton(R.string.start_trip) { _, _ ->
                viewModel.startNewTrip()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText) {
                focused.getGlobalVisibleRect(touchRect)
                if (!touchRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    focused.clearFocus()
                    hideKeyboard()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun setupTutorial() {
        tutorialManager = TutorialManager(this, binding, adapter, viewModel)

        // Dismiss tutorial on back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (tutorialManager.isRunning()) {
                    tutorialManager.finish()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Auto-show on first launch — TutorialManager handles its own timing
        if (tutorialManager.shouldShowTutorial()) {
            tutorialManager.startTutorial()
        }
    }

    private fun showTutorial() {
        tutorialManager.startTutorial()
    }

    private fun showItemContextMenu(item: Item, anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.popup_item_context, menu)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit_item -> {
                        EditItemDialogFragment.newInstance(item)
                            .show(supportFragmentManager, "edit_item")
                        true
                    }
                    R.id.action_move_item -> {
                        showMoveToSectionDialog(item)
                        true
                    }
                    R.id.action_delete_item -> {
                        viewModel.deleteItem(item)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showSectionContextMenu(section: Section, anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.popup_section_context, menu)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit_section -> {
                        AddSectionDialogFragment.newInstance(section)
                            .show(supportFragmentManager, "edit_section")
                        true
                    }
                    R.id.action_delete_section -> {
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(R.string.delete_section_confirm_title)
                            .setMessage(getString(R.string.delete_section_confirm_message, section.name))
                            .setPositiveButton(R.string.delete_section) { _, _ ->
                                viewModel.deleteSection(section)
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showMoveToSectionDialog(item: Item) {
        val sections = viewModel.sections.value.filter {
            it.id != ShoppingListViewModel.I_GOT_IT_SECTION_ID && it.id != item.sectionId
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.move_item_title)
            .setItems(sections.map { it.name }.toTypedArray()) { _, index ->
                viewModel.moveItemToSection(item, sections[index].id)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val PREFS_NAME = "shopping_prefs"
    }
}
