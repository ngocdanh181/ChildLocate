package com.example.childlocate.ui.child.childchat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.childlocate.databinding.FragmentDetailChatBinding
import com.example.childlocate.ui.child.main.MainChildActivity
import com.example.childlocate.ui.parent.detailchat.ChatAdapter

class ChildChatDetailFragment : Fragment() {

    companion object {
        private const val ARG_PARENT_ID = "parent_id"
        private const val ARG_CHILD_ID = "child_id"

        fun newInstance(parentId: String?, childId: String?) = ChildChatDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PARENT_ID, parentId)
                putString(ARG_CHILD_ID, childId)
            }
        }
    }

    private lateinit var binding: FragmentDetailChatBinding
    private val viewModel: ChildChatDetailViewModel by lazy {
        ViewModelProvider(this)[ChildChatDetailViewModel::class.java]
    }

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var senderId: String
    private lateinit var receiverId: String

    private var isAtBottom = true

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true) {
            // Permissions granted, open gallery
            openGallery()
        } else {
            // Permissions denied
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.uploadAvatar(it)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            receiverId = it.getString(ARG_PARENT_ID) ?: ""
            senderId = it.getString(ARG_CHILD_ID) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDetailChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d("Child","$senderId,$receiverId")

        viewModel.setChatParticipants(senderId, receiverId)
        Log.d("ChildDetailChatFragment", "Participants set: senderId=$senderId, receiverId=$receiverId")

        /*chatAdapter = ChatAdapter(senderId)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.chatRecyclerView.adapter = chatAdapter*/
        // Chỉ quan sát khi đã tải xong tên các thành viên
        viewModel.memberNames.observe(viewLifecycleOwner, Observer { memberNames ->
            chatAdapter = ChatAdapter(senderId, memberNames)
            binding.chatRecyclerView.adapter = chatAdapter
            binding.chatRecyclerView.layoutManager = LinearLayoutManager(context)

            // Quan sát danh sách tin nhắn sau khi Adapter được thiết lập
            viewModel.messages.observe(viewLifecycleOwner, Observer { messages ->
                chatAdapter.submitList(messages)
                if (isAtBottom) {
                    binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                }
            })

            // Chỉ gọi loadMessages() một lần sau khi thành viên được tải xong
            viewModel.loadMessages()
        })


        binding.chatRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                isAtBottom = lastVisiblePosition == chatAdapter.itemCount - 1
            }
        })

        binding.backButton.setOnClickListener{
            val intent = Intent(requireContext(), MainChildActivity::class.java)
            startActivity(intent)
        }


        viewModel.messages.observe(viewLifecycleOwner, Observer { messages ->

            chatAdapter.submitList(messages)
            viewModel.loadMessages()
            if (isAtBottom) {
                binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
        })



        viewModel.loadMessages()
        binding.imageMessage.setOnClickListener{
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }


        binding.sendButton.setOnClickListener {
            val messageText = binding.messageEditText.text.toString()
            if (messageText.isNotEmpty()) {
                viewModel.sendMessage(messageText)
                binding.messageEditText.text.clear()

                // Hide the keyboard
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.messageEditText.windowToken, 0)

                // Scroll to the bottom
                binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }

        binding.childCall.setOnClickListener{
            val phoneNumber = "0987654321"
            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            startActivity(callIntent)
        }


    }

    private fun openGallery() {
        getContent.launch("image/*")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Show BottomNavigationView
    }
}