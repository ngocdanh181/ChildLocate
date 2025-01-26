package com.example.childlocate.ui.parent.usagedetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.R
import com.example.childlocate.data.model.AppUsageInfo
import com.example.childlocate.databinding.ItemUsageLayoutBinding
import com.example.childlocate.ui.parent.timelimit.TimeLimitDialog
import java.util.concurrent.TimeUnit


class AppUsageAdapter(private val childId: String) : ListAdapter<AppUsageInfo,AppUsageAdapter.ViewHolder>(AppUsageDiffCallback()) {

    class ViewHolder(private val binding: ItemUsageLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppUsageInfo, childId:String) {
            binding.tvAppName.text = item.appName
            binding.tvUsageTime.text = formatUsageTime(item.usageTime)
            // Load app icon using packageName
            try {
                val packageManager = itemView.context.packageManager
                val icon = packageManager.getApplicationIcon(item.packageName)
                binding.ivAppIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                binding.ivAppIcon.setImageResource(R.mipmap.ic_launcher)
            }

            // Handle click event
            itemView.setOnClickListener {
                val fragmentManager = (itemView.context as FragmentActivity).supportFragmentManager
                TimeLimitDialog.newInstance(item, childId).show(fragmentManager, "app_limit_dialog")
            }

        }

        private fun formatUsageTime(timeInMillis: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(timeInMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % 60
            return if (hours > 0) {
                "$hours giờ $minutes phút"
            } else {
                "$minutes phút"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemUsageLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), childId)
    }
}

class AppUsageDiffCallback : DiffUtil.ItemCallback<AppUsageInfo>() {
    override fun areItemsTheSame(oldItem: AppUsageInfo, newItem: AppUsageInfo): Boolean {
        return oldItem.packageName == newItem.packageName
    }

    override fun areContentsTheSame(oldItem: AppUsageInfo, newItem: AppUsageInfo): Boolean {
        return oldItem == newItem
    }
}
