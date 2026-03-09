package com.simpleshopping.dialog

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simpleshopping.R
import com.simpleshopping.ShoppingListViewModel
import com.simpleshopping.data.Item
import com.simpleshopping.databinding.DialogEditItemBinding

class EditItemDialogFragment : DialogFragment() {

    private val viewModel: ShoppingListViewModel by activityViewModels()
    private var _binding: DialogEditItemBinding? = null
    private val binding get() = _binding!!

    private val item: Item? by lazy {
        arguments?.let { BundleCompat.getParcelable(it, ARG_ITEM, Item::class.java) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogEditItemBinding.inflate(layoutInflater)
        val currentItem = item ?: run {
            Log.e(TAG, "EditItemDialogFragment launched without a valid item argument")
            dismissAllowingStateLoss()
            return super.onCreateDialog(savedInstanceState)
        }

        binding.itemNameInput.setText(currentItem.name)
        binding.itemNameInput.setSelection(currentItem.name.length)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_item_title)
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = binding.itemNameInput.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentItem.name) {
                    viewModel.renameItem(currentItem, newName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "EditItemDialogFragment"
        private const val ARG_ITEM = "item"

        fun newInstance(item: Item): EditItemDialogFragment {
            return EditItemDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_ITEM, item)
                }
            }
        }
    }
}
