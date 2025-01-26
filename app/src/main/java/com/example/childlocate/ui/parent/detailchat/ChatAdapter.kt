package com.example.childlocate.ui.parent.detailchat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.childlocate.data.model.ChatMessage
import com.example.childlocate.databinding.ItemChatMessageReceivedBinding
import com.example.childlocate.databinding.ItemChatMessageSentBinding


class ChatAdapter(private val currentUserId: String,  private val memberNames: Map<String, String>) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(
    DiffCallback()
) {

    companion object {
        const val VIEW_TYPE_SENT = 1
        const val VIEW_TYPE_RECEIVED = 2

    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val binding = ItemChatMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            SentMessageViewHolder(binding)
        } else {
            val binding = ItemChatMessageReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ReceivedMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        val senderName = memberNames[message.senderId] ?: "Unknown"
        if (holder is SentMessageViewHolder) {
            holder.bind(message,senderName)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message,senderName)
        }
    }

    class SentMessageViewHolder(private val binding: ItemChatMessageSentBinding)
        : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage,senderName: String) {
            binding.senderNameTextView.text = senderName
            if (message.messageText.startsWith("https://firebasestorage.googleapis.com/v0")){
                binding.messageImageView.visibility = View.VISIBLE
                Glide.with(binding.root.context).load(message.messageText).into(binding.messageImageView)
                binding.messageTextView.visibility = View.GONE

            }else {
                binding.messageTextView.visibility = View.VISIBLE
                binding.messageTextView.text = message.messageText
                binding.messageImageView.visibility = View.GONE
            }
        }
    }

    class ReceivedMessageViewHolder(private val binding: ItemChatMessageReceivedBinding)
        : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, senderName: String) {
            binding.receivedNameTextView.text = senderName
            if (message.messageText.startsWith("https://firebasestorage.googleapis.com/v0")){
                binding.messageImageView.visibility = View.VISIBLE
                Glide.with(binding.root.context).load(message.messageText).into(binding.messageImageView)
                binding.messageTextView.visibility = View.GONE

            }else {
                binding.messageTextView.visibility = View.VISIBLE
                binding.messageTextView.text = message.messageText
                binding.messageImageView.visibility = View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
