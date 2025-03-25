package com.spotlylb.admin.ui.orders

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.spotlylb.admin.R
import com.spotlylb.admin.databinding.DialogOrderFiltersBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OrderFilterDialogFragment : DialogFragment() {
    private var _binding: DialogOrderFiltersBinding? = null
    private val binding get() = _binding!!

    private var listener: FilterAppliedListener? = null
    private var currentFilters = OrderFilters()

    interface FilterAppliedListener {
        fun onFiltersApplied(filters: OrderFilters)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOrderFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set initial values from current filters
        setupInitialValues()

        // Set up date pickers
        setupDatePickers()

        // Set up buttons
        binding.btnApply.setOnClickListener {
            applyFilters()
        }

        binding.btnReset.setOnClickListener {
            resetFilters()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // Set the dialog width to 90% of screen width
            val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)

            // Set dialog attributes for better appearance
            setBackgroundDrawableResource(R.drawable.dialog_background)
        }
    }

    private fun setupInitialValues() {
        binding.editOrderId.setText(currentFilters.orderId ?: "")

        // Set status spinner selection
        val statusPosition = when (currentFilters.status?.lowercase()) {
            "pending" -> 1
            "confirmed" -> 2
            "shipped" -> 3
            "delivered" -> 4
            "cancelled" -> 5
            else -> 0 // All
        }
        binding.spinnerStatus.setSelection(statusPosition)

        // Set sort option selection
        binding.radioNewest.isChecked = currentFilters.sortBy == SortOption.NEWEST
        binding.radioOldest.isChecked = currentFilters.sortBy == SortOption.OLDEST

        // Set date values if available
        currentFilters.dateFrom?.let { from ->
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            binding.editDateFrom.setText(dateFormat.format(from))
        }

        currentFilters.dateTo?.let { to ->
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            binding.editDateTo.setText(dateFormat.format(to))
        }
    }

    private fun setupDatePickers() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        binding.editDateFrom.setOnClickListener {
            showDatePicker(currentFilters.dateFrom) { date ->
                currentFilters = currentFilters.copy(dateFrom = date)
                binding.editDateFrom.setText(dateFormat.format(date))
            }
        }

        binding.editDateTo.setOnClickListener {
            showDatePicker(currentFilters.dateTo) { date ->
                currentFilters = currentFilters.copy(dateTo = date)
                binding.editDateTo.setText(dateFormat.format(date))
            }
        }
    }

    private fun showDatePicker(initialDate: Date?, onDateSelected: (Date) -> Unit) {
        val calendar = Calendar.getInstance()
        initialDate?.let { calendar.time = it }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                onDateSelected(calendar.time)
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun applyFilters() {
        val orderId = binding.editOrderId.text.toString().trim().takeIf { it.isNotEmpty() }

        val statusPosition = binding.spinnerStatus.selectedItemPosition
        val status = when (statusPosition) {
            0 -> null // All
            1 -> "pending"
            2 -> "confirmed"
            3 -> "shipped"
            4 -> "delivered"
            5 -> "cancelled"
            else -> null
        }

        val sortOption = if (binding.radioNewest.isChecked) SortOption.NEWEST else SortOption.OLDEST

        val filters = OrderFilters(
            orderId = orderId,
            status = status,
            dateFrom = currentFilters.dateFrom,
            dateTo = currentFilters.dateTo,
            sortBy = sortOption
        )

        listener?.onFiltersApplied(filters)
        dismiss()
    }

    private fun resetFilters() {
        currentFilters = OrderFilters()
        setupInitialValues()
    }

    fun setFilterAppliedListener(listener: FilterAppliedListener) {
        this.listener = listener
    }

    fun setCurrentFilters(filters: OrderFilters) {
        currentFilters = filters
        if (isAdded && view != null) {
            setupInitialValues()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(currentFilters: OrderFilters): OrderFilterDialogFragment {
            val fragment = OrderFilterDialogFragment()
            fragment.currentFilters = currentFilters
            return fragment
        }

        const val TAG = "OrderFilterDialogFragment"
    }
}