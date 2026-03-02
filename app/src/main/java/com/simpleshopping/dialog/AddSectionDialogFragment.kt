package com.simpleshopping.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simpleshopping.R
import com.simpleshopping.ShoppingListViewModel
import com.simpleshopping.data.Section
import com.simpleshopping.databinding.DialogAddSectionBinding

class AddSectionDialogFragment : DialogFragment() {

    private val viewModel: ShoppingListViewModel by activityViewModels()
    private var _binding: DialogAddSectionBinding? = null
    private val binding get() = _binding!!

    private val existingSection: Section? by lazy {
        val args = arguments ?: return@lazy null
        val id = args.getLong(ARG_SECTION_ID, -1L)
        val name = args.getString(ARG_SECTION_NAME)
        val sortOrder = args.getInt(ARG_SECTION_SORT_ORDER, 0)
        val isDefault = args.getBoolean(ARG_SECTION_IS_DEFAULT, false)
        if (id != -1L && name != null) {
            Section(id = id, name = name, sortOrder = sortOrder, isDefault = isDefault)
        } else null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddSectionBinding.inflate(layoutInflater)

        val section = existingSection

        if (section != null) {
            binding.sectionNameInput.setText(section.name)
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (section != null) R.string.edit_section_title else R.string.add_section_title)
            .setView(binding.root)
            .setPositiveButton(if (section != null) R.string.save else R.string.add) { _, _ ->
                val name = binding.sectionNameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (section != null) {
                        viewModel.updateSection(section, name)
                    } else {
                        viewModel.addSection(name)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)

        if (section != null && !section.isDefault) {
            builder.setNeutralButton(R.string.delete_section) { _, _ ->
                viewModel.deleteSection(section)
            }
        }

        return builder.create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SECTION_ID = "section_id"
        private const val ARG_SECTION_NAME = "section_name"
        private const val ARG_SECTION_SORT_ORDER = "section_sort_order"
        private const val ARG_SECTION_IS_DEFAULT = "section_is_default"

        fun newInstance(section: Section?): AddSectionDialogFragment {
            return AddSectionDialogFragment().apply {
                arguments = Bundle().apply {
                    if (section != null) {
                        putLong(ARG_SECTION_ID, section.id)
                        putString(ARG_SECTION_NAME, section.name)
                        putInt(ARG_SECTION_SORT_ORDER, section.sortOrder)
                        putBoolean(ARG_SECTION_IS_DEFAULT, section.isDefault)
                    }
                }
            }
        }
    }
}
