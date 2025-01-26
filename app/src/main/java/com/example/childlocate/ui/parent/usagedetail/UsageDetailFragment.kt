package com.example.childlocate.ui.parent.usagedetail

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.childlocate.R
import com.example.childlocate.data.model.DayUsageStats
import com.example.childlocate.data.model.UsageStatsState
import com.example.childlocate.databinding.FragmentUsageDetailBinding
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.utils.ViewPortHandler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit


class UsageDetailFragment : Fragment() {
    private lateinit var binding: FragmentUsageDetailBinding
    private val viewModel: UsageStatsViewModel by lazy {
        ViewModelProvider(this)[UsageStatsViewModel::class.java]
    }

    private val navigationArgs: UsageDetailFragmentArgs by navArgs()
    private lateinit var appUsageAdapter: AppUsageAdapter

    private val dayStatsAdapter = DayStatsAdapter { dayStats ->
        viewModel.selectDay(dayStats)
    }

    private lateinit var childId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUsageDetailBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        childId = navigationArgs.childId
        Log.d("UsageDetailFragment", childId)
        // Load initial data
        viewModel.loadUsageStats(childId)
        appUsageAdapter = AppUsageAdapter(childId)

        setupRecyclerViews()
        setupSwipeRefresh()
        //setupCharts()
        observeViewModel()
        setupDateNavigation()
        //set up PinMode
        setupPinButton()
    }

    private fun setupPinButton() {
        binding.btnSetPin.setOnClickListener {
            showSetPinDialog()
        }
    }


    private fun showSetPinDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_set_pin, null)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Đặt mã PIN")
            .setView(dialogView)
            .setPositiveButton("Xác nhận") { dialog, _ ->
                val pin = dialogView.findViewById<TextInputEditText>(R.id.etPin).text.toString()
                val confirmPin = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPin).text.toString()

                if (pin.length < 4 || pin.length > 6) {
                    Toast.makeText(context, "PIN phải có độ dài từ 4-6 số", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (pin != confirmPin) {
                    Toast.makeText(context, "Mã PIN không khớp", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.setAppPin(childId, pin)
                Toast.makeText(context, "Đã đặt mã PIN thành công", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }



    private fun setupDateNavigation() {
        binding.prevDate.setOnClickListener {
            val needLoadPrevWeek = dayStatsAdapter.navigateToPreviousDay()
            if (needLoadPrevWeek) {
                viewModel.loadPreviousWeek(childId)
            }
        }

        binding.nextDate.setOnClickListener {
            val needLoadNextWeek = dayStatsAdapter.navigateToNextDay()
            if (needLoadNextWeek) {
                viewModel.loadNextWeek(childId)
            }
        }
     }

    private fun setupRecyclerViews() {
        //setup days recycler view
        binding.rvDays.apply {
            adapter = dayStatsAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }
        // Sau khi set data cho adapter
        viewLifecycleOwner.lifecycleScope.launch {
            // Đảm bảo list đã được load
            delay(1000)
            dayStatsAdapter.setInitialSelection()
        }
        //setup apps RecyclerViews
        binding.rvAppUsage.apply{
            layoutManager = LinearLayoutManager(context)
            adapter = appUsageAdapter
        }
    }
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshData(childId)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }
                launch {
                    viewModel.selectedDay.collect { dayStats ->
                        dayStats?.let { updateSelectedDayUI(it) }
                    }
                }
            }
        }
    }

    private fun handleUiState(state: UsageStatsState) {
        binding.swipeRefresh.isRefreshing = false
        binding.progressBar.isVisible = state is UsageStatsState.Loading
        binding.errorView.isVisible = state is UsageStatsState.Error
        binding.emptyView.isVisible = state is UsageStatsState.Empty

        when (state) {
            is UsageStatsState.Success -> {
                val sortedDays = state.data.dailyStats.values.sortedBy { it.date }
                dayStatsAdapter.submitList(sortedDays)
                setupBarChart(sortedDays)
            }
            is UsageStatsState.Error -> {
                binding.errorText.text = state.message
            }
            else -> {}
        }
    }

    private fun updateSelectedDayUI(dayStats: DayUsageStats) {
        // Update total usage time
        val hours = TimeUnit.MILLISECONDS.toHours(dayStats.totalTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(dayStats.totalTime) % 60
        binding.totalUsageTime.text = getString(R.string.usage_time_format, hours, minutes)

        // Update app list
        val sortedApps = dayStats.appUsageList.sortedByDescending { it.usageTime }
        appUsageAdapter.submitList(sortedApps)
    }

    private fun setupBarChart(days: List<DayUsageStats>) {
        binding.weeklyBarChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDrawGridBackground(false)

            val entries = days.mapIndexed { index, stats ->
                val hoursWithMinutes = stats.totalTime / (1000.0 * 60 * 60)
                BarEntry(index.toFloat(), hoursWithMinutes.toFloat())
                }

            val dataSet = BarDataSet(entries, "Thời gian sử dụng").apply {
                val colors = MutableList(entries.size) {
                    ContextCompat.getColor(requireContext(), R.color.teal_200)
                }

                // Đánh dấu cột được chọn
                val selectedDay = viewModel.selectedDay.value
                val selectedIndex = days.indexOfFirst { it.date == selectedDay?.date }
                if (selectedIndex != -1) {
                    colors[selectedIndex] = ContextCompat.getColor(requireContext(), R.color.teal_700)
                }
                setColors(colors)
                valueFormatter = TimeValueFormatter()
            }

            data = BarData(dataSet).apply{
                barWidth = 0.6f
            }

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelRotationAngle = 0f
                valueFormatter = DayAxisFormatter(days)
            }
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = HourAxisFormatter()
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            animateY(1000)
            invalidate()
        }
    }

}

class TimeValueFormatter : IValueFormatter {
    override fun getFormattedValue(
        value: Float,
        entry: Entry?,
        dataSetIndex: Int,
        viewPortHandler: ViewPortHandler?
    ): String {
        val hours = value.toInt()
        val minutes = ((value - hours) * 60).toInt()
        return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
    }
}

class DayAxisFormatter(private val days: List<DayUsageStats>) : IAxisValueFormatter {
    override fun getFormattedValue(value: Float, axis: AxisBase?): String {
        return if (value.toInt() >= 0 && value.toInt() < days.size) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .parse(days[value.toInt()].date)
            SimpleDateFormat("EE", Locale("vi")).format(date)
        } else ""
    }
}

class HourAxisFormatter : IAxisValueFormatter {
    override fun getFormattedValue(value: Float, axis: AxisBase?): String {
        return "${value.toInt()}h"
    }
}

