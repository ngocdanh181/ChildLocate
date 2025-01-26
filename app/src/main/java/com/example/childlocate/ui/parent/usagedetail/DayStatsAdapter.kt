package com.example.childlocate.ui.parent.usagedetail

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.data.model.DayUsageStats
import com.example.childlocate.databinding.ItemDayStatsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class DayStatsAdapter(private val onDaySelected: (DayUsageStats) -> Unit)
    : ListAdapter<DayUsageStats, DayStatsAdapter.DayViewHolder >(DayDiffCallback){

    private var selectedPosition = 0

    // Cập nhật getItemCount để chỉ trả về 1 nếu có dữ liệu
    override fun getItemCount(): Int {
        return currentList.size
        //return if (currentList.isNotEmpty()) 1 else 0
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        /*if (currentList.isNotEmpty() && selectedPosition < currentList.size) {
            holder.bind(getItem(selectedPosition), selectedPosition)
        }*/
        holder.bind(getItem(position), position)
    }

    override fun submitList(list: List<DayUsageStats>?) {
        // Khi submit list mới, set selected position về cuối list (ngày gần nhất)
        super.submitList(list)
    }

    // Hàm để set vị trí được chọn mặc định (ngày hiện tại)
    fun setInitialSelection() {
        // Kiểm tra nếu danh sách rỗng, không làm gì thêm
        if (currentList.isEmpty()) return

        // Lấy ngày hôm nay và định dạng thành chuỗi "yyyy-MM-dd"
        val today = Calendar.getInstance().time
        val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today)

        // Tìm vị trí của ngày hôm nay trong danh sách
        val todayPosition = currentList.indexOfFirst { it.date == todayString }

        Log.d("Check", todayString)
        Log.d("Check", currentList.toString())

        if (todayPosition != -1) {
            updateSelection(todayPosition) // Đánh dấu vị trí hôm nay là ngày được chọn
        }
        Log.d("CHECK", todayPosition.toString())

    }
    fun navigateToNextDay(): Boolean {
        if (selectedPosition < currentList.size - 1) {
            updateSelection(selectedPosition + 1)
            return false
        }
        // Kiểm tra xem ngày hiện tại có phải là Chủ nhật không
        val currentDay = currentList[selectedPosition]
        val calendar = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(currentDay.date)!!
        }
        return calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    }

    fun navigateToPreviousDay(): Boolean {
        if (selectedPosition > 0) {
            updateSelection(selectedPosition - 1)
            return false
        }
        // Kiểm tra xem ngày hiện tại có phải là thứ Hai không
        val currentDay = currentList[selectedPosition]
        val calendar = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(currentDay.date)!!
        }
        return calendar.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY
    }

    private fun updateSelection(newPosition: Int) {
        val oldPosition = selectedPosition
        selectedPosition = newPosition
        notifyItemChanged(oldPosition)
        notifyItemChanged(newPosition)
        onDaySelected(currentList[newPosition])
    }


    inner class DayViewHolder(private val binding: ItemDayStatsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(dayStats: DayUsageStats, position: Int) {
        binding.apply {
            // Format date to display (e.g., "T2" for Monday)
            val calendar = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayStats.date)!!
            }
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayStats.date)!!

            val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "CN"
                Calendar.MONDAY -> "T2"
                Calendar.TUESDAY -> "T3"
                Calendar.WEDNESDAY -> "T4"
                Calendar.THURSDAY -> "T5"
                Calendar.FRIDAY -> "T6"
                Calendar.SATURDAY -> "T7"
                else -> ""
            }

            // Calculate usage time in hours
            val hours = TimeUnit.MILLISECONDS.toHours(dayStats.totalTime)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(dayStats.totalTime) % 60
            //hien thi thu
            dayText.text = dayOfWeek
            //hien thi thoi gian
            timeText.text = String.format(Locale.getDefault(),"%d:%02d", hours, minutes)
            //hien thi ngay

            dateText.text= SimpleDateFormat("dd/MM", Locale("vi")).format(date)

            // Update selected state
            root.isSelected = position == selectedPosition
            root.setBackgroundColor(if (position == selectedPosition) Color.LTGRAY else Color.WHITE)
            root.setOnClickListener {
                updateSelection(position)
            }
            // Hiển thị item của ngày được chọn, ẩn các item khác
            //root.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE


        }
    }
}

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DayViewHolder {
        return DayViewHolder(
            ItemDayStatsBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }
}

object DayDiffCallback : DiffUtil.ItemCallback<DayUsageStats>() {
    override fun areItemsTheSame(oldItem: DayUsageStats, newItem: DayUsageStats): Boolean {
        return oldItem.date == newItem.date
    }
    override fun areContentsTheSame(oldItem: DayUsageStats, newItem: DayUsageStats): Boolean {
        return oldItem == newItem
    }
}