package com.simpleshopping

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.simpleshopping.adapter.ListItem
import com.simpleshopping.adapter.ShoppingListAdapter
import com.simpleshopping.data.Item
import com.simpleshopping.data.Section
import com.simpleshopping.databinding.ActivityMainBinding
import com.takusemba.spotlight.OnSpotlightListener
import com.takusemba.spotlight.OnTargetListener
import com.takusemba.spotlight.Spotlight
import com.takusemba.spotlight.Target
import com.takusemba.spotlight.shape.Circle
import com.takusemba.spotlight.shape.RoundedRectangle

class TutorialManager(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val adapter: ShoppingListAdapter,
    private val viewModel: ShoppingListViewModel
) {
    private var spotlight: Spotlight? = null
    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldShowTutorial(): Boolean {
        return !prefs.getBoolean(PREF_TUTORIAL_SHOWN, false)
    }

    fun markTutorialShown() {
        prefs.edit().putBoolean(PREF_TUTORIAL_SHOWN, true).apply()
    }

    fun isRunning(): Boolean = spotlight != null

    fun startTutorial() {
        // Set CREATE mode demo list
        viewModel.setDemoItems(createModeDemoList())

        // Wait for the demo list to be laid out by RecyclerView before building targets.
        // setDemoItems → StateFlow → collect → submitList → async DiffUtil → layout,
        // so we poll until a ViewHolder actually exists.
        waitForDemoLayout {
            val overlays = mutableListOf<View>()
            val targets = buildTargets(overlays)
            if (targets.isEmpty()) {
                viewModel.setDemoItems(null)
                return@waitForDemoLayout
            }

            spotlight = Spotlight.Builder(activity)
                .setTargets(targets)
                .setBackgroundColorRes(R.color.spotlight_background)
                .setOnSpotlightListener(object : OnSpotlightListener {
                    override fun onStarted() {}
                    override fun onEnded() {
                        viewModel.setDemoItems(null)
                        spotlight = null
                    }
                })
                .build()

            // Wire up tap-to-advance on every overlay root
            for ((index, overlay) in overlays.withIndex()) {
                val isLast = index == overlays.lastIndex
                overlay.setOnClickListener {
                    if (isLast) {
                        finish()
                    } else {
                        spotlight?.next()
                    }
                }
            }

            spotlight?.start()
            markTutorialShown()
        }
    }

    /**
     * Polls via post until the RecyclerView has actually laid out the demo items.
     * DiffUtil runs asynchronously, so we can't just double-post.
     */
    private fun waitForDemoLayout(retries: Int = 20, onReady: () -> Unit) {
        binding.recyclerView.post {
            if (binding.recyclerView.findViewHolderForAdapterPosition(0) != null) {
                onReady()
            } else if (retries > 0) {
                waitForDemoLayout(retries - 1, onReady)
            } else {
                // Give up — shouldn't happen, but don't leave demo mode stuck
                viewModel.setDemoItems(null)
            }
        }
    }

    fun finish() {
        viewModel.setDemoItems(null)
        spotlight?.finish()
        spotlight = null
    }

    // --- Demo list builders ---

    private fun createModeDemoList(): List<ListItem> {
        val produce = Section(id = DEMO_SECTION_PRODUCE, name = "Produce", sortOrder = 0, isDefault = true)
        val dairy = Section(id = DEMO_SECTION_DAIRY, name = "Dairy", sortOrder = 1, isDefault = true)
        val bakery = Section(id = DEMO_SECTION_BAKERY, name = "Bakery", sortOrder = 2, isDefault = true)

        return listOf(
            ListItem.SectionHeader(produce, isCollapsed = false, mode = AppMode.CREATE),
            ListItem.ShoppingItem(Item(id = -101, sectionId = DEMO_SECTION_PRODUCE, name = "Bananas", sortOrder = 0), mode = AppMode.CREATE),
            ListItem.ShoppingItem(Item(id = -102, sectionId = DEMO_SECTION_PRODUCE, name = "Apples", isRecurring = true, sortOrder = 1), mode = AppMode.CREATE),
            ListItem.SectionHeader(dairy, isCollapsed = false, mode = AppMode.CREATE),
            ListItem.ShoppingItem(Item(id = -103, sectionId = DEMO_SECTION_DAIRY, name = "Milk", sortOrder = 0), mode = AppMode.CREATE),
            ListItem.ShoppingItem(Item(id = -104, sectionId = DEMO_SECTION_DAIRY, name = "Cheese", sortOrder = 1), mode = AppMode.CREATE),
            ListItem.SectionHeader(bakery, isCollapsed = false, mode = AppMode.CREATE),
            ListItem.ShoppingItem(Item(id = -105, sectionId = DEMO_SECTION_BAKERY, name = "Bread", sortOrder = 0), mode = AppMode.CREATE),
        )
    }

    private fun shoppingModeDemoList(): List<ListItem> {
        val produce = Section(id = DEMO_SECTION_PRODUCE, name = "Produce", sortOrder = 0, isDefault = true)
        val dairy = Section(id = DEMO_SECTION_DAIRY, name = "Dairy", sortOrder = 1, isDefault = true)
        val bakery = Section(id = DEMO_SECTION_BAKERY, name = "Bakery", sortOrder = 2, isDefault = true)

        return listOf(
            ListItem.SectionHeader(produce, isCollapsed = false, mode = AppMode.SHOPPING),
            ListItem.ShoppingItem(Item(id = -101, sectionId = DEMO_SECTION_PRODUCE, name = "Bananas", sortOrder = 0), mode = AppMode.SHOPPING),
            ListItem.ShoppingItem(Item(id = -102, sectionId = DEMO_SECTION_PRODUCE, name = "Apples", isRecurring = true, sortOrder = 1), mode = AppMode.SHOPPING),
            ListItem.SectionHeader(dairy, isCollapsed = false, mode = AppMode.SHOPPING),
            ListItem.ShoppingItem(Item(id = -103, sectionId = DEMO_SECTION_DAIRY, name = "Milk", isChecked = true, sortOrder = 0), mode = AppMode.SHOPPING),
            ListItem.ShoppingItem(Item(id = -104, sectionId = DEMO_SECTION_DAIRY, name = "Cheese", sortOrder = 1), mode = AppMode.SHOPPING),
            ListItem.SectionHeader(bakery, isCollapsed = false, mode = AppMode.SHOPPING),
            ListItem.ShoppingItem(Item(id = -105, sectionId = DEMO_SECTION_BAKERY, name = "Bread", sortOrder = 0), mode = AppMode.SHOPPING),
        )
    }

    // --- Target builders ---

    private fun buildTargets(overlays: MutableList<View>): List<Target> {
        val targets = mutableListOf<Target>()
        val inflater = LayoutInflater.from(activity)
        val totalSteps = 9

        // Step 1: Welcome overlay
        targets += buildWelcomeTarget(inflater, overlays)

        // Step 2: Tap a category — highlight Produce section header (position 0)
        targets += buildHighlightRowTarget(
            inflater, overlays, totalSteps, step = 2, adapterPosition = 0,
            titleRes = R.string.tutorial_section_title, bodyRes = R.string.tutorial_section_body
        )

        // Step 3: Item row — highlight "Bananas" (position 1)
        targets += buildHighlightRowTarget(
            inflater, overlays, totalSteps, step = 3, adapterPosition = 1,
            titleRes = R.string.tutorial_item_title, bodyRes = R.string.tutorial_item_body
        )

        // Step 4: Hold section header — highlight Produce again (position 0)
        targets += buildHighlightRowTarget(
            inflater, overlays, totalSteps, step = 4, adapterPosition = 0,
            titleRes = R.string.tutorial_hold_section_title, bodyRes = R.string.tutorial_hold_section_body
        )

        // Step 5: Overflow menu (three dots)
        targets += buildMenuTarget(inflater, overlays, totalSteps)

        // Step 6: FAB — switch to shopping mode
        targets += buildFabTarget(inflater, overlays, totalSteps)

        // Step 7: Tap item in shopping mode — highlight "Bananas" row (position 1)
        targets += buildShoppingItemTarget(inflater, overlays, totalSteps)

        // Step 8: Section collapse — highlight section header (position 0)
        targets += buildHighlightRowTarget(
            inflater, overlays, totalSteps, step = 8, adapterPosition = 0,
            titleRes = R.string.tutorial_collapse_title, bodyRes = R.string.tutorial_collapse_body
        )

        // Step 9: Finish overlay
        targets += buildFinishTarget(inflater, overlays)

        return targets
    }

    private fun buildWelcomeTarget(inflater: LayoutInflater, overlays: MutableList<View>): Target {
        val overlay = inflater.inflate(R.layout.tutorial_overlay_welcome, null)
        overlays += overlay

        overlay.findViewById<View>(R.id.tutorialSkipButton).setOnClickListener {
            finish()
        }

        return Target.Builder()
            .setAnchor(
                binding.recyclerView.width / 2f,
                binding.recyclerView.height / 2f
            )
            .setShape(Circle(0f))
            .setOverlay(overlay)
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {}
                override fun onEnded() {}
            })
            .build()
    }

    private fun buildHighlightRowTarget(
        inflater: LayoutInflater,
        overlays: MutableList<View>,
        totalSteps: Int,
        step: Int,
        adapterPosition: Int,
        titleRes: Int,
        bodyRes: Int
    ): Target {
        val overlay = inflater.inflate(R.layout.tutorial_overlay_step, null)
        overlays += overlay
        setupStepOverlay(overlay, titleRes, bodyRes, step, totalSteps)

        val holder = binding.recyclerView.findViewHolderForAdapterPosition(adapterPosition)
        val anchorView = holder?.itemView ?: binding.recyclerView

        val location = IntArray(2)
        anchorView.getLocationInWindow(location)
        val anchorX = location[0] + anchorView.width / 2f
        val anchorY = location[1] + anchorView.height / 2f

        positionOverlay(overlay, anchorY)

        return Target.Builder()
            .setAnchor(anchorX, anchorY)
            .setShape(RoundedRectangle(
                anchorView.height.toFloat(),
                anchorView.width.toFloat(),
                8f
            ))
            .setOverlay(overlay)
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {}
                override fun onEnded() {}
            })
            .build()
    }

    private fun buildMenuTarget(inflater: LayoutInflater, overlays: MutableList<View>, totalSteps: Int): Target {
        val overlay = inflater.inflate(R.layout.tutorial_overlay_step, null)
        overlays += overlay
        setupStepOverlay(overlay, R.string.tutorial_menu_title, R.string.tutorial_menu_body, 5, totalSteps)

        val overflowButton = findOverflowButton() ?: binding.toolbar

        val location = IntArray(2)
        overflowButton.getLocationInWindow(location)
        val anchorX = location[0] + overflowButton.width / 2f
        val anchorY = location[1] + overflowButton.height / 2f
        positionOverlay(overlay, anchorY)

        val radius = if (overflowButton === binding.toolbar) {
            0f
        } else {
            overflowButton.width.toFloat() * 0.7f
        }

        return Target.Builder()
            .setAnchor(anchorX, anchorY)
            .setShape(Circle(radius))
            .setOverlay(overlay)
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {}
                override fun onEnded() {}
            })
            .build()
    }

    private fun buildFabTarget(inflater: LayoutInflater, overlays: MutableList<View>, totalSteps: Int): Target {
        val overlay = inflater.inflate(R.layout.tutorial_overlay_step, null)
        overlays += overlay
        setupStepOverlay(overlay, R.string.tutorial_fab_title, R.string.tutorial_fab_body, 6, totalSteps)

        val fab = binding.fabStartShopping
        val location = IntArray(2)
        fab.getLocationInWindow(location)
        val anchorX = location[0] + fab.width / 2f
        val anchorY = location[1] + fab.height / 2f
        positionOverlay(overlay, anchorY)

        return Target.Builder()
            .setAnchor(anchorX, anchorY)
            .setShape(Circle(fab.width / 2f + 16f))
            .setOverlay(overlay)
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {}
                override fun onEnded() {}
            })
            .build()
    }

    private fun buildShoppingItemTarget(inflater: LayoutInflater, overlays: MutableList<View>, totalSteps: Int): Target {
        val overlay = inflater.inflate(R.layout.tutorial_overlay_step, null)
        overlays += overlay
        setupStepOverlay(overlay, R.string.tutorial_shopping_title, R.string.tutorial_shopping_body, 7, totalSteps)

        // We'll use a fallback anchor initially — onStarted swaps to shopping demo list
        // and re-measures after layout settles
        val location = IntArray(2)
        binding.recyclerView.getLocationInWindow(location)

        // Pre-calculate a reasonable anchor (Bananas row = position 1)
        val holder = binding.recyclerView.findViewHolderForAdapterPosition(1)
        val anchorView = holder?.itemView ?: binding.recyclerView
        anchorView.getLocationInWindow(location)
        val anchorX = location[0] + anchorView.width / 2f
        val anchorY = location[1] + anchorView.height / 2f
        positionOverlay(overlay, anchorY)

        return Target.Builder()
            .setAnchor(anchorX, anchorY)
            .setShape(RoundedRectangle(
                anchorView.height.toFloat(),
                anchorView.width.toFloat(),
                8f
            ))
            .setOverlay(overlay)
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {
                    // Switch to shopping mode demo list when this step becomes active
                    viewModel.setDemoItems(shoppingModeDemoList())
                }
                override fun onEnded() {}
            })
            .build()
    }

    private fun buildFinishTarget(inflater: LayoutInflater, overlays: MutableList<View>): Target {
        val overlay = inflater.inflate(R.layout.tutorial_overlay_welcome, null)
        overlays += overlay

        // Repurpose the welcome layout for the finish screen
        overlay.findViewById<android.widget.TextView>(R.id.tutorialWelcomeTitle).text =
            activity.getString(R.string.tutorial_thank_you_title)
        overlay.findViewById<android.widget.TextView>(R.id.tutorialWelcomeBody).text =
            activity.getString(R.string.tutorial_thank_you_body)

        overlay.findViewById<View>(R.id.tutorialSkipButton).setOnClickListener {
            finish()
        }

        return Target.Builder()
            .setAnchor(
                binding.recyclerView.width / 2f,
                binding.recyclerView.height / 2f
            )
            .setShape(Circle(0f))
            .setOverlay(overlay)
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {}
                override fun onEnded() {}
            })
            .build()
    }

    // --- Helpers ---

    private fun setupStepOverlay(overlay: View, titleRes: Int, bodyRes: Int, step: Int, totalSteps: Int) {
        overlay.findViewById<android.widget.TextView>(R.id.tutorialStepTitle).text =
            activity.getString(titleRes)
        overlay.findViewById<android.widget.TextView>(R.id.tutorialStepBody).text =
            activity.getString(bodyRes)
        overlay.findViewById<android.widget.TextView>(R.id.tutorialStepCounter).text =
            activity.getString(R.string.tutorial_step_counter, step, totalSteps)

        // Wire up skip button on every step overlay
        overlay.findViewById<View>(R.id.tutorialSkipButton).setOnClickListener {
            finish()
        }
    }

    private fun positionOverlay(overlay: View, targetCenterY: Float) {
        val screenHeight = activity.resources.displayMetrics.heightPixels
        val isInTopHalf = targetCenterY < screenHeight / 2f

        val topSpacer = overlay.findViewById<View>(R.id.topSpacer)
        val bottomSpacer = overlay.findViewById<View>(R.id.bottomSpacer)

        if (isInTopHalf) {
            // Target is in top half — show text below
            (topSpacer.layoutParams as LinearLayout.LayoutParams).weight = 3f
            (bottomSpacer.layoutParams as LinearLayout.LayoutParams).weight = 1f
        } else {
            // Target is in bottom half — show text above
            (topSpacer.layoutParams as LinearLayout.LayoutParams).weight = 1f
            (bottomSpacer.layoutParams as LinearLayout.LayoutParams).weight = 3f
        }
    }

    private fun findOverflowButton(): View? {
        for (i in 0 until binding.toolbar.childCount) {
            val child = binding.toolbar.getChildAt(i)
            val desc = child.contentDescription?.toString() ?: ""
            if (desc.contains("More options", ignoreCase = true) ||
                desc.contains("overflow", ignoreCase = true)) {
                return child
            }
        }
        return null
    }

    companion object {
        private const val PREFS_NAME = "shopping_prefs"
        private const val PREF_TUTORIAL_SHOWN = "tutorial_shown"

        private const val DEMO_SECTION_PRODUCE = -100L
        private const val DEMO_SECTION_DAIRY = -200L
        private const val DEMO_SECTION_BAKERY = -300L
    }
}
